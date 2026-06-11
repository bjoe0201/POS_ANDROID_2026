# 印表機偵測（USB + 藍芽）設計與實作計畫

> 日期：2026-06-11
> 版本：v1.0

---

## 背景與目標

現有印表機設定僅支援 USB（ESC/POS），採硬編碼偵測 EPSON 裝置。
本次改版目標：

1. 新增「偵測印表機」掃描功能，同時列出 **USB** 與**已配對藍芽**裝置
2. 讓使用者從清單中選擇一台印表機並確認
3. 選定後執行測試列印
4. 選擇結果持久化（DataStore），下次開啟 App 自動使用

---

## 設計決策（已確認）

| 議題 | 決策 |
|------|------|
| 藍芽裝置來源 | 只列出手機**已配對**裝置（不主動掃描未配對裝置） |
| 持久化 | 儲存至 DataStore（`selectedPrinterType` + `selectedPrinterId`） |
| 新舊 UI 關係 | 完全取代現有 USB 狀態框 + 測試列印流程 |
| 架構 | 方案 A：新建 `PrinterDevice` + `PrinterManager`；`UsbPrinterManager` 內部邏輯保留不動 |
| 錯誤處理 | 若已儲存的印表機無法找到，App 啟動時顯示警告提示使用者重新偵測 |

---

## 架構概覽

```
util/
  PrinterDevice.kt       ← NEW：sealed class Usb / Bt
  PrinterManager.kt      ← NEW：掃描、列印路由、裝置解析
  UsbPrinterManager.kt   ← 保留，被 PrinterManager 呼叫（不動內部邏輯）

data/datastore/
  SettingsDataStore.kt   ← 新增 SELECTED_PRINTER_TYPE / SELECTED_PRINTER_ID

data/repository/
  SettingsRepository.kt  ← expose 新 Flow + setter

ui/settings/
  SettingsViewModel.kt   ← 訂閱新 key；掃描/選擇/測試邏輯
  SettingsScreen.kt      ← PrinterSection 全面改版

ui/order/
  OrderScreen.kt         ← printCheckoutReceipt 改呼叫 PrinterManager

ui/report/
  ReportScreen.kt        ← printOrderDetail 改呼叫 PrinterManager
  ReportViewModel.kt     ← printReport 改呼叫 PrinterManager；資料結構改用 PrinterManager 型別

AndroidManifest.xml      ← 新增藍芽權限
```

---

## 資料模型

### `util/PrinterDevice.kt`（新建）

```kotlin
sealed class PrinterDevice {
    data class Usb(val device: UsbDevice) : PrinterDevice()
    data class Bt(val device: BluetoothDevice) : PrinterDevice()

    val displayName: String
        get() = when (this) {
            is Usb -> device.productName ?: device.deviceName ?: "USB 裝置"
            is Bt  -> device.name ?: device.address
        }

    val typeKey: String
        get() = when (this) { is Usb -> "usb"; is Bt -> "bt" }

    // USB: deviceName；BT: MAC address
    val identifier: String
        get() = when (this) {
            is Usb -> device.deviceName
            is Bt  -> device.address
        }

    val typeLabel: String
        get() = when (this) { is Usb -> "USB"; is Bt -> "藍芽" }
}
```

---

## `util/PrinterManager.kt`（新建）

### 掃描

```kotlin
fun scanPrinters(context: Context): List<PrinterDevice>
```

- USB：`UsbManager.deviceList.values` → 包成 `PrinterDevice.Usb`
- 藍芽：`BluetoothAdapter.bondedDevices` → 包成 `PrinterDevice.Bt`
- API 31+ 需 `BLUETOOTH_CONNECT` 權限；無權限時回傳空藍芽清單（不 crash）

### 裝置解析

```kotlin
fun resolveDevice(context: Context, type: String, id: String): PrinterDevice?
```

- `type = "usb"` → 從 USB 裝置清單找 `deviceName == id`
- `type = "bt"` → 從已配對裝置找 `address == id`
- 找不到 → 回傳 `null`

