# USB 熱感印表機功能設計文件

> 建立日期：2026-04-28
> 最後更新：2026-04-29
> 實作版本：v1.2.5+
> 狀態：**紙張寬度已校正完成（PRINT_WIDTH_PX = 360）／收據、明細、報表列印已實作／網路列印調查完成（見末節）**

---

## 目錄

1. [功能概述](#功能概述)
2. [硬體環境](#硬體環境)
3. [Android USB 連接方式](#android-usb-連接方式)
4. [架構設計](#架構設計)
5. [ESC/POS 指令說明](#escpos-指令說明)
6. [Bitmap 列印模式（中文支援）](#bitmap-列印模式中文支援)
7. [紙張寬度校正紀錄](#紙張寬度校正紀錄)
8. [DataStore 設定鍵](#datastore-設定鍵)
9. [UI 流程](#ui-流程)
10. [檔案結構](#檔案結構)
11. [已知問題與限制](#已知問題與限制)
12. [調整參數方式](#調整參數方式)
13. [下一階段：報表列印](#下一階段報表列印)
14. [網路列印調查紀錄（2026-04-29）](#網路列印調查紀錄2026-04-29)

---

## 功能概述

透過 Android USB Host API（OTG）直接與 EPSON 熱感印表機通訊，傳送 ESC/POS 指令列印：

| 功能 | 觸發時機 | 開關位置 |
|------|----------|----------|
| **測試列印** | 設定頁手動按下 | 設定 → 印表機 → 測試列印 |
| **收款結帳列印** | 確認收款後自動觸發 | 設定 → 印表機 → 收款結帳列印（Switch） |
| **訂單明細列印** | 報表頁逐筆手動觸發 | 設定 → 印表機 → 明細列印（Switch） |
| **報表列印** | 報表頁手動按下 | 報表 → 報表列印 |

> Switch 開關只在「測試列印通過」後才顯示，確保印表機連線可用。

---

## 硬體環境

### 實測印表機

| 項目 | 值 |
|------|----|
| 型號 | EPSON TM-T70II |
| 連接 | USB（Android OTG/USB Host） |
| VendorID | `0x04B8`（EPSON） |
| ProductID | `0x0202` |
| 解析度 | 203 DPI |
| 機體設計紙寬 | 80mm（印表機規格） |
| 實際使用紙寬 | **58mm**（裝入 80mm 機體） |

### 58mm 紙裝入 80mm 印表機的問題

EPSON TM-T70II 的印字頭設計寬度為 80mm（576 dots @ 203 DPI），但本機裝入 58mm 紙張。
這造成以下挑戰：

- 印字頭從固定位置開始列印（左起 dot 0）
- 58mm 紙張的有效列印寬度 ≈ 463 dots，但實際可安全使用的寬度更小（含左側偏移與右邊距）
- **若 Bitmap 寬度過大，右側內容超出紙張邊界被截斷**
- **若 Bitmap 寬度與偏移未對齊紙張，左側內容印在紙張外部（空白區域）**

---

## Android USB 連接方式

### AndroidManifest.xml

```xml
<!-- USB Host 功能聲明 -->
<uses-feature android:name="android.hardware.usb.host" android:required="false" />

<!-- 插入 EPSON 裝置時自動啟動 App -->
<intent-filter>
    <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
</intent-filter>
<meta-data
    android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
    android:resource="@xml/usb_device_filter" />
```

### res/xml/usb_device_filter.xml

```xml
<resources>
    <!-- EPSON Vendor ID: 0x04B8 = 1208 -->
    <usb-device vendor-id="1208" />
</resources>
```

### 權限流程

```
1. 呼叫 UsbManager.deviceList 列舉已連接裝置
2. 優先找 vendorId == 0x04B8（EPSON），無則 fallback 任何裝置
3. UsbManager.hasPermission(device) 檢查是否已有授權
4. 若無 → 發送 PendingIntent 彈出系統「允許使用 USB 裝置」對話框
5. BroadcastReceiver 接收結果（RECEIVER_NOT_EXPORTED @ Android 13+）
6. 授權後開啟 UsbDeviceConnection，claimInterface，bulkTransfer
```

---

## 架構設計

### 主要元件

```
util/
  UsbPrinterManager.kt     — 單例 object，封裝所有 USB 印表機邏輯

data/datastore/
  SettingsDataStore.kt     — 新增 3 個 DataStore keys

data/repository/
  SettingsRepository.kt    — 對應 flows / setters

ui/settings/
  SettingsViewModel.kt     — 新增 3 個 state 欄位與 setter
  SettingsScreen.kt        — PrinterSection composable

ui/order/
  OrderViewModel.kt        — 新增 printCheckoutEnabled state
  OrderScreen.kt           — 確認收款後自動列印

ui/report/
  ReportViewModel.kt       — 新增 printDetailEnabled / isPrintingReport state、報表列印快照
  ReportScreen.kt          — OrderSummaryRow 明細列印按鈕、報表列印按鈕
```

### UsbPrinterManager 公開 API

```kotlin
object UsbPrinterManager {
    // 查詢
    fun findPrinterDevice(context): UsbDevice?
    fun hasPermission(context, device): Boolean
    fun requestPermission(context, device, onResult: (Boolean) -> Unit)

    // 列印（suspend，在 Dispatchers.IO 執行）
    suspend fun printTestPage(context, device): Result<Unit>
    suspend fun printCheckoutReceipt(
        context, tableName, items, total, remark,
        orderId: Long = 0L,                       // ← 新增：訂單編號
        createdAt: Long = System.currentTimeMillis() // ← 新增：開單時間
    ): Result<Unit>
    suspend fun printOrderDetail(context, orderId, tableName, createdAt, items, total): Result<Unit>
    suspend fun printReport(context, snapshot): Result<Unit> // ← 新增：目前篩選報表列印
}
```

---

## ESC/POS 指令說明

### 測試頁（紙寬校準頁，ASCII + Bitmap 混合）

測試頁設計為**紙寬校準工具**，包含兩個區段：

#### 區段 A — ASCII 字元尺（CHAR RULER）
送出兩條 80 字元長的數字字串（不加 LF），由印表機自動 wrap。觀察紙上每行印出的字元數即可推算印表機目前的字元欄寬：

| 每行字元數 | 對應紙寬 / 字型模式 |
|-----------|-------------------|
| 32 | 58mm 字型 A |
| 42 | 80mm 字型 B |
| 48 | 80mm 字型 A |

#### 區段 B — Bitmap 寬度測試條（WIDTH BARS）
依序送出 `288 / 320 / 360 / 384 / 432 / 480 / 576` dots 七種寬度的 raster bitmap，每條左端標註寬度數字、右端為 `||` 終端標記。

**判讀方式**：選擇「最後一條右端 `||` 仍完整、且左端寬度數字未被截斷」的數值作為 `PRINT_WIDTH_PX`。

### 主要指令對照

| 指令 | Hex | 說明 |
|------|-----|------|
| ESC @ | `1B 40` | 初始化印表機 |
| ESC a n | `1B 61 01` | 對齊方式（0=左、1=中、2=右） |
| GS ! n | `1D 21 11` | 字元放大（0x11 = 雙寬雙高） |
| LF | `0A` | 換行 |
| GS V A | `1D 56 41 10` | 半切（Partial Cut，進紙 16mm） |

### Bitmap 列印（中文收據）

| 指令 | Hex | 說明 |
|------|-----|------|
| ESC @ | `1B 40` | 初始化 |
| GS v 0 | `1D 76 30 00 xL xH yL yH data` | 點陣圖列印 |
| GS V A | `1D 56 41 10` | 半切 |

#### GS v 0 格式

```
1D 76 30  m  xL xH  yL yH  d1...dk
               ↑              ↑
        每行 byte 數       行數（高度）
       (ceil(widthPx/8))
```

- `m = 0x00`：正常大小（1:1）
- `xL xH`：每行 bytes = ceil(PRINT_WIDTH_PX / 8)
- `yL yH`：Bitmap 高度（行數）
- `data`：逐行掃描，MSB 在前，1=黑，0=白

---

## Bitmap 列印模式（中文支援）

### 為何使用 Bitmap 模式

EPSON TM-T70II 標準機型的內建字型只支援 ASCII 及部分歐語字元集，**不支援直接輸出繁體中文**。

解決方案：使用 Android 的 `Canvas/Bitmap` API 將文字渲染成點陣圖，再以 `GS v 0` 指令傳送給印表機。此法相容所有 EPSON 熱感印表機，不依賴機體內建字型。

### 渲染流程

```
List<RL>（收據行定義）
    ↓ renderBitmap()
Android Bitmap（ARGB_8888）
    ↓ toEscPosRaster()
ByteArray（GS v 0 點陣資料）
    ↓ wrapBitmap()
ESC @ + GS v 0 data + GS V A
    ↓ sendToDevice()
UsbDeviceConnection.bulkTransfer()（4096 bytes / chunk）
```

### RL（Receipt Line）資料模型

```kotlin
private data class RL(
    val text: String,          // 左側文字（或唯一文字）
    val right: String = "",    // 右側文字（金額欄，靠右對齊）
    val align: A = A.LEFT,     // LEFT / CENTER
    val bold: Boolean = false,
    val large: Boolean = false
)
```

分隔線偵測：`text.startsWith("═")` 或 `text.startsWith("─")` → 改以 `canvas.drawLine()` 繪製，確保填滿寬度。

---

## 紙張寬度校正紀錄

### 問題根因分析

EPSON TM-T70II 為 80mm 機體（印字頭 576 dots），裝入 58mm 紙時存在**起始位置偏移**：
- Bitmap 從 dot 0 開始，但紙張左邊界不在 dot 0
- 若 Bitmap 太寬，右側超出紙張邊界

### 測試歷程

| 嘗試 | PRINT_WIDTH_PX | L_MARGIN | R_MARGIN | 結果 |
|------|----------------|----------|----------|------|
| 第 1 次 | 576 | 10 | 10 | 內容旋轉 90°（寬度超出，行資料錯位） |
| 第 2 次 | 384 | 10 | 10 | 左側內容起始在紙外、右側超出邊界 |
| 第 3 次 | 320 | 10 | 10 | 右側 `NT$xxx` 結尾仍 wrap 到下一行左邊（出現 "NT" / "↑" 殘字） |
| 第 4 次 | 384 | 16 | 32 | 加大右邊距 + 加入 reset 指令 + 名稱截斷 |
| 第 5 次 | 384 | 16 | 32 | 仍有右側 wrap 殘字 → 改用紙寬校準頁實測 |
| **第 6 次** | **360** | **12** | **12** | **✅ 校準頁顯示 360 dots 是最後一條 `\|\|` 完整、左端數字完整的寬度** |

### 校準結論（第 6 次，當前實作）

透過紙寬校準頁實測：

- `288 / 320 / 360 dots`：左端寬度數字、右端 `||` 終端標記**全部完整**
- `384 dots`：橫線延伸到紙張右邊界，但 `||` 終端標記被截
- `432 / 480 / 576 dots`：右側明顯被截斷

→ **取最大可用值 `PRINT_WIDTH_PX = 360`** 作為實際參數，配合 `L_MARGIN = 12 / R_MARGIN = 12` 並送出 `GS L`（左邊距=0）+ `GS W`（列印區寬度）強制告知印表機列印區寬度，避免依預設區寬自行 wrap。

### 當前參數

```kotlin
private const val PRINT_WIDTH_PX = 360   // Bitmap 寬度（dots）— 校準頁實測最後完整值
private const val L_MARGIN = 12f         // 文字左邊距（px）
private const val R_MARGIN = 12f         // 文字右邊距（px）
private const val SEP = 20               // 分隔線輔助常數（畫線時使用 widthPx 全寬）
private const val NAME_MAX_VW = 14       // 名稱視覺寬度上限（中文=2, ASCII=1）
```

### 調整建議

若仍有偏差：

| 現象 | 調整方向 |
|------|----------|
| 右側 `NT$xxx` 仍 wrap 到下一行左邊 | 減小 `PRINT_WIDTH_PX`（每次 -8 px，即 -1mm） |
| 右側有大片空白、內容偏左 | 增大 `PRINT_WIDTH_PX`（注意不要超過校準頁實測值） |
| 左側第一個字被截 | 增大 `L_MARGIN`（+4f 約 +0.5mm） |
| 左側空白太多 | 減小 `L_MARGIN` |
| 紙張改變（換紙寬 / 換印表機） | **先重印「紙寬校準頁」**（設定 → 印表機 → 測試列印），再依結果調整 `PRINT_WIDTH_PX` |

**理論最大安全寬度**（58mm 紙 @ 203 DPI）：
```
58mm × (203 / 25.4) ≈ 463 dots（含紙張左右邊距約各 1.5mm）
實際可用 ≈ 440 dots，但因 80mm 機體偏移，安全值更低
```

---

## DataStore 設定鍵

| Key | 類型 | 預設 | 說明 |
|-----|------|------|------|
| `printer_test_passed` | Boolean | false | 測試列印是否通過（通過後才顯示功能 Switch） |
| `print_checkout_enabled` | Boolean | false | 確認收款後自動列印收據 |
| `print_detail_enabled` | Boolean | false | 報表訂單明細顯示逐筆列印按鈕 |

---

## UI 流程

### 設定頁 — 印表機區塊

```
[印表機] Section
  ├── 說明文字
  ├── 狀態訊息框（deviceName、VendorID、授權狀態、列印結果）
  ├── [測試列印] 按鈕
  │     ├── 點擊 → findPrinterDevice()
  │     ├── 無裝置 → 錯誤提示
  │     ├── 無權限 → requestPermission() → 系統對話框
  │     └── 列印成功 → setPrinterTestPassed(true) → 顯示 Switch
  │
  └── [若 printerTestPassed == true]
        ├── Switch：收款結帳列印
        └── Switch：明細列印
```

### 記帳頁 — 確認收款

```
[確認收款] 按鈕
  └── onConfirm = {
        val tName       = uiState.selectedTable?.tableName ?: ""
        val tTotal      = uiState.total
        val tItems      = uiState.orderItems.toList()       // 在 payOrder 前快照
        val tRemark     = uiState.remark
        val tOrderId    = uiState.order?.id ?: 0L           // ← 新增
        val tCreatedAt  = uiState.order?.createdAt          // ← 新增（開單時間）
                          ?: System.currentTimeMillis()
        val shouldPrint = uiState.printCheckoutEnabled
        viewModel.payOrder {
            SoundEffects.playPaymentSuccess()
            if (shouldPrint) {
                scope.launch {
                    UsbPrinterManager.printCheckoutReceipt(
                        context, tName, tItems, tTotal, tRemark,
                        tOrderId, tCreatedAt
                    )
                }
            }
        }
      }
```

### 報表頁 — 訂單明細列印

```
OrderSummaryRow（展開後）
  └── [若 printDetailEnabled && !isDeleted]
        └── [列印明細] OutlinedButton
              └── scope.launch {
                    UsbPrinterManager.printOrderDetail(...)
                  }
```

### 報表頁 — 報表列印

```
[報表列印] [匯出報表]
  └── onClick = viewModel.printCurrentReport(context)
        ├── 無資料 / 載入中 / 列印中 → 不重複送印並顯示提示
        ├── 若超過 10 筆且日期範圍超過 1 天 → 先詢問是否列印訂單明細
        ├── 建立 ReportPrintSnapshot（目前篩選條件 + 統計 + 排行 + 訂單明細）
        └── UsbPrinterManager.printReport(context, snapshot)
              ├── findPrinterDevice()
              ├── hasPermission()
              ├── buildReportBytes(snapshot)
              └── wrapBitmapChunks(lines) 分段 Bitmap 送印
```

---

## 收據版面

### 收款收據（printCheckoutReceipt） — 強化版（v1.2.5+）

包含**訂單編號、開單時間、結帳時間、項目數**等完整對帳資訊：

```
══════════════════════
        1 號桌
══════════════════════
訂單編號           #32
開單時間   2026-04-28 21:00
結帳時間   2026-04-28 21:14
──────────────────────
酒桃輝哥 ×1        NT$300
狗燈 ×1             NT$50
很菜 ×3            NT$150
西歐否 ×1          NT$100
狗加 ×1             NT$80
密魯 ×1            NT$100
──────────────────────
項目數               8
合　計             NT$780
══════════════════════
備註：XXX（選填）

      謝謝光臨！
```

> 若 `orderId == 0L`（理論上不會發生，僅為安全預設），則「訂單編號」行會自動隱藏。

### 訂單明細（printOrderDetail）

```
══════════════════════
    訂單明細  #32
══════════════════════
1號桌  2026-04-27 21:46

酒桃輝哥 ×1        NT$300
很菜 ×4            NT$200
─────────────────────
合　計             NT$500
══════════════════════
```

### 報表列印（printReport）

列印目前 `ReportScreen` 篩選後的報表內容，包含總覽、排行與訂單明細；長報表以 `wrapBitmapChunks(...)` 分段渲染，避免單張 Bitmap 過高。

防呆規則：若目前報表 **超過 10 筆訂單** 且 **日期範圍超過 1 天**，按下「報表列印」時會先跳出確認視窗，提供：

- `列印明細`：列印總覽 / 排行 / 完整訂單明細。
- `只印總覽`：列印總覽與排行，訂單明細區塊標示「未列印」。
- `取消`：不送印。

```
══════════════════════
        銷售報表
══════════════════════
日期區間             今日
2026-04-28 ~ 2026-04-28
含已刪除             否
產生時間   2026-04-28 22:10
──────────────────────
總營業額          NT$800
總筆數              2 筆
平均客單          NT$400
══════════════════════
品項銷售排行
1. 西歐否          ×3
2. 酒桃輝哥        ×2
──────────────────────
群組銷售排行
1. 肉品 5份     NT$500
══════════════════════
訂單明細（2 筆）
#39  1號桌       NT$800
2026-04-28 21:59
  西歐否 ×2      NT$200
══════════════════════
```

---

## 檔案結構

```
app/src/main/
├── AndroidManifest.xml                         ← USB Host feature + device filter
├── res/xml/usb_device_filter.xml               ← EPSON VendorID 1208 過濾
└── java/com/pos/app/
    ├── util/
    │   └── UsbPrinterManager.kt                ← 核心列印邏輯
    ├── data/
    │   ├── datastore/SettingsDataStore.kt       ← 3 個新 key
    │   └── repository/SettingsRepository.kt    ← 對應 flows/setters
    └── ui/
        ├── settings/
        │   ├── SettingsViewModel.kt             ← printer state + setters
        │   └── SettingsScreen.kt               ← PrinterSection composable
        ├── order/
        │   ├── OrderViewModel.kt               ← printCheckoutEnabled state
        │   └── OrderScreen.kt                  ← 確認收款後列印
        └── report/
            ├── ReportViewModel.kt              ← printDetailEnabled / isPrintingReport、報表列印快照
            └── ReportScreen.kt                 ← OrderSummaryRow 明細列印按鈕、報表列印按鈕
```

---

## 已知問題與限制

### 中文字型

- 使用 Android 系統預設字型（`Typeface.DEFAULT`），各裝置渲染結果略有差異
- 字型大小設為 22px，在 320px 寬度下約可容納 **14 個半形** 或 **7 個全形**（中文）字元
- 品項名稱較長時，目前**不自動換行**，超出部分會被 Bitmap 邊界截掉

### USB 權限

- 第一次連接需使用者手動授權，**重開 App 後不需重新授權**（Android 系統快取）
- App 重啟後仍需呼叫 `hasPermission()` 確認（部分裝置在重開後可能需再次授權）

### 列印失敗處理

- 目前列印失敗只輸出 `Result.failure`，UI 端不顯示 Snackbar（待改善）
- 收款結帳後若列印失敗，收款資料已寫入資料庫（不影響帳務）

### 80mm 機體裝 58mm 紙

- 這是非標準用法，**強烈建議換用標準 58mm 熱感印表機**（如 EPSON TM-T20III-5GJ1）
- 或換用 80mm 紙張（TM-T70II 原廠規格）使用 `PRINT_WIDTH_PX = 576`

---

## 調整參數方式

所有列印尺寸參數集中在 `UsbPrinterManager.kt` 頂層（`private const`）：

```kotlin
// ── 紙張 ──
private const val PRINT_WIDTH_PX = 360   // Bitmap 寬度（dots）。校準頁實測值
private const val L_MARGIN = 12f         // 文字左邊距（px）
private const val R_MARGIN = 12f         // 文字右邊距（px）
private const val SEP = 20               // 分隔線輔助常數
private const val NAME_MAX_VW = 14       // 品項名稱視覺寬度上限（中文=2, ASCII=1）

// ── 字型（renderBitmap 內的 local val）──
val normalSz = 22f      // 一般文字大小
val largeSz = 30f       // 標題文字大小（桌號）
val normalLH = 32       // 一般行高（px）
val largeLH = 44        // 大字行高（px）
val blankLH = 12        // 空行高度（px）
val padTop = 6          // 頂部留白
val padBot = 12         // 底部留白
```

**換紙寬 / 換印表機時的標準流程：**

1. 設定頁 → 印表機 → **測試列印** → 取得「紙寬校準頁」
2. 觀察區段 B 的寬度條，找出最後一條**右端 `||` 完整、左端寬度數字未截**的數值
3. 將該值填入 `PRINT_WIDTH_PX`，重新 Build + Install
4. 再次測試列印確認排版無誤後，至設定頁開啟「收款結帳列印 / 明細列印」Switch

修改後需重新 Build + Install 再測試列印。

---

## 下一階段：報表列印

> 實作狀態：**已完成**。已新增 `ReportScreen`「報表列印」按鈕、`ReportViewModel.printCurrentReport(context)`、`UsbPrinterManager.printReport(...)` 與長報表分段 Bitmap 列印。

### 目標

在 `ReportScreen` 報表頁的「匯出報表」按鈕左側新增 **「報表列印」** 按鈕，將目前畫面套用篩選條件後的報表內容直接透過 USB 熱感印表機列印。

列印內容應與目前 `ReportViewModel.exportCsv(...)` 的報表邏輯一致，包含：

1. 報表檔頭
2. 日期區間與產生時間
3. 總覽：總營業額、總筆數、平均客單
4. 品項銷售排行
5. 群組銷售排行
6. 訂單明細

### UI 規劃

#### 按鈕位置

目前 `ReportScreen` 在日期篩選區塊中提供「匯出報表」：

- 自訂模式：位於「套用」右側
- 非自訂模式：靠右單獨顯示

新增後排列：

```text
[報表列印] [匯出報表]
```

自訂模式建議放在「套用」右側、匯出報表左側：

```text
[開始日期] [結束日期] [套用] [報表列印] [匯出報表]
```

非自訂模式建議靠右顯示兩個按鈕：

```text
                              [報表列印] [匯出報表]
```

#### 按鈕狀態

| 狀態 | 行為 |
|------|------|
| `uiState.orders.isEmpty()` | 停用，避免空報表列印 |
| `uiState.isLoading == true` | 停用，避免列印未完成計算的資料 |
| `uiState.isPrintingReport == true` | 停用並顯示「列印中…」 |
| 找不到 USB 印表機 | Snackbar 顯示「找不到 USB 印表機」 |
| 未授權 USB | Snackbar 顯示「未取得 USB 權限，請先在設定頁完成測試列印」 |
| 列印成功 | Snackbar 顯示「報表已送出列印」 |

> 建議「報表列印」按鈕不受 `print_detail_enabled` 控制；`print_detail_enabled` 只控制逐筆「列印明細」按鈕。報表列印屬於報表頁主要動作，按鈕直接顯示即可。

### 資料流規劃

```text
ReportScreen
  ↓ 點擊「報表列印」
ReportViewModel.printCurrentReport(context)
  ↓ 快照目前 ReportUiState
  ↓ 檢查是否有資料 / 是否列印中
UsbPrinterManager.printReport(context, snapshot)
  ↓ findPrinterDevice()
  ↓ hasPermission()
  ↓ buildReportBytes(...)
  ↓ sendToDevice()
Snackbar 顯示結果
```

### 建議新增資料模型

避免 `UsbPrinterManager` 直接依賴整個 UI state，可在 `ui/report` 或 `util` 附近定義列印用快照模型：

```kotlin
data class ReportPrintSnapshot(
    val rangeLabel: String,
    val rangeText: String,
    val showDeleted: Boolean,
    val generatedAt: Long,
    val totalRevenue: Double,
    val totalOrders: Int,
    val avgOrderValue: Double,
    val itemRanking: List<Pair<String, Int>>,
    val groupRanking: List<GroupSalesStat>,
    val orders: List<OrderWithItems>
)
```

也可先以最小變更方式讓 `ReportViewModel` 組成必要參數後傳入 `UsbPrinterManager.printReport(...)`，但長期建議使用 snapshot，避免列印過程中資料重新 recompute 導致內容不一致。

### `ReportViewModel` 規劃

#### `ReportUiState` 新增欄位

```kotlin
val isPrintingReport: Boolean = false
```

#### 新增方法

```kotlin
fun printCurrentReport(context: Context)
```

責任：

1. 讀取目前 `_uiState.value` 作為列印快照。
2. 若無資料，設定 `message = "此期間無資料可列印"`。
3. 若正在列印，直接 return，避免重複送印。
4. 設定 `isPrintingReport = true`。
5. 呼叫 `UsbPrinterManager.printReport(...)`。
6. 成功時設定 `message = "報表已送出列印"`。
7. 失敗時設定 `message = "報表列印失敗：..."`。
8. finally 設定 `isPrintingReport = false`。

### `UsbPrinterManager` API 規劃

新增：

```kotlin
suspend fun printReport(
    context: Context,
    snapshot: ReportPrintSnapshot
): Result<Unit>
```

或最小變更版：

```kotlin
suspend fun printReport(
    context: Context,
    rangeLabel: String,
    rangeText: String,
    showDeleted: Boolean,
    totalRevenue: Double,
    totalOrders: Int,
    avgOrderValue: Double,
    itemRanking: List<Pair<String, Int>>,
    groupRanking: List<GroupSalesStat>,
    orders: List<OrderWithItems>
): Result<Unit>
```

建議實作流程與 `printCheckoutReceipt(...)`、`printOrderDetail(...)` 一致：

```text
findPrinterDevice(context)
hasPermission(context, device)
buildReportBytes(snapshot)
sendToDevice(context, device, bytes)
```

### 列印版面規劃

熱感紙寬有限，報表列印應偏向「可讀摘要」，不複製 CSV 的多欄表格格式。

```text
══════════════════════
        銷售報表
══════════════════════
日期區間
2026-04-28 ~ 2026-04-28
含已刪除             否
產生時間   2026-04-28 22:10
──────────────────────
總營業額          NT$800
總筆數              2 筆
平均客單          NT$400
══════════════════════
品項銷售排行
1. 西歐否 ×3
2. 酒桃輝哥 ×2
3. 很菜 ×2
──────────────────────
群組銷售排行
1. 肉品  5份      NT$500
2. 飲料  3份      NT$300
══════════════════════
訂單明細（2 筆）
#39  1號桌
2026-04-28 21:59  NT$800
  西歐否 ×2       NT$200
  酒桃輝哥 ×1     NT$300
──────────────────────
#38  1號桌
2026-04-28 21:18  NT$800
  西歐否 ×1       NT$300
  很菜 ×2         NT$100
══════════════════════
```

#### 內容取捨建議

- 品項排行：沿用目前 `itemRanking.take(10)`。
- 群組排行：沿用目前 `groupRanking.take(10)`。
- 訂單明細：列出篩選後所有訂單，但每筆訂單內品項名稱需使用 `clipName(...)` 截斷。
- 若訂單很多，紙張會很長；第一版先完整列印，後續可再加「只列印摘要 / 列印完整明細」選項。

### 長報表處理

目前 `wrapBitmap(lines)` 會將所有 lines render 成單一 Bitmap。報表可能遠長於收據，建議新增分段列印：

```kotlin
private fun wrapBitmapChunks(lines: List<RL>, chunkLineCount: Int = 48): ByteArray
```

策略：

1. 將 `lines` 分段。
2. 第一段送 `ESC @` 初始化。
3. 每段各自 `renderBitmap(chunk)` → `toEscPosRaster(bitmap)`。
4. 段落間送少量 LF。
5. 最後一段才送 `GS V A` 半切。

好處：

- 避免單一 Bitmap 過高造成記憶體壓力。
- 避免 `GS v 0` 高度參數過大或傳輸時間過長。
- 後續也可復用於長訂單或其他列印項目。

### 錯誤處理規劃

| 情境 | 顯示訊息 |
|------|----------|
| 無報表資料 | `此期間無資料可列印` |
| 找不到印表機 | `報表列印失敗：找不到 USB 印表機` |
| 未取得權限 | `報表列印失敗：未取得 USB 權限，請先在設定頁完成測試列印` |
| USB 傳輸失敗 | `報表列印失敗：資料傳輸失敗...` |
| 成功 | `報表已送出列印` |

### 需修改檔案

| 檔案 | 變更 |
|------|------|
| `app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt` | 新增 `isPrintingReport`、`printCurrentReport(context)`、報表列印 snapshot 組裝 |
| `app/src/main/java/com/pos/app/ui/report/ReportScreen.kt` | 在「匯出報表」左側新增「報表列印」按鈕、列印中狀態 UI |
| `app/src/main/java/com/pos/app/util/UsbPrinterManager.kt` | 新增 `printReport(...)`、`buildReportBytes(...)`、必要時新增分段 bitmap 包裝 |
| `README.md` | 更新報表功能與印表機功能說明 |
| `CLAUDE.md` | 更新重要常數 / 報表匯出與列印說明 |
| `PLANS/plan-usb-printer.md` | 記錄本節規劃與實作結果 |

### 驗證項目

1. `gradlew.bat assembleDebug` 可成功建置。
2. 無資料期間按鈕停用或顯示「此期間無資料可列印」。
3. 未接印表機時顯示錯誤 Snackbar。
4. 未授權 USB 時提示先到設定頁測試列印。
5. 今日 / 昨天 / 本週 / 本月 / 今年 / 全部 / 自訂區間列印內容與畫面統計一致。
6. 勾選「已刪除」後，列印內容與畫面一致。
7. 長報表可完整送印，不發生右側殘字或 app 記憶體錯誤。
8. 列印完成後可再次點擊列印，不會卡在 `isPrintingReport = true`。

### 建議實作順序

1. 先在 `ReportViewModel` 新增列印狀態與 `printCurrentReport(context)`。
2. 在 `UsbPrinterManager` 新增最小可用 `printReport(...)`，先輸出總覽 + 排行。
3. 加入訂單明細區段。
4. 若測試長報表有風險，再將 `wrapBitmap(...)` 擴充為分段列印。
5. 在 `ReportScreen` 新增「報表列印」按鈕。
6. 實機測試版面後微調 `NAME_MAX_VW` 或新增報表專用截斷寬度。
7. 更新 `README.md`、`CLAUDE.md` 與本文件狀態。

---

## 網路列印調查紀錄（2026-04-29）

### 背景

嘗試將 EPSON TM-T70II 接至 **ASUS RT-N14UHP** 路由器的 USB 埠，透過路由器內建的 USB Print Server 功能，讓 Android 以 TCP/IP 網路方式列印，取代 OTG USB 直連。

### 測試環境

| 項目 | 值 |
|------|----|
| 印表機 | EPSON TM-T70II |
| 路由器 | ASUS RT-N14UHP |
| 路由器 IP | 192.168.1.1 |
| 測試工具 | Python 3.13（Windows） |
| 測試腳本 | `test_network_printer.py` ～ `test_network_printer5.py`（已刪除） |

### 測試過程

#### Port 掃描結果

| Port | 狀態 | 說明 |
|------|------|------|
| 9100 | **OPEN** | RAW TCP（JetDirect） |
| 515  | **OPEN** | LPD/LPR |
| 9101 | closed | — |
| 9102 | closed | — |

兩個標準列印 port 皆開放，初步看似支援。

#### 測試方法與結果

| 方法 | 協定 | 說明 | 結果 |
|------|------|------|------|
| A | RAW TCP 9100 | sendall + shutdown + 等 2s | 送出成功，**無出紙** |
| B | RAW TCP 9100 | SO_LINGER(3s) | 送出成功，**無出紙** |
| C | RAW TCP 9100 | 分塊 64B + 每塊 50ms 延遲 | 送出成功，**無出紙** |
| D | LPD 515 | RFC 1179，queue=`lp`，等 ACK | **ACK timeout，失敗** |
| E | RAW TCP 9100 | 最小指令（ESC @ + LF×5 + CUT）| 送出成功，**無出紙** |
| F | RAW TCP 9100 | 持連線 10 秒後才關閉 | 送出成功，**無出紙** |
| G | LPD 515 | RFC 順序，queue=`LPRServer`，不等 ACK | 送出成功，**無出紙** |
| H | LPD 515 | Microsoft 順序（ctrl 先於 data），queue=`LPRServer` | 送出成功，**無出紙** |
| I | RAW TCP 9100 | hold 5s 後關閉 | 送出成功，**無出紙** |

> 方法 G/H 的 queue name `LPRServer` 來自 ASUS 官方 Windows 設定說明（Standard TCP/IP Port，Protocol=LPR，Queue Name=LPRServer）。

#### 根本原因

路由器 Web 後台 → USB Application 頁面顯示：

> **TM-T70II — 未啟用**

ASUS RT-N14UHP 的 USB Print Server 韌體有相容印表機清單。TM-T70II 為 ESC/POS 熱感 POS 印表機，**不在清單內**，路由器 USB 子系統無法初始化此裝置。

- Port 9100 / 515 雖開放，但路由器只接受 TCP 連線，不會將資料轉發給「未啟用」的印表機
- 協定（RAW TCP / LPR）層面完全正確，問題在更底層的 USB 驅動初始化

### 結論

| 方案 | 結果 |
|------|------|
| ASUS RT-N14UHP USB Print Server + TM-T70II | **不可行**，硬體韌體不支援 |
| USB OTG 直連（現有實作） | **可用，穩定** |

### 後續網路列印方向（待評估）

若日後需要網路列印，有以下替代方案：

| 方案 | 說明 | 難度 |
|------|------|------|
| 換購內建網路介面的印表機 | 如 EPSON TM-T82III 乙太網路版，直接暴露 RAW TCP port 9100，免中間裝置 | 低 |
| 換購獨立 USB Print Server | 如 TP-Link TL-PS110U，支援任意 USB 印表機的 RAW TCP 直送 | 低 |
| 路由器刷 OpenWrt + p910nd | 刷第三方韌體後安裝 raw printer daemon，可開放標準 port 9100 | 高（有磚機風險） |

**目前決策：維持 USB OTG 直連，網路列印方案留待後續評估。**

網路列印的 Android 端架構設計（`PrinterDispatcher` / `NetworkPrinterManager`）已記錄於 `PLANS/plan-network-printer.md` 備用。

