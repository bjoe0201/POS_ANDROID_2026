package com.pos.app.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.pos.app.ui.report.ReportUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 使用 Android 內建 [PdfDocument] API 將報表資料渲染為 PDF 檔案，
 * 不依賴任何第三方函式庫。
 */
object ReportPdfBuilder {

    private const val PAGE_W = 595   // A4 寬度（72dpi 點）
    private const val PAGE_H = 842   // A4 高度（72dpi 點）
    private const val MARGIN = 40f
    private const val LINE_H = 18f
    private const val GAP = 10f

    /**
     * 在 IO 執行緒將 [state] 渲染為 PDF 並寫入 [uri]。
     * 回傳 [Result]；失敗時 [Result.exceptionOrNull] 包含錯誤訊息。
     */
    suspend fun build(
        context: Context,
        uri: Uri,
        state: ReportUiState,
        includeOrderDetails: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            try {
                val r = Renderer(document)
                val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                // ── 頁首 ──────────────────────────────────────────────
                r.title("報表匯出")
                r.body("產生時間：${timeFmt.format(Date())}")
                r.body("含已刪除：${if (state.showDeleted) "是" else "否"}")
                r.gap()

                // ── 總覽 ──────────────────────────────────────────────
                r.section("總覽")
                r.row("總營業額", "NT${"$"}%.0f".format(state.totalRevenue))
                r.row("總筆數", "${state.totalOrders} 筆")
                r.row("平均客單", "NT${"$"}%.0f".format(state.avgOrderValue))
                r.gap()

                // ── 品項銷售排行 ──────────────────────────────────────
                r.section("品項銷售排行")
                if (state.itemRanking.isEmpty()) {
                    r.body("（無資料）")
                } else {
                    state.itemRanking.forEachIndexed { i, (name, qty) ->
                        r.row("${i + 1}. $name", "$qty 份")
                    }
                }
                r.gap()

                // ── 群組銷售排行 ──────────────────────────────────────
                r.section("群組銷售排行")
                if (state.groupRanking.isEmpty()) {
                    r.body("（無資料）")
                } else {
                    state.groupRanking.forEachIndexed { i, g ->
                        r.row("${i + 1}. ${g.groupName}（${g.quantity}份）", "NT${"$"}%.0f".format(g.revenue))
                    }
                }

                // ── 訂單明細（可選）──────────────────────────────────
                if (includeOrderDetails) {
                    r.gap()
                    r.section("訂單明細")
                    state.orders.forEach { owi ->
                        val o = owi.order
                        val tag = if (o.isDeleted) "  【已刪除】" else ""
                        r.sub("#${o.id}  ${o.tableName}  ${timeFmt.format(Date(o.createdAt))}$tag")
                        owi.items.forEach { item ->
                            r.row(
                                "  ${item.name} × ${item.quantity}",
                                "NT${"$"}%.0f".format(item.price * item.quantity)
                            )
                        }
                        val total = owi.items.sumOf { it.price * it.quantity }
                        r.row("  小計", "NT${"$"}%.0f".format(total))
                        r.gap(4f)
                    }
                }

                r.finish()

                context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                    ?: error("無法開啟輸出串流")
            } finally {
                document.close()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 內部渲染器：管理多頁翻頁、提供語意化繪圖方法
    // ─────────────────────────────────────────────────────────────
    private class Renderer(private val doc: PdfDocument) {
        private var pageNum = 0
        private var pg: PdfDocument.Page = startPage()
        private var cv: Canvas = pg.canvas
        private var y = 60f

        private val pTitle   = Paint().apply { textSize = 20f; isFakeBoldText = true; color = Color.BLACK }
        private val pSection = Paint().apply { textSize = 13f; isFakeBoldText = true; color = Color.BLACK }
        private val pSub     = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.DKGRAY }
        private val pBody    = Paint().apply { textSize = 11f; color = Color.BLACK }

        private fun startPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNum).create()
            return doc.startPage(info)
        }

        /** 當剩餘空間不足時翻到下一頁。 */
        private fun checkOverflow(neededSpace: Float = LINE_H) {
            if (y + neededSpace > PAGE_H - MARGIN) {
                doc.finishPage(pg)
                pg = startPage()
                cv = pg.canvas
                y = 60f
            }
        }

        fun title(text: String) {
            checkOverflow(LINE_H + 8f)
            cv.drawText(text, MARGIN, y, pTitle)
            y += LINE_H + 8f
        }

        fun section(text: String) {
            checkOverflow(LINE_H + 4f)
            cv.drawText("| $text", MARGIN, y, pSection)
            y += LINE_H + 4f
        }

        fun sub(text: String) {
            checkOverflow()
            cv.drawText(text, MARGIN, y, pSub)
            y += LINE_H
        }

        fun body(text: String) {
            checkOverflow()
            cv.drawText(text, MARGIN, y, pBody)
            y += LINE_H
        }

        /** 左對齊文字 + 右對齊文字，各位於頁面左右邊界。 */
        fun row(left: String, right: String) {
            checkOverflow()
            cv.drawText(left, MARGIN, y, pBody)
            val rightX = PAGE_W - MARGIN - pBody.measureText(right)
            cv.drawText(right, rightX, y, pBody)
            y += LINE_H
        }

        fun gap(extra: Float = GAP) {
            y += extra
            if (y + LINE_H > PAGE_H - MARGIN) checkOverflow()
        }

        /** 最後必須呼叫，將最後一頁結尾。 */
        fun finish() { doc.finishPage(pg) }
    }
}