### USB 權限

```kotlin
fun hasUsbPermission(context: Context, device: UsbDevice): Boolean
fun requestUsbPermission(context: Context, device: UsbDevice, onResult: (Boolean) -> Unit)
```
委派給 `UsbPrinterManager`。

### 列印（統一介面）

```kotlin
suspend fun printTestPage(context: Context, printer: PrinterDevice): Result<Unit>
suspend fun printCheckoutReceipt(context: Context, printer: PrinterDevice, ...): Result<Unit>
suspend fun printOrderDetail(context: Context, printer: PrinterDevice, ...): Result<Unit>
suspend fun printReport(context: Context, printer: PrinterDevice, snapshot: ReportPrintSnapshot): Result<Unit>
```

路由邏輯：
- `is PrinterDevice.Usb` → 委派 `UsbPrinterManager.*`（完全不動原始邏輯）
- `is PrinterDevice.Bt`  → `sendViaBluetooth(device.device, buildXxxBytes(...))`

### 藍芽傳輸（SPP）

```kotlin
private fun sendViaBluetooth(device: BluetoothDevice, data: ByteArray) {
    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP
    val socket = device.createRfcommSocketToServiceRecord(uuid)
    try {
        socket.connect()
        socket.outputStream.write(data)
        socket.outputStream.flush()
    } finally {
        socket.close()
    }
}
```

Bitmap 組裝委派相同的 `buildXxxBytes()` 方法（從 `UsbPrinterManager` 提取為 internal/package-level fun 或直接在 `UsbPrinterManager` 改為 `internal`）。

> **注意**：`UsbPrinterManager` 目前的 `buildCheckoutBytes`、`buildDetailBytes`、`buildReportBytes`、`buildTestPageBytes` 是 `private`。需改為 `internal` 讓 `PrinterManager` 能呼叫。

---

## DataStore 新增（`SettingsDataStore.kt`）

```kotlin
private val SELECTED_PRINTER_TYPE = stringPreferencesKey("selected_printer_type") // "usb"|"bt"|""
private val SELECTED_PRINTER_ID   = stringPreferencesKey("selected_printer_id")   // deviceName 或 BT MAC

val selectedPrinterType: Flow<String> = context.dataStore.data.map { it[SELECTED_PRINTER_TYPE] ?: "" }
val selectedPrinterId:   Flow<String> = context.dataStore.data.map { it[SELECTED_PRINTER_ID]   ?: "" }

suspend fun setSelectedPrinter(type: String, id: String) {
    context.dataStore.edit {
        it[SELECTED_PRINTER_TYPE] = type
        it[SELECTED_PRINTER_ID]   = id
    }
}

suspend fun clearSelectedPrinter() {
    context.dataStore.edit {
        it[SELECTED_PRINTER_TYPE] = ""
        it[SELECTED_PRINTER_ID]   = ""
    }
}
```

---

## SettingsRepository 新增

```kotlin
val selectedPrinterType: Flow<String>
val selectedPrinterId:   Flow<String>
suspend fun setSelectedPrinter(type: String, id: String)
suspend fun clearSelectedPrinter()
```

---

## SettingsUiState 新增欄位

```kotlin
data class SettingsUiState(
    // ... 現有欄位 ...
    val selectedPrinterType: String = "",
    val selectedPrinterId:   String = "",
    val selectedPrinterName: String = "",       // 顯示用，掃描解析後設定
    val selectedPrinterError: String? = null,   // 儲存的裝置消失時的錯誤訊息
    val scannedDevices: List<PrinterDevice> = emptyList(),  // 掃描結果（transient）
    val isScanning: Boolean = false,
)
```

---

## SettingsViewModel 新增

