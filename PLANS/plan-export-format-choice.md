# 報表匯出：格式選擇（CSV/PDF）＋明細範圍選擇 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 點「匯出報表」時先彈出選項 Dialog，讓使用者選擇輸出格式（CSV / PDF）與內容範圍（列印明細 / 只印總覽），再啟動對應的檔案選擇器，以選定格式匯出。

**Architecture:** `ReportScreen` 新增 `ExportOptionsDialog` composable（含 `ExportFormat` enum）；`ReportViewModel.exportCsv` 新增 `includeOrderDetails` 參數，並新增 `exportPdf` 函式；PDF 渲染邏輯獨立封裝於 `util/ReportPdfBuilder.kt`，使用 Android 內建 `android.graphics.pdf.PdfDocument`，不需新增任何 dependency。

**Tech Stack:** Kotlin · Jetpack Compose Material3 · `android.graphics.pdf.PdfDocument` (Android API 19+，minSdk 29 已滿足，零額外 dependency)

---

## 涉及檔案

| 動作 | 路徑 |
|------|------|
| **新增** | `app/src/main/java/com/pos/app/util/ReportPdfBuilder.kt` |
| **修改** | `app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt` |
| **修改** | `app/src/main/java/com/pos/app/ui/report/ReportScreen.kt` |

---

## Task 1：更新 `ReportViewModel` — CSV 支援 `includeOrderDetails` 旗標

**Files:**
- Modify: `app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt`

- [ ] **Step 1：在 `exportCsv` 加入 `includeOrderDetails` 參數**

找到以下函式（約第 332 行）並替換：

```kotlin
// 舊
fun exportCsv(context: Context, uri: Uri) {
    viewModelScope.launch {
        val state = _uiState.value
        if (state.orders.isEmpty()) {
            _uiState.update { it.copy(message = "此期間無資料可匯出") }
            return@launch
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val content = buildReportCsv(state)
```

```kotlin
// 新
fun exportCsv(context: Context, uri: Uri, includeOrderDetails: Boolean = true) {
    viewModelScope.launch {
        val state = _uiState.value
        if (state.orders.isEmpty()) {
            _uiState.update { it.copy(message = "此期間無資料可匯出") }
            return@launch
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val content = buildReportCsv(state, includeOrderDetails)
```

- [ ] **Step 2：更新 `buildReportCsv` 簽章，並將「訂單明細」區段包入 `if (includeOrderDetails)`**

找到以下函式宣告（約第 357 行）：

```kotlin
private fun buildReportCsv(state: ReportUiState): String {
```

改為：

```kotlin
private fun buildReportCsv(state: ReportUiState, includeOrderDetails: Boolean): String {
```

在同一函式內，找到以下區段（約第 399 行）：

```kotlin
        // 4. 訂單明細
        line("===== 訂單明細 =====")
        line("訂單ID", "桌號", "建立時間", "狀態", "已刪除", "品項", "群組", "數量", "單價", "小計")
        state.orders.forEach { owi ->
            val o = owi.order
            val createdStr = dateTimeFmt.format(java.util.Date(o.createdAt))
            val deletedFlag = if (o.isDeleted) "是" else ""
            if (owi.items.isEmpty()) {
                line(o.id, o.tableName, createdStr, o.status, deletedFlag, "", "", "", "", "")
            } else {
                owi.items.forEach { item ->
                    line(
                        o.id,
                        o.tableName,
                        createdStr,
                        o.status,
                        deletedFlag,
                        item.name,
                        item.menuGroupName,
                        item.quantity,
                        "%.0f".format(item.price),
                        "%.0f".format(item.price * item.quantity)
                    )
                }
            }
        }
```

在整個區段外套上 `if (includeOrderDetails) { ... }`：

```kotlin
        // 4. 訂單明細
        if (includeOrderDetails) {
            line("===== 訂單明細 =====")
            line("訂單ID", "桌號", "建立時間", "狀態", "已刪除", "品項", "群組", "數量", "單價", "小計")
            state.orders.forEach { owi ->
                val o = owi.order
                val createdStr = dateTimeFmt.format(java.util.Date(o.createdAt))
                val deletedFlag = if (o.isDeleted) "是" else ""
                if (owi.items.isEmpty()) {
                    line(o.id, o.tableName, createdStr, o.status, deletedFlag, "", "", "", "", "")
                } else {
                    owi.items.forEach { item ->
                        line(
                            o.id,
                            o.tableName,
                            createdStr,
                            o.status,
                            deletedFlag,
                            item.name,
                            item.menuGroupName,
                            item.quantity,
                            "%.0f".format(item.price),
                            "%.0f".format(item.price * item.quantity)
                        )
                    }
                }
            }
        }
```

