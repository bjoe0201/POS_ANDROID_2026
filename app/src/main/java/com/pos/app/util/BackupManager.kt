package com.pos.app.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pos.app.data.db.AppDatabase
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupData(
    val version: Int = 1,
    val exportedAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val menuItems: List<MenuItemEntity>,
    val orders: List<OrderEntity>,
    val orderItems: List<OrderItemEntity>
)

object BackupManager {

    private const val DB_NAME = "pos_database"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportJson(context: Context, uri: Uri, data: BackupData): Result<Unit> {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(gson.toJson(data).toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open output stream")
        }
    }

    fun importJson(context: Context, uri: Uri): Result<BackupData> {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val json = stream.bufferedReader().readText()
                gson.fromJson(json, BackupData::class.java)
            } ?: error("Cannot open input stream")
        }
    }

    fun exportCsv(context: Context, uri: Uri, orders: List<OrderEntity>, orderItems: List<OrderItemEntity>): Result<Unit> {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                val sb = StringBuilder()
                sb.appendLine("訂單ID,桌號,建立時間,狀態,品項,數量,單價,小計")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                orders.forEach { order ->
                    val items = orderItems.filter { it.orderId == order.id }
                    if (items.isEmpty()) {
                        sb.appendLine("${order.id},${order.tableName},${sdf.format(Date(order.createdAt))},${order.status},,,, ")
                    } else {
                        items.forEach { item ->
                            sb.appendLine("${order.id},${order.tableName},${sdf.format(Date(order.createdAt))},${order.status},${item.name},${item.quantity},${item.price},${item.price * item.quantity}")
                        }
                    }
                }
                stream.write(sb.toString().toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open output stream")
        }
    }

    /**
     * 將 SQLite 資料庫（含 -shm、-wal）打包成 ZIP，寫入 uri。
     * 備份前呼叫 checkpoint 確保 WAL 已合併到主檔。
     */
    fun exportZip(context: Context, uri: Uri, db: AppDatabase): Result<Unit> {
        return runCatching {
            // Checkpoint WAL so the main db file is up-to-date
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

            val dbDir = context.getDatabasePath(DB_NAME).parentFile
                ?: error("Cannot locate database directory")

            val filesToZip = listOf(
                File(dbDir, DB_NAME),
                File(dbDir, "$DB_NAME-shm"),
                File(dbDir, "$DB_NAME-wal")
            ).filter { it.exists() }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    filesToZip.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: error("Cannot open output stream")
        }
    }

    /**
     * 從 ZIP 還原 SQLite 資料庫。
     * 策略：先解壓到 temp 目錄確認完整 → 關閉 Room → 覆蓋正式 DB 檔 → 呼叫方負責 kill process。
     */
    fun importZip(context: Context, uri: Uri, db: AppDatabase): Result<Unit> {
        return runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open input stream")
            input.use { restoreFromStream(context, it, db) }
        }
    }

    /**
     * 將 DB（含 -wal / -shm）直接寫入指定檔案，供「自動儲存」寫到 App 私有目錄。
     */
    fun exportZipToFile(context: Context, outFile: File, db: AppDatabase): Result<Unit> {
        return runCatching {
            outFile.parentFile?.mkdirs()
            outFile.outputStream().use { writeZipToStream(context, it, db) }
        }
    }

    /**
     * 將 DB 打包為 ZIP 並寫到任意 OutputStream（供 MediaStore / DocumentFile 使用）。
     * 呼叫端負責關閉 stream。
     */
    fun writeZipToStream(context: Context, out: java.io.OutputStream, db: AppDatabase) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val dbDir = context.getDatabasePath(DB_NAME).parentFile
            ?: error("Cannot locate database directory")
        val filesToZip = listOf(
            File(dbDir, DB_NAME),
            File(dbDir, "$DB_NAME-shm"),
            File(dbDir, "$DB_NAME-wal")
        ).filter { it.exists() }

        ZipOutputStream(out.buffered()).use { zip ->
            filesToZip.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * 從本機檔案還原（供自動儲存列表使用）。呼叫方負責在成功後 kill process。
     */
    fun importZipFromFile(context: Context, file: File, db: AppDatabase): Result<Unit> {
        return runCatching {
            file.inputStream().use { restoreFromStream(context, it, db) }
        }
    }

    private fun restoreFromStream(context: Context, input: java.io.InputStream, db: AppDatabase) {
        val dbDir = context.getDatabasePath(DB_NAME).parentFile
            ?: error("Cannot locate database directory")

        // Step 1: 先解壓到 temp 目錄，確認 zip 完整且包含 DB 主檔
        val tempDir = File(context.cacheDir, "db_restore_tmp").apply {
            deleteRecursively(); mkdirs()
        }
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                File(tempDir, entry.name).outputStream().use { zip.copyTo(it) }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val tempDb = File(tempDir, DB_NAME)
        if (!tempDb.exists() || tempDb.length() == 0L) error("備份 ZIP 中找不到有效的資料庫檔案")

        // Step 2: WAL checkpoint 後關閉 Room，避免 Room 在覆蓋期間持有檔案鎖
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close() }
        db.close()

        // Step 3: 將 temp 裡的檔案覆蓋到正式 DB 目錄
        tempDir.listFiles()?.forEach { src ->
            src.copyTo(File(dbDir, src.name), overwrite = true)
        }
        tempDir.deleteRecursively()
    }
}