```kotlin
// init 中訂閱
settingsRepository.selectedPrinterType.onEach { ... }.launchIn(viewModelScope)
settingsRepository.selectedPrinterId.onEach { ... }.launchIn(viewModelScope)

// App 啟動後嘗試解析已儲存裝置
private fun validateSavedPrinter(context: Context) {
    val type = _uiState.value.selectedPrinterType
    val id   = _uiState.value.selectedPrinterId
    if (type.isBlank()) return
    val device = PrinterManager.resolveDevice(context, type, id)
    if (device == null) {
        _uiState.update {
            it.copy(selectedPrinterError = "上次選擇的印表機已無法使用，請重新偵測")
        }
    } else {
        _uiState.update { it.copy(selectedPrinterName = device.displayName, selectedPrinterError = null) }
    }
}

// 掃描（在 SettingsScreen 請求 BT 權限後呼叫）
fun scanPrinters(context: Context) {
    viewModelScope.launch {
        _uiState.update { it.copy(isScanning = true, scannedDevices = emptyList()) }
        val devices = withContext(Dispatchers.IO) { PrinterManager.scanPrinters(context) }
        _uiState.update { it.copy(isScanning = false, scannedDevices = devices) }
    }
}

// 選擇印表機
fun selectPrinter(printer: PrinterDevice) {
    viewModelScope.launch {
        settingsRepository.setSelectedPrinter(printer.typeKey, printer.identifier)
        _uiState.update {
            it.copy(
                selectedPrinterType  = printer.typeKey,
                selectedPrinterId    = printer.identifier,
                selectedPrinterName  = printer.displayName,
                selectedPrinterError = null,
                printerTestPassed    = false,   // 換了裝置，重置測試狀態
            )
        }
        settingsRepository.setPrinterTestPassed(false)
    }
}

// 清除選擇
fun clearSelectedPrinter() {
    viewModelScope.launch {
        settingsRepository.clearSelectedPrinter()
        settingsRepository.setPrinterTestPassed(false)
        _uiState.update {
            it.copy(
                selectedPrinterType = "",
                selectedPrinterId   = "",
                selectedPrinterName = "",
                printerTestPassed   = false,
            )
        }
    }
}
```

---

## UI：PrinterSection 改版（`SettingsScreen.kt`）

### 新流程

```
[1] 已選印表機欄位（或「尚未選擇印表機」灰字）
    - 若有 selectedPrinterError → 顯示橘/紅色警告文字
    - 「重新選擇」icon 按鈕（清除選擇，展開掃描區）

[2] 「偵測印表機」按鈕
    - 點擊 → 檢查 BLUETOOTH_CONNECT 權限（API 31+）→ 若需要則 Request
    - 掃描中：顯示 CircularProgressIndicator
    - 掃描完成：展開裝置清單

[3] 裝置清單（掃描後顯示）
    USB 裝置
    ┌─────────────────────────────────┐
    │ [USB]  EPSON TM-T70             │ ← 選擇
    └─────────────────────────────────┘
    藍芽裝置
    ┌─────────────────────────────────┐
    │ [BT]   MTP-II  AA:BB:CC:DD:EE   │ ← 選擇
    ├─────────────────────────────────┤
    │ [BT]   HC-06   11:22:33:44:55   │ ← 選擇
    └─────────────────────────────────┘
    - 點擊「選擇」：若為 USB → requestUsbPermission → 確認後 selectPrinter()
                   若為 BT  → 直接 selectPrinter()
    - 掃描結果為空：顯示「未偵測到任何裝置」

[4] 「測試列印」按鈕（僅在 selectedPrinterType != "" 時顯示）
    - 點擊 → PrinterManager.printTestPage(context, resolvedDevice)
    - 成功 → viewModel.setPrinterTestPassed(true)

[5] 功能開關（printerTestPassed == true 時顯示）
    - 收款結帳列印（不變）
    - 明細列印（不變）

[6] PDF列印機（不變，獨立於印表機選擇）
```

### 藍芽權限請求（SettingsScreen 內）

```kotlin
val btPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) viewModel.scanPrinters(context)
    else scope.launch { snackbarHostState.showSnackbar("需要藍芽權限才能列出藍芽裝置") }
}

fun startScan() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val perm = Manifest.permission.BLUETOOTH_CONNECT
        if (context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            btPermissionLauncher.launch(perm)
            return
        }
    }
    viewModel.scanPrinters(context)
}
```

