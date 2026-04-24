package com.pos.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.pos.app.data.datastore.SettingsDataStore
import com.pos.app.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 單一備份檔的抽象描述。 */
data class BackupEntry(
    val name: String,
    val uri: Uri,
    val lastModified: Long,
    val size: Long
)

/**
 * 自動儲存（閒置備份）管理器。
 *
 * 儲存位置：
 * - **預設**：系統「下載」目錄的「火鍋店POS備份」子資料夾（透過 MediaStore 寫入，App 解除安裝後檔案不會消失）。
 * - **使用者指定**：透過 SAF 選擇的資料夾（Tree URI 持久化授權），用 DocumentFile 讀寫。
 *
 * 行為：
 * 1. 使用者操作後經過「閒置 N 分鐘」即自動執行備份。
 * 2. 檔名為 `pos_auto_YYYYMMDD.zip`，同日觸發會覆蓋；保留最近 M 天。
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var enabled: Boolean = true
    @Volatile private var idleMinutes: Int = 5
    @Volatile private var retentionDays: Int = 3
    @Volatile private var externalTreeUri: String = ""
    private var idleJob: Job? = null

    private val _lastBackupAt = MutableStateFlow<Long?>(null)
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

    init {
        _lastBackupAt.value = runCatching { listBackups().firstOrNull()?.lastModified }.getOrNull()

        combine(
            settingsDataStore.autoBackupEnabled,
            settingsDataStore.autoBackupIdleMinutes,
            settingsDataStore.autoBackupRetentionDays,
            settingsDataStore.autoBackupExternalTreeUri
        ) { e, m, d, uri -> AutoCfg(e, m.coerceAtLeast(1), d.coerceAtLeast(1), uri) }
            .onEach { cfg ->
                enabled = cfg.enabled
                idleMinutes = cfg.idleMinutes
                retentionDays = cfg.retentionDays
                externalTreeUri = cfg.externalTreeUri
                _lastBackupAt.value = runCatching { listBackups().firstOrNull()?.lastModified }.getOrNull()
                if (!cfg.enabled) cancelTimer() else scheduleIdleBackup()
            }
            .launchIn(scope)
    }

    fun onUserActivity() {
        if (!enabled) return
        scheduleIdleBackup()
    }

    fun onAppBackgrounded() {
        if (!enabled) return
        scope.launch { performBackupInternal() }
    }

    suspend fun backupNow(): Result<BackupEntry> = performBackupInternal()

    fun listBackups(): List<BackupEntry> =
        runCatching { currentStorage().list() }.getOrElse { emptyList() }

    fun deleteBackup(entry: BackupEntry) {
        runCatching { currentStorage().delete(entry) }
    }

    fun storageDescription(): String = currentStorage().description()

    private fun scheduleIdleBackup() {
        idleJob?.cancel()
        val delayMs = idleMinutes.toLong() * 60_000L
        idleJob = scope.launch {
            delay(delayMs)
            performBackupInternal()
        }
    }

    private fun cancelTimer() {
        idleJob?.cancel()
        idleJob = null
    }

    private suspend fun performBackupInternal(): Result<BackupEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val storage = currentStorage()
                val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                val fileName = "$FILE_PREFIX$today$FILE_SUFFIX"
                val entry = storage.writeBackup(fileName) { os ->
                    BackupManager.writeZipToStream(context, os, appDatabase)
                }
                _lastBackupAt.value = entry.lastModified
                storage.cleanup(retentionDays)
                entry
            }
        }
    }

    private fun currentStorage(): AutoBackupStorage {
        val uriStr = externalTreeUri
        return if (uriStr.isNotBlank()) {
            runCatching { SafTreeStorage(context, Uri.parse(uriStr)) }
                .getOrElse { MediaStoreDownloadsStorage(context) }
        } else {
            MediaStoreDownloadsStorage(context)
        }
    }

    private data class AutoCfg(
        val enabled: Boolean,
        val idleMinutes: Int,
        val retentionDays: Int,
        val externalTreeUri: String
    )

    companion object {
        const val BACKUP_SUBDIR = "火鍋店POS備份"
        const val FILE_PREFIX = "pos_auto_"
        const val FILE_SUFFIX = ".zip"
        const val MIME_ZIP = "application/zip"
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// Storage backends
// ───────────────────────────────────────────────────────────────────────────────

private interface AutoBackupStorage {
    fun writeBackup(name: String, writer: (java.io.OutputStream) -> Unit): BackupEntry
    fun list(): List<BackupEntry>
    fun delete(entry: BackupEntry)
    fun cleanup(keep: Int) {
        list().drop(keep).forEach { delete(it) }
    }
    fun description(): String
}

/** 預設：系統「下載 / 火鍋店POS備份」。使用 MediaStore，App 解除安裝後檔案仍保留。 */
private class MediaStoreDownloadsStorage(private val context: Context) : AutoBackupStorage {