- [ ] **Step 3：確認建置通過**

```bash
./gradlew assembleDebug
```

預期：BUILD SUCCESSFUL，無 compile error。

- [ ] **Step 4：Commit**

```bash
git add app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt
git commit -m "feat(report): exportCsv supports includeOrderDetails flag"
```

---

## Task 2：新增 `ReportPdfBuilder.kt`

**Files:**
- Create: `app/src/main/java/com/pos/app/util/ReportPdfBuilder.kt`

- [ ] **Step 1：建立檔案，內容如下**

```kotlin
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
            val r = Renderer(document)
            val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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
            document.close()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 內部渲染器：管理多頁翻頁、提供語意化繪圖方法
    // ─────────────────────────────────────────────────────────────
    private class Renderer(private val doc: PdfDocument) {
        private var pageNum = 1
        private var pg: PdfDocument.Page = startPage()
        private var cv: Canvas = pg.canvas
        private var y = 60f

        private val pTitle   = Paint().apply { textSize = 20f; isFakeBoldText = true; color = Color.BLACK }
        private val pSection = Paint().apply { textSize = 13f; isFakeBoldText = true; color = Color.BLACK }
        private val pSub     = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.DKGRAY }
        private val pBody    = Paint().apply { textSize = 11f; color = Color.BLACK }

        private fun startPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum++).create()
            return doc.startPage(info)
        }

        /** 當剩餘空間不足時翻到下一頁。 */
        private fun checkOverflow() {
            if (y + LINE_H > PAGE_H - MARGIN) {
                doc.finishPage(pg)
                pg = startPage()
                cv = pg.canvas
                y = 60f
            }
        }

        fun title(text: String) {
            checkOverflow()
            cv.drawText(text, MARGIN, y, pTitle)
            y += LINE_H + 8f
        }

        fun section(text: String) {
            checkOverflow()
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

        fun gap(extra: Float = GAP) { y += extra }

        /** 最後必須呼叫，將最後一頁結尾。 */
        fun finish() { doc.finishPage(pg) }
    }
}
```

- [ ] **Step 2：確認建置通過**

```bash
./gradlew assembleDebug
```

預期：BUILD SUCCESSFUL。

- [ ] **Step 3：Commit**

```bash
git add app/src/main/java/com/pos/app/util/ReportPdfBuilder.kt
git commit -m "feat(report): add ReportPdfBuilder for PDF export"
```

---

## Task 3：在 `ReportViewModel` 新增 `exportPdf`

**Files:**
- Modify: `app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt`

- [ ] **Step 1：在 import 區塊末尾加入 `ReportPdfBuilder`**

在現有 import 最後一行之後加入：

```kotlin
import com.pos.app.util.ReportPdfBuilder
```

- [ ] **Step 2：在 `exportCsv` 函式之後新增 `exportPdf`**

緊接在 `exportCsv` 函式的結尾 `}` 之後新增：

```kotlin
fun exportPdf(context: Context, uri: Uri, includeOrderDetails: Boolean = true) {
    viewModelScope.launch {
        val state = _uiState.value
        if (state.orders.isEmpty()) {
            _uiState.update { it.copy(message = "此期間無資料可匯出") }
            return@launch
        }
        val result = ReportPdfBuilder.build(context, uri, state, includeOrderDetails)
        _uiState.update {
            it.copy(
                message = if (result.isSuccess) {
                    "已匯出 PDF（${state.orders.size} 筆訂單）"
                } else {
                    "PDF 匯出失敗：${result.exceptionOrNull()?.message ?: "未知錯誤"}"
                }
            )
        }
    }
}
```

- [ ] **Step 3：確認建置通過**

```bash
./gradlew assembleDebug
```

預期：BUILD SUCCESSFUL。