---

## OrderScreen / ReportScreen / ReportViewModel 修改

### OrderScreen.kt

```kotlin
// 從 uiState 取得已儲存的印表機資訊（OrderViewModel 訂閱 SettingsRepository）
if (shouldPrint) {
    scope.launch {
        val type = uiState.selectedPrinterType
        val id   = uiState.selectedPrinterId
        val printer = PrinterManager.resolveDevice(context, type, id)
        if (printer != null) {
            PrinterManager.printCheckoutReceipt(context, printer, tName, tItems, tTotal, tRemark, tOrderId, tCreatedAt)
        }
    }
}
```

### OrderViewModel.kt

新增 UiState 欄位：
```kotlin
val selectedPrinterType: String = "",
val selectedPrinterId:   String = "",
```
訂閱 `settingsRepository.selectedPrinterType` / `selectedPrinterId`。

### ReportScreen.kt

```kotlin
val printer = PrinterManager.resolveDevice(context, selectedPrinterType, selectedPrinterId)
if (printer != null) {
    UsbPrinterManager.printOrderDetail(...)  // 改為 PrinterManager.printOrderDetail(context, printer, ...)
}
```

### ReportViewModel.kt

- `printCurrentReport` 改為呼叫 `PrinterManager.printReport(context, printer, snapshot)`
- `ReportPrintSnapshot` / `ReportGroupLine` 等資料類別繼續留在 `UsbPrinterManager`，`PrinterManager` import 使用即可（或搬至獨立檔案，但非必要）

---

## AndroidManifest.xml 新增權限

```xml
<!-- 藍芽 Classic（SPP 列印）API < 31 -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />

<!-- 藍芽 Classic API 31+（列出已配對裝置 + 連線） -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

---

## 版本遞增

- `APP_VERSION_CODE`：+1
- `APP_VERSION_NAME`：`1.2.16`
- `CLAUDE.md` / `DEVELOPER.md` 同步更新新增的 DataStore keys 與架構說明

---

## 實作順序

1. `PrinterDevice.kt`（新建）
2. `UsbPrinterManager.kt`：`buildXxxBytes` 改為 `internal`
3. `PrinterManager.kt`（新建）
4. `AndroidManifest.xml`：加藍芽權限
5. `SettingsDataStore.kt`：加兩個新 key
6. `SettingsRepository.kt`：expose 新 Flow + setter
7. `SettingsViewModel.kt`：新增狀態欄位 + 掃描/選擇/清除/驗證方法
8. `SettingsScreen.kt`：`PrinterSection` 改版
9. `OrderViewModel.kt`：訂閱 `selectedPrinterType/Id`
10. `OrderScreen.kt`：改呼叫 `PrinterManager`
11. `ReportViewModel.kt`：改呼叫 `PrinterManager`
12. `ReportScreen.kt`：改呼叫 `PrinterManager`
13. `gradle.properties`：版本遞增
14. `CLAUDE.md` / `DEVELOPER.md`：同步文件

---

## 已知限制與注意事項

- **BT SPP 連線時間**：`BluetoothSocket.connect()` 在 IO thread 可能需要 5–10 秒；列印時需顯示 loading 避免 ANR
- **BT 裝置名稱**：`BluetoothDevice.name` 在 API 31+ 需 `BLUETOOTH_CONNECT` 才能讀取；無權限時退回顯示 MAC address
- **USB 裝置在 BT 掃描時可能為空**：若沒有 OTG 連接，USB 清單為空，這是正常行為
- **換裝置後重置 `printerTestPassed`**：`selectPrinter()` 時必須將 `PRINTER_TEST_PASSED` 設為 `false`，避免舊測試狀態被沿用
- **`UsbPrinterManager` buildXxxBytes 改 internal**：需確認同一 module 內 `PrinterManager` 可存取