    private val relativePath: String =
        "${Environment.DIRECTORY_DOWNLOADS}/${AutoBackupManager.BACKUP_SUBDIR}/"

    private val collection: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else
            MediaStore.Files.getContentUri("external")

    override fun writeBackup(name: String, writer: (java.io.OutputStream) -> Unit): BackupEntry {
        // 若同名檔案已存在就先刪除（同日覆寫語意）
        findExisting(name)?.let { context.contentResolver.delete(it, null, null) }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, AutoBackupManager.MIME_ZIP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("MediaStore 無法建立備份檔")

        resolver.openOutputStream(uri, "w")?.use { writer(it) }
            ?: error("無法開啟備份輸出串流")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }

        return queryEntry(uri) ?: BackupEntry(name, uri, System.currentTimeMillis(), 0L)
    }

    override fun list(): List<BackupEntry> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED
        )
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            args = arrayOf(relativePath, "${AutoBackupManager.FILE_PREFIX}%${AutoBackupManager.FILE_SUFFIX}")
        } else {
            selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            args = arrayOf("${AutoBackupManager.FILE_PREFIX}%${AutoBackupManager.FILE_SUFFIX}")
        }
        val result = mutableListOf<BackupEntry>()
        resolver.query(collection, projection, selection, args, "${MediaStore.Downloads.DATE_MODIFIED} DESC")
            ?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    result += BackupEntry(
                        name = c.getString(nameIdx),
                        uri = uri,
                        lastModified = c.getLong(dateIdx) * 1000L,
                        size = c.getLong(sizeIdx)
                    )
                }
            }
        return result
    }

    override fun delete(entry: BackupEntry) {
        runCatching { context.contentResolver.delete(entry.uri, null, null) }
    }

    override fun description(): String = "下載／${AutoBackupManager.BACKUP_SUBDIR}"

    private fun findExisting(name: String): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?"
            args = arrayOf(relativePath, name)
        } else {
            selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
            args = arrayOf(name)
        }
        resolver.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }

    private fun queryEntry(uri: Uri): BackupEntry? {
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE))
                val date = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)) * 1000L
                return BackupEntry(name, uri, date, size)
            }
        }
        return null
    }
}

/** 使用者透過 SAF 指定的資料夾（DocumentFile 樹）。 */
private class SafTreeStorage(
    private val context: Context,
    private val treeUri: Uri
) : AutoBackupStorage {

    private fun root(): DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri) ?: error("無法開啟備份資料夾")

    override fun writeBackup(name: String, writer: (java.io.OutputStream) -> Unit): BackupEntry {
        val root = root()
        root.findFile(name)?.delete()
        val created = root.createFile(AutoBackupManager.MIME_ZIP, name)
            ?: error("無法在指定資料夾建立備份檔")
        context.contentResolver.openOutputStream(created.uri, "w")?.use { writer(it) }
            ?: error("無法開啟備份輸出串流")
        return BackupEntry(
            name = created.name ?: name,
            uri = created.uri,
            lastModified = created.lastModified(),
            size = created.length()
        )
    }

    override fun list(): List<BackupEntry> {
        val root = runCatching { root() }.getOrNull() ?: return emptyList()
        return root.listFiles()
            .filter { df ->
                val n = df.name ?: return@filter false
                df.isFile && n.startsWith(AutoBackupManager.FILE_PREFIX) && n.endsWith(AutoBackupManager.FILE_SUFFIX)
            }
            .map {
                BackupEntry(
                    name = it.name.orEmpty(),
                    uri = it.uri,
                    lastModified = it.lastModified(),
                    size = it.length()
                )
            }
            .sortedByDescending { it.lastModified }
    }

    override fun delete(entry: BackupEntry) {
        runCatching { DocumentFile.fromSingleUri(context, entry.uri)?.delete() }
    }

    override fun description(): String {
        val df = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
        return df?.name ?: treeUri.toString()
    }
}

