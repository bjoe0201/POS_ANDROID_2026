package com.pos.app.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.pos.app.data.db.entity.OrderItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UsbPrinterManager {

    private const val ACTION_USB_PERMISSION = "com.pos.app.USB_PERMISSION"
    const val EPSON_VENDOR_ID = 0x04B8  // 1208

    // ── Public API ──────────────────────────────────────────────────────────

    /** 找 EPSON 裝置，無則找任何 USB 裝置（fallback）。 */
    fun findPrinterDevice(context: Context): UsbDevice? {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = mgr.deviceList.values
        return devices.firstOrNull { it.vendorId == EPSON_VENDOR_ID } ?: devices.firstOrNull()
    }

    fun hasPermission(context: Context, device: UsbDevice): Boolean =
        (context.getSystemService(Context.USB_SERVICE) as UsbManager).hasPermission(device)

    /** 非同步請求 USB 權限，結果透過 [onResult] 在主執行緒回傳。 */
    fun requestPermission(context: Context, device: UsbDevice, onResult: (Boolean) -> Unit) {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                ctx.unregisterReceiver(this)
                onResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        mgr.requestPermission(
            device,
            PendingIntent.getBroadcast(
                context, 0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                flags
            )
        )
    }

    /** 列印 ASCII 測試頁（ESC/POS 文字模式，無需 Bitmap）。 */
    suspend fun printTestPage(context: Context, device: UsbDevice): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { sendToDevice(context, device, buildTestPageBytes()) }
        }

    /**
     * 列印收款收據（中文 Bitmap 點陣模式）。
     * 若找不到裝置或未取得權限，回傳 Result.failure。
     */
    suspend fun printCheckoutReceipt(
        context: Context,
        tableName: String,
        items: List<OrderItemEntity>,
        total: Double,
        remark: String,
        orderId: Long = 0L,
        createdAt: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val device = findPrinterDevice(context) ?: error("找不到 USB 印表機")
            if (!hasPermission(context, device)) error("未取得 USB 權限，請先在設定頁完成測試列印")
            sendToDevice(context, device, buildCheckoutBytes(tableName, items, total, remark, orderId, createdAt))
        }
    }

    /**
     * 列印訂單明細（中文 Bitmap 點陣模式）。
     */
    suspend fun printOrderDetail(
        context: Context,
        orderId: Long,
        tableName: String,
        createdAt: Long,
        items: List<OrderItemEntity>,
        total: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val device = findPrinterDevice(context) ?: error("找不到 USB 印表機")
            if (!hasPermission(context, device)) error("未取得 USB 權限，請先在設定頁完成測試列印")
            sendToDevice(context, device, buildDetailBytes(orderId, tableName, createdAt, items, total))
        }
    }

    // ── USB 傳輸 ─────────────────────────────────────────────────────────────

    private fun sendToDevice(context: Context, device: UsbDevice, data: ByteArray) {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        var bulkIface: android.hardware.usb.UsbInterface? = null
        var bulkEp: android.hardware.usb.UsbEndpoint? = null
        outer@ for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    bulkIface = iface; bulkEp = ep; break@outer
                }
            }
        }
        requireNotNull(bulkIface) { "找不到 USB Bulk OUT 介面" }
        requireNotNull(bulkEp) { "找不到 USB Bulk OUT 端點" }
        val conn = mgr.openDevice(device) ?: error("無法開啟 USB 裝置連線")
        try {
            conn.claimInterface(bulkIface, true)
            // 分塊傳送，避免緩衝溢位
            var offset = 0
            val chunkSize = 4096
            while (offset < data.size) {
                val len = minOf(chunkSize, data.size - offset)
                val sent = conn.bulkTransfer(bulkEp, data, offset, len, 8000)
                if (sent < 0) error("資料傳輸失敗（code=$sent，offset=$offset）")
                offset += sent
            }
        } finally {
            conn.releaseInterface(bulkIface)
            conn.close()
        }
    }

    // ── ESC/POS 測試頁（紙寬校準專用） ───────────────────────────────────────

    /**
     * 列印「紙寬校準頁」：
     *
     * 區段 A —— ASCII 字元尺：
     *   送一條 80 字元長的 "0123456789..." 不加 LF，由印表機自行 wrap。
     *   數第一行印出的字元數即可得知**目前印表機設定的字元欄寬**：
     *     - 32 → 58mm 字型 A 模式
     *     - 42 → 80mm 字型 B 模式
     *     - 48 → 80mm 字型 A 模式
     *
     * 區段 B —— Bitmap 寬度測試條：
     *   依序送出 288 / 320 / 360 / 384 / 432 / 480 / 576 dots 寬的 raster。
     *   每條左端標示寬度文字，右端為 "|" 終端標記。觀察：
     *     - 哪個寬度的右側 "|" 完整顯示在紙上 → 該寬度可用
     *     - 哪個寬度的 "|" 被截掉或 wrap 到下一列左邊 → 已超出印表機設定列印區
     *
     *   選擇「最後一個右側 | 完整、且左端寬度數字也完整」的數值，
     *   填入 [PRINT_WIDTH_PX] 即為最佳列印寬度。
     */
    private fun buildTestPageBytes(): ByteArray {
        val buf = mutableListOf<Byte>()
        fun b(vararg bytes: Int) = bytes.forEach { buf.add(it.toByte()) }
        fun str(s: String) = buf.addAll(s.toByteArray(Charsets.US_ASCII).toList())
        fun lf() = b(0x0A)

        b(0x1B, 0x40)                           // ESC @ Init
        b(0x1B, 0x21, 0x00)                     // ESC ! 0  取消任何字元放大
        b(0x1D, 0x21, 0x00)                     // GS  ! 0
        b(0x1B, 0x61, 0x01)                     // Center
        str("== PAPER WIDTH TEST =="); lf()
        b(0x1B, 0x61, 0x00)                     // Left
        lf()

        // ── 區段 A：ASCII 字元尺（讓印表機自己 wrap，紙上字數 = 欄寬）──
        str("[A] CHAR RULER (count chars/line):"); lf()
        // 兩列「十位刻度」+「個位刻度」共印 4 行，每行 80 字（會 wrap）
        // 第一行：十位 0..7（每 10 字一個數字）
        str("0         1         2         3         4         5         6         7         ")
        lf()
        // 第二行：個位 0123456789 重覆 8 次共 80 字
        str("01234567890123456789012345678901234567890123456789012345678901234567890123456789")
        lf()
        lf()

        // ── 區段 B：Bitmap 寬度測試條 ──
        str("[B] BITMAP WIDTH BARS:"); lf()
        lf()

        val widths = intArrayOf(288, 320, 360, 384, 432, 480, 576)
        for (w in widths) {
            buf.addAll(buildWidthBar(w).toList())
        }

        // 最後加一個「576 dots 滿版」實心條當印表機物理印頭最大寬度參考
        b(0x1B, 0x61, 0x00)
        lf()
        str("== END =="); lf()
        lf(); lf(); lf()
        b(0x1D, 0x56, 0x41, 0x10)              // Partial cut
        return buf.toByteArray()
    }

    /**
     * 產生一條寬度測試 Bitmap 的 ESC/POS bytes：
     *   左端：寬度數字（如 "360"）
     *   中間：細線
     *   右端："|" 終端標記（離右邊界 1 px）
     */
    private fun buildWidthBar(widthPx: Int): ByteArray {
        val height = 36
        val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f; color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; strokeWidth = 2f; style = Paint.Style.STROKE
        }
        // 左端寬度文字
        canvas.drawText("$widthPx dots", 4f, 24f, textP)
        // 中間水平細線
        canvas.drawLine(80f, 18f, (widthPx - 18).toFloat(), 18f, lineP)
        // 右端終端標記（兩條短豎線 = "||"）
        canvas.drawLine((widthPx - 14).toFloat(), 4f, (widthPx - 14).toFloat(), 32f, lineP)
        canvas.drawLine((widthPx - 6).toFloat(), 4f, (widthPx - 6).toFloat(), 32f, lineP)

        // 包成 ESC/POS：reset + GS v 0 raster
        val out = mutableListOf<Byte>()
        fun b(vararg bytes: Int) = bytes.forEach { out.add(it.toByte()) }
        b(0x1B, 0x21, 0x00)
        b(0x1D, 0x21, 0x00)
        b(0x1B, 0x33, 0x00)             // ESC 3 0  raster 期間行距 0
        out.addAll(toEscPosRaster(bmp).toList())
        b(0x1B, 0x32)                   // ESC 2  恢復行距
        b(0x0A)                         // LF 拉開條目間距
        return out.toByteArray()
    }

    // ── Bitmap 收據 ──────────────────────────────────────────────────────────

    private fun buildCheckoutBytes(
        tableName: String, items: List<OrderItemEntity>, total: Double, remark: String,
        orderId: Long, createdAt: Long
    ): ByteArray {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val totalQty = items.sumOf { it.quantity }
        val lines = mutableListOf<RL>()
        lines += RL("═".repeat(SEP), align = A.CENTER)
        lines += RL(tableName, align = A.CENTER, bold = true, large = true)
        lines += RL("═".repeat(SEP), align = A.CENTER)
        if (orderId > 0L) {
            lines += RL("訂單編號", right = "#$orderId")
        }
        lines += RL("開單時間", right = sdf.format(Date(createdAt)))
        lines += RL("結帳時間", right = sdf.format(Date(now)))
        lines += RL("─".repeat(SEP), align = A.CENTER)
        items.forEach { item ->
            val sub = item.price * item.quantity
            lines += RL(
                "${clipName(item.name)} ×${item.quantity}",
                right = "NT${"$"}${"%.0f".format(sub)}"
            )
        }
        lines += RL("─".repeat(SEP), align = A.CENTER)
        lines += RL("項目數", right = "$totalQty")
        lines += RL("合　計", right = "NT${"$"}${"%.0f".format(total)}", bold = true)
        lines += RL("═".repeat(SEP), align = A.CENTER)
        if (remark.isNotBlank()) {
            lines += RL.BLANK
            lines += RL("備註：$remark")
        }
        lines += RL.BLANK
        lines += RL("謝謝光臨！", align = A.CENTER, bold = true)
        lines += RL.BLANK
        lines += RL.BLANK
        return wrapBitmap(lines)
    }

    private fun buildDetailBytes(
        orderId: Long, tableName: String, createdAt: Long,
        items: List<OrderItemEntity>, total: Double
    ): ByteArray {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val lines = mutableListOf<RL>()
        lines += RL("═".repeat(SEP), align = A.CENTER)
        lines += RL("訂單明細  #$orderId", align = A.CENTER, bold = true)
        lines += RL("═".repeat(SEP), align = A.CENTER)
        lines += RL("$tableName  ${sdf.format(Date(createdAt))}")
        lines += RL.BLANK
        items.forEach { item ->
            lines += RL("${clipName(item.name)} ×${item.quantity}", right = "NT${"$"}${"%.0f".format(item.price * item.quantity)}")
        }
        lines += RL("─".repeat(SEP), align = A.CENTER)
        lines += RL("合　計", right = "NT${"$"}${"%.0f".format(total)}", bold = true)
        lines += RL("═".repeat(SEP), align = A.CENTER)
        lines += RL.BLANK
        lines += RL.BLANK
        return wrapBitmap(lines)
    }

    // ── Bitmap 渲染 ──────────────────────────────────────────────────────────

    /**
     * Bitmap 寬度（單位 px = dots）。
     * 設為 360 dots（≈45mm @ 8 dot/mm），留出 ~13mm 安全邊界給 58mm 紙。
     *
     * 配合 [wrapBitmap] 內送出的 GS L（左邊距=0）與 GS W（列印區寬度=PRINT_WIDTH_PX），
     * 強制告訴印表機「我要的列印區就是這個寬度」，避免印表機依預設區寬自行 wrap
     * 把 bitmap 右側內容換行到下一行左邊（形成紙張左側殘字異常）。
     *
     * 調整方向：
     *  - 若右側內容仍 wrap 到下一行左邊 → 再縮小 [PRINT_WIDTH_PX]（每次 -16）。
     *  - 若內容右側留白太多 → 加大 [PRINT_WIDTH_PX]（每次 +8，但不要超過 384）。
     *  - 若內容整體偏離紙張左邊 → 加大 [L_MARGIN]。
     */
    private const val PRINT_WIDTH_PX = 360

    /** 文字內容左邊距（避開紙張左邊界偏移） */
    private const val L_MARGIN = 12f
    /** 文字內容右邊距 */
    private const val R_MARGIN = 12f

    /** 分隔線重複字元數（配合寬度，未實際輸出文字，僅用於辨識分隔列） */
    private const val SEP = 20

    /** 名稱顯示最大「視覺寬度」（中文=2, ASCII=1）— 超過會自動截斷加上「…」避免覆蓋右側金額。 */
    private const val NAME_MAX_VW = 14

    private enum class A { LEFT, CENTER }

    /** Receipt Line */
    private data class RL(
        val text: String,
        val right: String = "",
        val align: A = A.LEFT,
        val bold: Boolean = false,
        val large: Boolean = false
    ) {
        companion object {
            val BLANK = RL("")
        }
    }

    /** 將 lines 渲染為 Bitmap，包上 ESC@ + reset 指令 + GS v 0 + partial cut。 */
    private fun wrapBitmap(lines: List<RL>): ByteArray {
        val bitmap = renderBitmap(lines)
        val buf = mutableListOf<Byte>()
        fun b(vararg bytes: Int) = bytes.forEach { buf.add(it.toByte()) }
        b(0x1B, 0x40)                    // ESC @ Init
        b(0x1B, 0x21, 0x00)              // ESC ! 0  取消所有字元列印模式
        b(0x1D, 0x21, 0x00)              // GS ! 0  取消字元放大倍率（避免殘留 double-width 影響 raster）
        b(0x1D, 0x42, 0x00)              // GS B 0  取消反白
        b(0x1B, 0x33, 0x00)              // ESC 3 0 行距 = 0（raster 由 bitmap 自帶行高）
        buf.addAll(toEscPosRaster(bitmap).toList())
        b(0x1B, 0x32)                    // ESC 2  恢復預設行距
        b(0x1D, 0x56, 0x41, 0x10)       // Partial cut
        return buf.toByteArray()
    }

    /** 計算字串視覺寬度：中日韓字元 = 2，其餘 = 1。 */
    private fun visualWidth(s: String): Int {
        var w = 0
        for (c in s) {
            val code = c.code
            w += if (code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF ||
                code in 0xFF00..0xFFEF || code in 0x3000..0x303F
            ) 2 else 1
        }
        return w
    }

    /** 截斷名稱，避免覆蓋右側金額。超過 [NAME_MAX_VW] 視覺寬度時尾端加 「…」。 */
    private fun clipName(s: String): String {
        if (visualWidth(s) <= NAME_MAX_VW) return s
        val sb = StringBuilder()
        var w = 0
        for (c in s) {
            val cw = if (c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF ||
                c.code in 0xFF00..0xFFEF || c.code in 0x3000..0x303F
            ) 2 else 1
            if (w + cw > NAME_MAX_VW - 1) break
            sb.append(c); w += cw
        }
        sb.append('…')
        return sb.toString()
    }

    private fun renderBitmap(lines: List<RL>): Bitmap {
        val widthPx = PRINT_WIDTH_PX
        val normalSz = 22f
        val largeSz = 30f
        val normalLH = 32
        val largeLH = 44
        val blankLH = 12
        val padTop = 6
        val padBot = 12

        val normalP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = normalSz; color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val boldP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = normalSz; color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val largeBoldP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = largeSz; color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sepP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; strokeWidth = 1.5f; style = Paint.Style.STROKE
        }

        fun lh(l: RL) = when {
            l.text.isBlank() && l.right.isBlank() -> blankLH
            l.large -> largeLH
            else -> normalLH
        }

        val totalH = padTop + lines.sumOf { lh(it) } + padBot
        val bmp = Bitmap.createBitmap(widthPx, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        var y = padTop.toFloat()
        lines.forEach { line ->
            val lhPx = lh(line)
            val baseline = y + normalSz

            // 分隔線：直接畫線取代文字，確保完整橫跨
            if (line.text.startsWith("═") || line.text.startsWith("─")) {
                val lineY = y + lhPx / 2f
                canvas.drawLine(L_MARGIN, lineY, widthPx - R_MARGIN, lineY, sepP)
                y += lhPx
                return@forEach
            }

            if (line.text.isNotBlank() || line.right.isNotBlank()) {
                val p = when {
                    line.large -> largeBoldP
                    line.bold  -> boldP
                    else       -> normalP
                }
                val bl = y + p.textSize
                when (line.align) {
                    A.CENTER -> {
                        val tw = p.measureText(line.text)
                        canvas.drawText(line.text, ((widthPx - tw) / 2f).coerceAtLeast(L_MARGIN), bl, p)
                    }
                    A.LEFT -> {
                        canvas.drawText(line.text, L_MARGIN, bl, p)
                        if (line.right.isNotBlank()) {
                            val rw = p.measureText(line.right)
                            canvas.drawText(line.right, widthPx - R_MARGIN - rw, bl, p)
                        }
                    }
                }
            }
            y += lhPx
        }
        return bmp
    }

    /** Bitmap → ESC/POS GS v 0 點陣指令。黑色像素 (luminance < 128) 印出，白色略過。 */
    private fun toEscPosRaster(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val bytesPerRow = (w + 7) / 8
        val buf = mutableListOf<Byte>()
        // GS v 0: 1D 76 30 m xL xH yL yH
        buf += 0x1D.toByte(); buf += 0x76.toByte()
        buf += 0x30.toByte(); buf += 0x00.toByte()
        buf += (bytesPerRow and 0xFF).toByte()
        buf += (bytesPerRow shr 8 and 0xFF).toByte()
        buf += (h and 0xFF).toByte()
        buf += (h shr 8 and 0xFF).toByte()
        for (row in 0 until h) {
            for (byteIdx in 0 until bytesPerRow) {
                var byte = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    if (x < w) {
                        val px = bmp.getPixel(x, row)
                        val lum = (0.299 * ((px shr 16) and 0xFF) +
                                   0.587 * ((px shr 8)  and 0xFF) +
                                   0.114 *  (px          and 0xFF)).toInt()
                        if (lum < 128) byte = byte or (1 shl (7 - bit))
                    }
                }
                buf += byte.toByte()
            }
        }
        return buf.toByteArray()
    }
}
