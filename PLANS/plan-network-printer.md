# 網路列印伺服器功能設計文件

> 建立日期：2026-04-29
> 狀態：**草稿，待審查**
> 目標版本：v1.2.8

---

## 目錄

1. [背景與需求](#背景與需求)
2. [硬體環境](#硬體環境)
3. [架構設計](#架構設計)
4. [新增 DataStore 設定鍵](#新增-datastore-設定鍵)
5. [PrinterManager 抽象層](#printermanager-抽象層)
6. [NetworkPrinterManager 實作](#networkprintermanager-實作)
7. [UI 流程 — 設定頁印表機區塊重設計](#ui-流程--設定頁印表機區塊重設計)
8. [連線測試流程](#連線測試流程)
9. [既有列印呼叫的影響](#既有列印呼叫的影響)
10. [需修改檔案](#需修改檔案)
11. [驗證項目](#驗證項目)
12. [已知限制與注意事項](#已知限制與注意事項)

---

## 背景與需求

目前 `UsbPrinterManager` 僅支援 Android OTG/USB Host 直接連接印表機。
新需求：將 EPSON TM-T70II 插入 **ASUS RT-N14UHP** 路由器的 USB 埠，透過路由器內建的 **USB 列印伺服器（Print Server）** 功能，讓 Android 平板以 TCP/IP 網路方式連線列印。

主要變更：
- 設定頁新增「連線方式」下拉選單：`USB 直接連線` / `網路列印伺服器`
- 選擇「網路列印伺服器」後，顯示 IP 位址與連接埠輸入欄位
- 所有列印呼叫（測試列印、收款收據、訂單明細、報表列印）透過統一的分派層自動路由至正確的連線方式

---

## 硬體環境

| 項目 | 值 |
|------|----|
| 印表機 | EPSON TM-T70II |
| 路由器 | ASUS RT-N14UHP |
| 路由器 USB 列印伺服器協定 | RAW TCP（預設 port **9100**，即 JetDirect） |
| Android 裝置與路由器連線 | Wi-Fi（同一區域網路） |
| 路由器預設 IP（常見） | `192.168.1.1`（依實際環境不同） |

### ASUS RT-N14UHP 列印伺服器說明

ASUS RT-N14UHP 支援 USB 共享列印，Android 端透過 **RAW TCP Socket** 直接傳送 ESC/POS bytes 到路由器的指定 IP + Port 即可列印，與 USB 直連模式的 byte stream 完全相同，**無需修改 ESC/POS 指令邏輯**。

通訊流程：
```
Android App
  → Socket(ip, port)（預設 9100）
  → 寫入 ESC/POS bytes
  → close socket
  → 印表機列印
```

> 部分路由器列印伺服器也支援 LPD（port 515），但 RAW TCP 實作最簡單，建議優先使用。

---

## 架構設計

### 分派策略

不修改既有 `UsbPrinterManager` 的任何公開 API，改新增：

1. **`NetworkPrinterManager`**（新檔案 `util/NetworkPrinterManager.kt`）
   - 封裝 TCP Socket 連線與 bytes 傳送邏輯
   - 公開與 `UsbPrinterManager` 相同簽名的列印 API

2. **`PrinterDispatcher`**（新檔案 `util/PrinterDispatcher.kt`）
   - 讀取 `printerMode` 設定，路由至 `UsbPrinterManager` 或 `NetworkPrinterManager`
   - 呼叫端（`OrderScreen`、`ReportViewModel`、`SettingsScreen`）改呼叫 `PrinterDispatcher`

### 元件關係圖

```
SettingsScreen / OrderScreen / ReportViewModel
        |
        v
  PrinterDispatcher          ← 讀取 printerMode 設定，分派
       /       \
UsbPrinterManager   NetworkPrinterManager
 (USB OTG/Host)       (TCP Socket)
```

### 為何採用分派層而非繼承

- `UsbPrinterManager` 為 `object`（singleton），已在多處直接引用；大幅重構風險高
- `NetworkPrinterManager` 同樣設計為 `object`，兩者邏輯完全獨立
- `PrinterDispatcher` 為輕量 `object`，只負責讀 context 取設定後分派，邏輯簡單、易測試

---

## 新增 DataStore 設定鍵

### `SettingsDataStore.kt` 新增

```kotlin
private val PRINTER_MODE              = stringPreferencesKey("printer_mode")          // "USB" | "NETWORK"
private val NETWORK_PRINTER_IP        = stringPreferencesKey("network_printer_ip")    // e.g. "192.168.1.1"
private val NETWORK_PRINTER_PORT      = intPreferencesKey("network_printer_port")     // 預設 9100
private val NETWORK_PRINTER_TEST_PASSED = booleanPreferencesKey("network_printer_test_passed") // 網路測試是否通過
```

### Flows & Setters

```kotlin
val printerMode: Flow<String> = context.dataStore.data.map { it[PRINTER_MODE] ?: "USB" }
val networkPrinterIp: Flow<String> = context.dataStore.data.map { it[NETWORK_PRINTER_IP] ?: "" }
val networkPrinterPort: Flow<Int> = context.dataStore.data.map { it[NETWORK_PRINTER_PORT] ?: 9100 }
val networkPrinterTestPassed: Flow<Boolean> = context.dataStore.data.map { it[NETWORK_PRINTER_TEST_PASSED] ?: false }

suspend fun setPrinterMode(v: String) { context.dataStore.edit { it[PRINTER_MODE] = v } }
suspend fun setNetworkPrinterIp(v: String) { context.dataStore.edit { it[NETWORK_PRINTER_IP] = v } }
suspend fun setNetworkPrinterPort(v: Int) { context.dataStore.edit { it[NETWORK_PRINTER_PORT] = v } }
suspend fun setNetworkPrinterTestPassed(v: Boolean) { context.dataStore.edit { it[NETWORK_PRINTER_TEST_PASSED] = v } }
```

### PrinterMode 常數

```kotlin
// util/PrinterMode.kt（或直接在 SettingsDataStore companion 定義）
object PrinterMode {
    const val USB     = "USB"
    const val NETWORK = "NETWORK"
}
```

> 注意：`printerTestPassed`（USB 測試通過）與 `networkPrinterTestPassed`（網路測試通過）分開存儲。
> 切換連線方式時，功能 Switch（收款列印、明細列印）的顯示條件改為：
> - USB 模式：`printerTestPassed == true`
> - 網路模式：`networkPrinterTestPassed == true`

---

## PrinterManager 抽象層

### `PrinterDispatcher.kt`

```kotlin
object PrinterDispatcher {

    suspend fun printTestPage(context: Context): Result<Unit> {
        return when (getPrinterMode(context)) {
            PrinterMode.NETWORK -> NetworkPrinterManager.printTestPage(context)
            else -> {
                val device = UsbPrinterManager.findPrinterDevice(context)
                    ?: return Result.failure(Exception("找不到 USB 印表機"))
                UsbPrinterManager.printTestPage(context, device)
            }
        }
    }

    suspend fun printCheckoutReceipt(
        context: Context,
        tableName: String,
        items: List<OrderItemEntity>,
        total: Double,
        remark: String,
        orderId: Long = 0L,
        createdAt: Long = System.currentTimeMillis()
    ): Result<Unit> {
        return when (getPrinterMode(context)) {
            PrinterMode.NETWORK -> NetworkPrinterManager.printCheckoutReceipt(
                context, tableName, items, total, remark, orderId, createdAt
            )
            else -> {
                val device = UsbPrinterManager.findPrinterDevice(context)
                    ?: return Result.failure(Exception("找不到 USB 印表機"))
                UsbPrinterManager.printCheckoutReceipt(
                    context, device, tableName, items, total, remark, orderId, createdAt
                )
            }
        }
    }

    suspend fun printOrderDetail(
        context: Context,
        orderId: Long, tableName: String, createdAt: Long,
        items: List<OrderItemEntity>, total: Double
    ): Result<Unit> { /* 同上模式分派 */ }

    suspend fun printReport(
        context: Context,
        snapshot: ReportPrintSnapshot
    ): Result<Unit> { /* 同上模式分派 */ }

    // ── 連線測試（僅網路模式使用）──
    suspend fun testNetworkConnection(context: Context): Result<Unit> {
        return NetworkPrinterManager.testConnection(context)
    }

    private suspend fun getPrinterMode(context: Context): String {
        // 讀 DataStore（one-shot first()）
        return context.dataStore.data.map { it[PRINTER_MODE_KEY] ?: PrinterMode.USB }.first()
    }
}
```

> **呼叫端遷移**：`OrderScreen`、`ReportViewModel`、`SettingsScreen` 的列印呼叫改為 `PrinterDispatcher.printXxx(...)`，移除對 `UsbPrinterManager` 的直接引用（或保留相容，視修改量決定）。

---

## NetworkPrinterManager 實作

### `util/NetworkPrinterManager.kt`

```kotlin
object NetworkPrinterManager {

    private const val CONNECT_TIMEOUT_MS = 5_000   // 連線逾時 5 秒
    private const val WRITE_TIMEOUT_MS   = 10_000  // 寫入逾時 10 秒

    // ── 連線測試（不列印，僅建立 Socket 並立即關閉）──
    suspend fun testConnection(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val ip   = getIp(context)
        val port = getPort(context)
        if (ip.isBlank()) return@withContext Result.failure(Exception("尚未設定 IP 位址"))
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                // 連線成功，立即關閉
            }
        }
    }

    // ── 測試列印（與 UsbPrinterManager 相同版面，透過網路傳送）──
    suspend fun printTestPage(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val bytes = UsbPrinterManager.buildTestPageBytes()   // 重用既有 bytes 組裝邏輯
        sendBytes(context, bytes)
    }

    suspend fun printCheckoutReceipt(
        context: Context,
        tableName: String,
        items: List<OrderItemEntity>,
        total: Double,
        remark: String,
        orderId: Long,
        createdAt: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bytes = UsbPrinterManager.buildCheckoutReceiptBytes(
            tableName, items, total, remark, orderId, createdAt
        )
        sendBytes(context, bytes)
    }

    suspend fun printOrderDetail(
        context: Context,
        orderId: Long, tableName: String, createdAt: Long,
        items: List<OrderItemEntity>, total: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bytes = UsbPrinterManager.buildOrderDetailBytes(orderId, tableName, createdAt, items, total)
        sendBytes(context, bytes)
    }

    suspend fun printReport(
        context: Context,
        snapshot: ReportPrintSnapshot
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bytes = UsbPrinterManager.buildReportBytes(snapshot)
        sendBytes(context, bytes)
    }

    // ── 核心：透過 TCP Socket 傳送 bytes ──
    private suspend fun sendBytes(context: Context, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            val ip   = getIp(context)
            val port = getPort(context)
            if (ip.isBlank()) return@withContext Result.failure(Exception("尚未設定印表機 IP 位址"))
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = WRITE_TIMEOUT_MS
                    socket.getOutputStream().apply {
                        write(bytes)
                        flush()
                    }
                }
            }
        }

    private suspend fun getIp(context: Context): String =
        context.dataStore.data.map { it[NETWORK_PRINTER_IP_KEY] ?: "" }.first()

    private suspend fun getPort(context: Context): Int =
        context.dataStore.data.map { it[NETWORK_PRINTER_PORT_KEY] ?: 9100 }.first()
}
```

### UsbPrinterManager 需要的重構

目前 `UsbPrinterManager` 的 `buildXxxBytes()` 方法為私有（`private`）。
需將以下方法改為 `internal` 或 `fun`，供 `NetworkPrinterManager` 重用：

| 現有私有方法 | 改為 |
|------------|------|
| `buildTestPageBytes(): ByteArray` | `internal fun` |
| `buildCheckoutReceiptBytes(...): ByteArray` | `internal fun` |
| `buildOrderDetailBytes(...): ByteArray` | `internal fun` |
| `buildReportBytes(snapshot): ByteArray` | `internal fun`（已存在，確認可見性） |

> 這些方法純粹組裝 ESC/POS bytes，**不依賴 USB 連線**，抽出後可被網路模式零成本重用。

### AndroidManifest.xml 新增網路權限

```xml
<!-- 已存在，確認有無 INTERNET 權限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## UI 流程 — 設定頁印表機區塊重設計

### 現有結構（USB 模式）

```
[印表機] Section
  ├── 說明文字
  ├── 狀態訊息框
  ├── [測試列印] 按鈕
  └── [若 printerTestPassed]
        ├── Switch：收款結帳列印
        └── Switch：明細列印
```

### 新結構（加入連線方式選擇）

```
[印表機] Section
  │
  ├── 連線方式（下拉選單）
  │     ├── USB 直接連線      ← 預設
  │     └── 網路列印伺服器
  │
  ├── [USB 直接連線 模式顯示]
  │     ├── 說明文字（Android OTG/USB Host）
  │     ├── 狀態訊息框（deviceName、VendorID、授權狀態）
  │     └── [測試列印] 按鈕
  │
  ├── [網路列印伺服器 模式顯示]
  │     ├── IP 位址輸入欄（TextField）  e.g. "192.168.1.1"
  │     ├── 連接埠輸入欄（TextField）   預設 "9100"
  │     ├── 說明文字（RAW TCP，ASUS RT-N14UHP 預設 port 9100）
  │     ├── [測試連線] 按鈕  ← 僅測試 TCP 連線是否通，不列印
  │     └── [測試列印] 按鈕  ← 測試連線通過後才啟用
  │
  └── [若 對應模式的測試通過]
        ├── Switch：收款結帳列印
        └── Switch：明細列印
```

### Composable 草稿

```kotlin
@Composable
fun PrinterSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    context: Context
) {
    // 連線方式下拉
    PrinterModeDropdown(
        selected = uiState.printerMode,
        onSelect = { viewModel.setPrinterMode(it) }
    )

    when (uiState.printerMode) {
        PrinterMode.USB -> {
            UsbPrinterSubSection(uiState, viewModel, context)
        }
        PrinterMode.NETWORK -> {
            NetworkPrinterSubSection(uiState, viewModel, context)
        }
    }

    // 功能 Switch（共用，依對應測試狀態顯示）
    val testPassed = when (uiState.printerMode) {
        PrinterMode.NETWORK -> uiState.networkPrinterTestPassed
        else                -> uiState.printerTestPassed
    }
    if (testPassed) {
        PrinterFeatureSwitches(uiState, viewModel)
    }
}
```

#### NetworkPrinterSubSection

```kotlin
@Composable
fun NetworkPrinterSubSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    context: Context,
    scope: CoroutineScope
) {
    // IP 輸入
    OutlinedTextField(
        value = uiState.networkPrinterIp,
        onValueChange = { viewModel.setNetworkPrinterIp(it) },
        label = { Text("印表機 IP 位址") },
        placeholder = { Text("192.168.1.1") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )

    // Port 輸入
    OutlinedTextField(
        value = uiState.networkPrinterPort.toString(),
        onValueChange = { viewModel.setNetworkPrinterPort(it.toIntOrNull() ?: 9100) },
        label = { Text("連接埠") },
        placeholder = { Text("9100") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    // 說明
    Text("RAW TCP 協定（JetDirect）。ASUS RT-N14UHP USB 列印伺服器預設 port 9100。")

    // 狀態訊息框（顯示連線測試結果）
    if (uiState.networkPrinterStatusMsg.isNotBlank()) {
        NetworkStatusBox(uiState.networkPrinterStatusMsg)
    }

    Row {
        // 測試連線按鈕
        OutlinedButton(
            enabled = uiState.networkPrinterIp.isNotBlank() && !uiState.isTestingNetwork,
            onClick = { scope.launch { viewModel.testNetworkConnection(context) } }
        ) {
            Text(if (uiState.isTestingNetwork) "連線測試中…" else "測試連線")
        }

        Spacer(Modifier.width(8.dp))

        // 測試列印按鈕（需先連線測試通過）
        Button(
            enabled = uiState.networkPrinterTestPassed && !uiState.isTestingNetwork,
            onClick = { scope.launch { viewModel.networkTestPrint(context) } }
        ) {
            Text("測試列印")
        }
    }
}
```

---

## 連線測試流程

### 測試連線（不列印）

```
1. 使用者輸入 IP + Port
2. 點擊「測試連線」
3. SettingsViewModel.testNetworkConnection(context)
     → 設定 isTestingNetwork = true
     → PrinterDispatcher.testNetworkConnection(context)
       → NetworkPrinterManager.testConnection(context)
         → Socket().connect(InetSocketAddress(ip, port), 5000ms)
     → 成功：setNetworkPrinterTestPassed(true) + statusMsg = "連線成功 (ip:port)"
     → 失敗：setNetworkPrinterTestPassed(false) + statusMsg = "連線失敗：{error}"
     → finally：isTestingNetwork = false
```

### 測試列印

```
1. 測試連線通過後，「測試列印」按鈕啟用
2. 點擊「測試列印」
3. SettingsViewModel.networkTestPrint(context)
     → PrinterDispatcher.printTestPage(context)
       → NetworkPrinterManager.printTestPage(context)
         → UsbPrinterManager.buildTestPageBytes()
         → sendBytes(ip, port, bytes)
     → 成功：statusMsg = "測試列印成功"
     → 失敗：statusMsg = "測試列印失敗：{error}"
```

### IP / Port 變更時的行為

- 使用者修改 IP 或 Port 時，`networkPrinterTestPassed` 自動重設為 `false`（需重新測試）
- 功能 Switch 隨之隱藏，避免使用舊設定的連線狀態

```kotlin
fun setNetworkPrinterIp(v: String) {
    viewModelScope.launch {
        settingsRepository.setNetworkPrinterIp(v)
        settingsRepository.setNetworkPrinterTestPassed(false)  // 重設
    }
}
fun setNetworkPrinterPort(v: Int) {
    viewModelScope.launch {
        settingsRepository.setNetworkPrinterPort(v)
        settingsRepository.setNetworkPrinterTestPassed(false)  // 重設
    }
}
```

---

## SettingsUiState 新增欄位

```kotlin
data class SettingsUiState(
    // ... 既有欄位 ...

    // 新增
    val printerMode: String = PrinterMode.USB,
    val networkPrinterIp: String = "",
    val networkPrinterPort: Int = 9100,
    val networkPrinterTestPassed: Boolean = false,
    val networkPrinterStatusMsg: String = "",
    val isTestingNetwork: Boolean = false,

    // ... message 欄位 ...
)
```

---

## 既有列印呼叫的影響

### 呼叫端遷移對照表

| 檔案 | 現有呼叫 | 改為 |
|------|----------|------|
| `SettingsScreen.kt` | `UsbPrinterManager.printTestPage(context, device)` | `PrinterDispatcher.printTestPage(context)` |
| `OrderScreen.kt` | `UsbPrinterManager.printCheckoutReceipt(...)` | `PrinterDispatcher.printCheckoutReceipt(...)` |
| `ReportScreen.kt` | `UsbPrinterManager.printOrderDetail(...)` | `PrinterDispatcher.printOrderDetail(...)` |
| `ReportViewModel.kt` | `UsbPrinterManager.printReport(...)` | `PrinterDispatcher.printReport(...)` |

> 呼叫簽名不變，只替換類別名稱。USB 模式下行為與現在完全一致，不影響已驗證的 USB 列印流程。

### USB 模式中 findPrinterDevice 的處理

`PrinterDispatcher` 在 USB 模式下負責呼叫 `UsbPrinterManager.findPrinterDevice(context)`，並在找不到裝置時回傳 `Result.failure`，錯誤訊息與現有行為相同。

---

## 需修改檔案

| 檔案 | 變更摘要 |
|------|----------|
| `util/NetworkPrinterManager.kt` | **新增**：TCP Socket 連線 + 列印邏輯 |
| `util/PrinterDispatcher.kt` | **新增**：USB / 網路分派層 |
| `util/UsbPrinterManager.kt` | 將 `buildXxxBytes()` 私有方法改為 `internal fun`，供 `NetworkPrinterManager` 重用 |
| `data/datastore/SettingsDataStore.kt` | 新增 4 個 DataStore key + flows + setters |
| `data/repository/SettingsRepository.kt` | 新增對應 flows / setters |
| `ui/settings/SettingsViewModel.kt` | 新增 printerMode、networkPrinterIp/Port/TestPassed、isTestingNetwork 欄位；新增 testNetworkConnection、networkTestPrint、setPrinterMode 等方法 |
| `ui/settings/SettingsScreen.kt` | 重設計 `PrinterSection`：新增連線方式下拉、`NetworkPrinterSubSection` composable |
| `ui/order/OrderScreen.kt` | 呼叫改為 `PrinterDispatcher.printCheckoutReceipt(...)` |
| `ui/report/ReportViewModel.kt` | 呼叫改為 `PrinterDispatcher.printReport(...)` |
| `ui/report/ReportScreen.kt` | 呼叫改為 `PrinterDispatcher.printOrderDetail(...)` |
| `AndroidManifest.xml` | 確認 `INTERNET` 與 `ACCESS_NETWORK_STATE` 權限存在 |
| `CLAUDE.md` | 更新 DataStore keys、架構說明 |
| `README.md` | 更新印表機功能說明 |
| `gradle.properties` | `APP_VERSION_CODE` +1、`APP_VERSION_NAME` → `1.2.8` |

---

## 驗證項目

### 建置

- [ ] `gradlew.bat assembleDebug` 可成功建置，無編譯錯誤

### USB 模式（回歸測試）

- [ ] 連線方式切換回「USB 直接連線」後，行為與 v1.2.7 完全相同
- [ ] 測試列印成功後 Switch 顯示
- [ ] 收款結帳列印、訂單明細列印、報表列印正常

### 網路模式

- [ ] 切換至「網路列印伺服器」後顯示 IP / Port 輸入欄
- [ ] IP 或 Port 留空時「測試連線」按鈕停用
- [ ] 輸入正確 IP + 9100 後「測試連線」成功，顯示「連線成功 (192.168.x.x:9100)」
- [ ] 輸入錯誤 IP 後「測試連線」失敗，顯示「連線失敗：Connection refused / timeout」
- [ ] 連線測試通過後「測試列印」啟用，列印收據版面正確
- [ ] 修改 IP / Port 後 `networkPrinterTestPassed` 自動重設，Switch 隱藏
- [ ] 收款結帳列印（網路模式）正常
- [ ] 報表列印（網路模式）正常，長報表不截斷
- [ ] 連線失敗時 Snackbar 顯示明確錯誤訊息，不靜默失敗

### 模式切換

- [ ] USB → 網路切換後，功能 Switch 依網路測試狀態正確顯示 / 隱藏
- [ ] 網路 → USB 切換後，功能 Switch 依 USB 測試狀態正確顯示 / 隱藏
- [ ] App 重啟後連線方式設定保持不變

---

## 已知限制與注意事項

### 網路延遲

TCP 連線比 USB 直連多約 10~50ms 延遲（區域網路），實際列印速度不受影響。
若路由器繁忙或 Wi-Fi 訊號差，`CONNECT_TIMEOUT_MS = 5000ms` 可於設定中調整（未來版本）。

### ASUS RT-N14UHP 列印伺服器注意

- 路由器 USB 列印伺服器功能需在路由器管理介面手動啟用（USB Application → Network Printer Server）
- 預設 port 為 **9100**（部分 ASUS 韌體為 **515 LPD** 或 **9100 RAW**，建議先測試 9100）
- 如遇連線被拒，請確認路由器防火牆未封鎖 9100 port
- 部分舊版 ASUS 韌體的 USB 列印伺服器只支援 Windows LPR 協定，Android 端可能無法直接用 RAW TCP；此情況建議更新路由器韌體或改用 USB 直連模式

### 同時只有一個連線

RAW TCP 模式下，若多台裝置同時對 port 9100 發送列印請求，路由器列印伺服器通常會佇列處理，但行為依韌體而異。本 App 為單裝置 POS，此問題不適用。

### bytes 重用的前提

`NetworkPrinterManager` 完全重用 `UsbPrinterManager.buildXxxBytes()`，意即紙張寬度（`PRINT_WIDTH_PX = 360`）與版面設定由 `UsbPrinterManager.kt` 頂部常數控制，兩種連線方式共用同一組版面參數。若日後需要網路模式使用不同紙張寬度，再另行抽出設定鍵。