- [ ] **Step 4：Commit**

```bash
git add app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt
git commit -m "feat(report): add exportPdf to ReportViewModel"
```

---

## Task 4：更新 `ReportScreen` — Dialog + 雙 Launcher + 按鈕流程

**Files:**
- Modify: `app/src/main/java/com/pos/app/ui/report/ReportScreen.kt`

### Step 1：在檔案頂層（package 宣告之後，composable 之前）加入 `ExportFormat` enum

在 `@OptIn(ExperimentalMaterial3Api::class)` 標註前加入：

```kotlin
enum class ExportFormat { CSV, PDF }
```

### Step 2：在 `ReportScreen` composable 內，更新狀態宣告與 launcher

找到以下現有程式碼區塊（約第 58–75 行）：

```kotlin
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showReportDetailPrintDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val onReportPrintClick: () -> Unit = {
        if (viewModel.shouldConfirmReportDetailPrint()) {
            showReportDetailPrintDialog = true
        } else {
            viewModel.printCurrentReport(context, includeOrderDetails = true)
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.exportCsv(context, it) }
    }

    val suggestedFileName: () -> String = {
        val now = System.currentTimeMillis()
        val start = uiState.customStartDate ?: now
        val end = uiState.customEndDate ?: now
        val label = when (uiState.dateRange) {
            DateRange.TODAY -> "today_${fileNameSdf.format(Date(now))}"
            DateRange.YESTERDAY -> "yesterday_${fileNameSdf.format(Date(now - 86_400_000L))}"
            DateRange.WEEK -> "week_${fileNameSdf.format(Date(now))}"
            DateRange.MONTH -> "month_${fileNameSdf.format(Date(now))}"
            DateRange.YEAR -> "year_${fileNameSdf.format(Date(now))}"
            DateRange.ALL -> "all_${fileNameSdf.format(Date(now))}"
            DateRange.CUSTOM -> "${fileNameSdf.format(Date(start))}_${fileNameSdf.format(Date(end))}"
        }
        "report_$label.csv"
    }
```

**完整替換為：**

```kotlin
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showReportDetailPrintDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingIncludeDetails by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val onReportPrintClick: () -> Unit = {
        if (viewModel.shouldConfirmReportDetailPrint()) {
            showReportDetailPrintDialog = true
        } else {
            viewModel.printCurrentReport(context, includeOrderDetails = true)
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.exportCsv(context, it, pendingIncludeDetails) }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.exportPdf(context, it, pendingIncludeDetails) }
    }

    val suggestedFileName: (ext: String) -> String = { ext ->
        val now = System.currentTimeMillis()
        val start = uiState.customStartDate ?: now
        val end = uiState.customEndDate ?: now
        val label = when (uiState.dateRange) {
            DateRange.TODAY -> "today_${fileNameSdf.format(Date(now))}"
            DateRange.YESTERDAY -> "yesterday_${fileNameSdf.format(Date(now - 86_400_000L))}"
            DateRange.WEEK -> "week_${fileNameSdf.format(Date(now))}"
            DateRange.MONTH -> "month_${fileNameSdf.format(Date(now))}"
            DateRange.YEAR -> "year_${fileNameSdf.format(Date(now))}"
            DateRange.ALL -> "all_${fileNameSdf.format(Date(now))}"
            DateRange.CUSTOM -> "${fileNameSdf.format(Date(start))}_${fileNameSdf.format(Date(end))}"
        }
        "report_$label.$ext"
    }
```

### Step 3：在 `if (showReportDetailPrintDialog)` 區塊之後，加入 `showExportDialog` 的 Dialog

緊接在 `showReportDetailPrintDialog` AlertDialog 的結尾 `}` 之後（約第 190 行），加入：

```kotlin
    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { format, includeDetails ->
                showExportDialog = false
                pendingIncludeDetails = includeDetails
                when (format) {
                    ExportFormat.CSV -> exportCsvLauncher.launch(suggestedFileName("csv"))
                    ExportFormat.PDF -> exportPdfLauncher.launch(suggestedFileName("pdf"))
                }
            },
            t = t
        )
    }
```

### Step 4：更新「匯出報表」按鈕的觸發點

找到以下兩個 `onExport` 呼叫（均在 `item { Column { ... } }` 區塊內，約第 321–335 行）：

```kotlin
                ReportActionButtons(
                    uiState = uiState,
                    onPrint = onReportPrintClick,
                    onExport = { exportCsvLauncher.launch(suggestedFileName()) },
                    t = t
                )
```

（此段出現兩次：自訂日期模式和非自訂模式各一次）

**兩處都改為：**

```kotlin
                ReportActionButtons(
                    uiState = uiState,
                    onPrint = onReportPrintClick,
                    onExport = { showExportDialog = true },
                    t = t
                )
```

### Step 5：在檔案末尾，`ReportChip` composable 之前，加入 `ExportOptionsDialog` composable

在 `@Composable` `private fun ReportChip(...)` 之前插入：

```kotlin
@Composable
private fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (format: ExportFormat, includeDetails: Boolean) -> Unit,
    t: PosColors
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var includeDetails by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text("匯出選項", color = t.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("輸出格式", color = t.textSub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFormat.entries.forEach { fmt ->
                            val label = if (fmt == ExportFormat.CSV) "CSV" else "PDF"
                            FilterChip(
                                selected = selectedFormat == fmt,
                                onClick = { selectedFormat = fmt },
                                label = { Text(label, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = t.accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("內容範圍", color = t.textSub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = includeDetails,
                            onClick = { includeDetails = true },
                            label = { Text("列印明細", fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = t.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = !includeDetails,
                            onClick = { includeDetails = false },
                            label = { Text("只印總覽", fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = t.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedFormat, includeDetails) },
                colors = ButtonDefaults.buttonColors(containerColor = t.accent)
            ) { Text("匯出") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = t.textSub) }
        }
    )
}
```

- [ ] **Step 6：確認建置通過**

```bash
./gradlew assembleDebug
```

預期：BUILD SUCCESSFUL，無 compile error（`FilterChip`、`FilterChipDefaults` 已由 `import androidx.compose.material3.*` 覆蓋）。

- [ ] **Step 7：Commit**

```bash
git add app/src/main/java/com/pos/app/ui/report/ReportScreen.kt
git commit -m "feat(report): add export options dialog (CSV/PDF, detail/summary)"
```

---

## Task 5：手動驗證與版本更新

- [ ] **Step 1：安裝 debug APK 並手動驗證**

```bash
./gradlew installDebug
```

驗證項目：

| 情境 | 預期結果 |
|------|---------|
| 點「匯出報表」 | 出現 Dialog，顯示「CSV / PDF」和「列印明細 / 只印總覽」兩組 FilterChip |
| 選 CSV + 列印明細 → 匯出 | 開啟檔案選擇器，建立 `.csv` 檔；內容包含總覽、排行、訂單明細 |
| 選 CSV + 只印總覽 → 匯出 | `.csv` 檔只有總覽和兩個排行榜，不含訂單明細區段 |
| 選 PDF + 列印明細 → 匯出 | 開啟檔案選擇器，建立 `.pdf` 檔；以 PDF 閱讀器開啟可看到所有區段（含訂單明細） |
| 選 PDF + 只印總覽 → 匯出 | PDF 只包含總覽、品項排行、群組排行，無訂單明細 |
| 點「取消」 | Dialog 關閉，不啟動檔案選擇器 |

- [ ] **Step 2：更新 `gradle.properties` 版本號**

```properties
APP_VERSION_CODE=13   # 原本 12，遞增 1
APP_VERSION_NAME=1.2.13
```

- [ ] **Step 3：最終 Commit**

```bash
git add gradle.properties
git commit -m "chore: bump version to v1.2.13"
```

---

## 備註

- **`FilterChip`** 已包含在 `androidx.compose.material3.*`，不需額外 dependency。
- **PDF 翻頁**：`ReportPdfBuilder.Renderer.checkOverflow()` 在每次繪製前判斷剩餘高度；若訂單明細很多（每頁 ~40 行），會自動分頁。
- **`$` 跳脫**：PDF 渲染器內的金額格式字串使用 `"NT${"$"}%.0f".format(...)` 以符合 Kotlin 字串樣板語法。
- **簽章 Release APK**：正式 Release 請依 `CLAUDE.md` 的「固定 APK 發佈流程」執行。
