# CLAUDE.md

> **重要提醒：**
> - 使用者操作說明（含畫面截圖）請見 [`README.md`](README.md)。
> - 完整技術開發文件（建置指令、架構、資料庫、版本管理）請見 [`DEVELOPER.md`](DEVELOPER.md)。
> - 每次異動架構或導航流程時，必須同步更新 `README.md`、`DEVELOPER.md` 與本文件。

此檔案提供 Claude Code（claude.ai/code）在此儲存庫中進行程式碼作業時的指引。

## 建置指令

```bash
# Debug 建置
./gradlew assembleDebug

# Release 建置
./gradlew assembleRelease

# 安裝到已連接裝置
./gradlew installDebug

# 執行單元測試
./gradlew test

# 執行儀器測試（需要已連接裝置/模擬器）
./gradlew connectedAndroidTest

# 執行單一測試類別
./gradlew test --tests "com.pos.app.ExampleUnitTest"

# Lint
./gradlew lint
```

在 Windows 上請使用 `gradlew.bat` 取代 `./gradlew`。

## 固定 APK 發佈流程（每次 GitHub Release 必做）

GitHub Releases 對外提供的 APK 必須是 `assembleRelease` 產生的 **已簽章 release APK**，可直接安裝；嚴禁上傳 debug APK、unsigned APK、或 Android Debug 憑證簽章的 APK。

- 正確上傳檔：`app/build/outputs/apk/release/app-release.apk`
- Release asset 命名：`POS_ANDROID_2026-vX.Y.Z-release.apk`
- 禁止上傳：`app-debug.apk`、`app-release-unsigned.apk`、任何 debug-signed APK
- 禁止提交或上傳：`keystore.properties`、`*.jks`、密碼、mapping 檔、unsigned APK
- 本專案 `app/build.gradle.kts` 在執行 Release 任務時會要求完整簽章設定，缺少 `keystore.properties` 必要欄位會直接建置失敗，避免誤發 unsigned APK。

### Release signing setup

專案根目錄需有本機私密檔 `keystore.properties`（已由 `.gitignore` 排除），必要欄位：

```properties
storeFile=pos-release.jks
storePassword=private-store-password
keyAlias=pos-release
keyPassword=private-key-password
```

### 0) 發佈前檢查（Windows PowerShell）

```powershell
git branch --show-current
git remote -v
gh auth status
gh release list --limit 100
```

確認：

- 分支為預期發佈分支（通常 `main`）。
- 遠端為 `https://github.com/bjoe0201/POS_ANDROID_2026.git`。
- GitHub CLI 登入正確帳號。
- `gradle.properties` 的 `APP_VERSION_CODE` 已遞增，`APP_VERSION_NAME` 與 release tag（去掉 `v`）一致。

### 1) 建置可安裝 release APK

```powershell
.\gradlew.bat clean assembleRelease
```

輸出檔案：

```text
app/build/outputs/apk/release/app-release.apk
```

檔案與 SHA256 檢查：

```powershell
$apk = ".\app\build\outputs\apk\release\app-release.apk"
Test-Path $apk
Get-Item $apk
Get-FileHash $apk -Algorithm SHA256
```

### 2) 簽章驗證（必做）

```powershell
$sdk = if (Test-Path .\local.properties) {
  Get-Content .\local.properties |
    Where-Object { $_ -like 'sdk.dir=*' } |
    Select-Object -First 1 |
    ForEach-Object { ($_ -replace '^sdk.dir=', '') -replace '\\:', ':' -replace '\\\\', '\' }
}
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw 'Android SDK path not found. Set sdk.dir in local.properties or ANDROID_HOME/ANDROID_SDK_ROOT.' }
$apksigner = Get-ChildItem -Path (Join-Path $sdk 'build-tools') -Recurse -Filter 'apksigner.bat' |
  Sort-Object FullName -Descending |
  Select-Object -First 1
if (-not $apksigner) { throw 'apksigner.bat not found under Android SDK build-tools.' }
& $apksigner.FullName verify --verbose --print-certs .\app\build\outputs\apk\release\app-release.apk
```

驗證結果必須包含：

```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
```

Signer certificate 不可為 `CN=Android Debug`。

### 3) 上傳 GitHub Release APK

```powershell
$version = "vX.Y.Z"
$asset = ".\app\build\outputs\apk\release\POS_ANDROID_2026-$version-release.apk"
Copy-Item ".\app\build\outputs\apk\release\app-release.apk" $asset -Force
gh release upload $version $asset --clobber
```

若先前誤傳錯誤 asset，先刪除：

```powershell
gh release delete-asset vX.Y.Z app-debug.apk --yes
gh release delete-asset vX.Y.Z app-release-unsigned.apk --yes
gh release delete-asset vX.Y.Z wrong-asset-name.apk --yes
```

### 4) 發佈後驗證

```powershell
gh release view vX.Y.Z --json tagName,name,url,publishedAt,assets
gh release list --limit 100
```

Release asset 應只保留當版 signed APK，例如：

```text
POS_ANDROID_2026-v1.2.10-release.apk
```

可比對本機與 GitHub asset SHA256：

```powershell
$version = "vX.Y.Z"
$localHash = (Get-FileHash ".\app\build\outputs\apk\release\app-release.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
$release = gh release view $version --json assets | ConvertFrom-Json
$asset = $release.assets | Where-Object { $_.name -eq "POS_ANDROID_2026-$version-release.apk" }
$remoteHash = $asset.digest -replace '^sha256:', ''
[pscustomobject]@{ LocalHash = $localHash; RemoteHash = $remoteHash; Match = ($localHash -eq $remoteHash) }
```

### 安裝注意事項

- 若裝置已安裝 debug-signed APK，可能因簽章不同無法覆蓋安裝；需先解除安裝舊 App。
- 每次 release 必須遞增 `APP_VERSION_CODE`，否則 Android 會拒絕降版或同版覆蓋安裝。
- 未來所有公開版本需固定使用同一把 release keystore；遺失或更換 keystore 會導致既有使用者無法正常更新。

## 版本更新方式

- 版本參數統一維護在 `gradle.properties`：
  - `APP_VERSION_CODE`：整數，每次發版都要遞增。
  - `APP_VERSION_NAME`：顯示版號，建議使用 `major.minor.patch`（例如 `1.0.1`）。
- `app/build.gradle.kts` 會讀取上述參數並寫入 `defaultConfig.versionCode` 與 `defaultConfig.versionName`。
- 更新流程：
  1. 修改 `gradle.properties` 的 `APP_VERSION_CODE` 與 `APP_VERSION_NAME`。
  2. 重新建置（Windows：`gradlew.bat assembleDebug` 或 `gradlew.bat assembleRelease`）。
  3. 登入頁與主程式畫面的版號顯示會自動跟著更新。

## 架構

**套件：** `com.pos.app` | **Min SDK：** 29（Android 10）| **Target SDK：** 35

### 分層結構

```
data/
  datastore/SettingsDataStore.kt   — 以 Jetpack DataStore 儲存 PIN 雜湊（SHA-256）及各項設定
  db/
    entity/                        — Room entities（6 張資料表，含 reservations）
    dao/                           — Room DAOs
    AppDatabase.kt                 — 單例；首次建立時預植入預設菜單與 8 張桌號
  repository/                      — 單一資料真實來源；透過 Hilt 注入至 ViewModels
ui/
  navigation/NavGraph.kt           — 根導航：Login → Home；Home 包含底部 6 個分頁的巢狀導航
  login / order / reservation / menu / table / report / settings / theme
util/BackupManager.kt              — 透過 SAF（Storage Access Framework）進行 ZIP 備份匯出/匯入
util/UsbPrinterManager.kt          — USB 熱感印表機列印（測試頁、收款收據、訂單明細、報表列印）
util/DatePickerDateUtils.kt        — Material3 DatePicker UTC 日期毫秒 ↔ 本機日期日界線轉換
```

### 導航流程

`LoginScreen` →（PIN 驗證通過）→ `HomeWithBottomNav`（**6 個**底部分頁）

底部分頁（依序）：**記帳**（`OrderScreen`）· **訂位**（`ReservationScreen`）· **菜單管理**（`MenuManagementScreen`）· **桌號設定**（`TableSettingScreen`）· **報表**（`ReportScreen`）· **設定**（`SettingsScreen`）

- **記帳** 與 **設定** 為必要分頁，永遠顯示。
- 其餘分頁可於「設定 → 功能頁面」個別開關；停用分頁若正在顯示，系統自動跳回記帳頁。

### 資料庫結構

| Table | Key fields |
|-------|-----------|
| `menu_groups` | code, name, sortOrder, isActive |
| `menu_items` | id, name, price, category, isAvailable, sortOrder |
| `orders` | id, **tableId** (FK→tables), **tableName** (snapshot), remark, createdAt, closedAt, status |
| `order_items` | id, orderId, menuItemId, name/price (snapshot), **menuGroupCode/menuGroupName (snapshot)**, quantity |
| `tables` | id, tableName (≤20 chars), seats, remark, isActive, sortOrder |

`OrderEntity.tableName` 是快照欄位——即使桌號後續被重新命名或刪除，仍可維持可讀性。

### DI（Hilt）

所有 DAOs、Repositories 與 `SettingsDataStore` 皆在 `AppModule` 以 `@Singleton` 提供。ViewModels 使用 `@HiltViewModel`。應用程式進入點為 `POSApplication`（`@HiltAndroidApp`）與 `MainActivity`（`@AndroidEntryPoint`）。

### 重要常數

- `CATEGORIES` 清單（順序 + 顯示名稱）位於 `ui/order/OrderViewModel.kt`，並由 menu 與其他畫面匯入使用。
- 預設 PIN：`1234`（SHA-256 雜湊）。連續輸入錯誤 3 次會鎖定 30 秒。
- 預設桌號：8 張（預植入為「1號桌」至「8號桌」）；可於 TableSettingScreen 進行 CRUD 調整。
- **日期選擇器時區轉換**：Material3 `DatePicker` 的 `selectedDateMillis` / `initialSelectedDateMillis` 是 UTC 日期午夜；App 內部日期狀態使用本機日界線 millis。所有 DatePicker 進出都必須透過 `DatePickerDateUtils`，避免台灣時區今天顯示成昨天。
- **長按連續加減**：記帳頁 `+` / `−` 按鈕長按超過 `qty_repeat_initial_delay_ms`（預設 1000ms）後，依 `qty_repeat_interval_ms`（預設 100ms）連續觸發；按住或單擊時卡片上方以 Popup 顯示數字氣泡（+ 亮黃 / − 亮綠），單擊放開後保留 600ms 才隱藏。觸覺回饋可於設定 `haptic_enabled` 整體開關，使用 `LocalHapticFeedback`。實作位於 `OrderScreen.kt` 的 `RepeatableQtyButton` 與 `MenuCard`。
- **DataStore keys**（`SettingsDataStore`）：含 PIN、Tab 開關、營業時間、訂位、自動備份，以及 `QTY_REPEAT_INTERVAL_MS` / `QTY_REPEAT_INITIAL_DELAY_MS`（點餐長按連續加減速度／啟動延遲）、`HAPTIC_ENABLED`（觸覺回饋開關，預設開啟）、`PRINTER_TEST_PASSED`（印表機測試已通過）、`PRINT_CHECKOUT_ENABLED`（收款結帳自動列印）、`PRINT_DETAIL_ENABLED`（報表明細列印按鈕）、`CLOUD_BACKUP_ENABLED`（雲端備份開關，預設關閉）、`CLOUD_BACKUP_TREE_URI`（雲端備份 SAF tree URI）、`PDF_PRINTER_ENABLED`（PDF列印機自動存放開關，預設關閉）、`PDF_PRINTER_TREE_URI`（PDF 自動存檔目錄 SAF tree URI）。

### OrderUiState 關鍵欄位（v1.2.7 新增）

- `isBackfillMode: Boolean`：`selectedDate` 不是今日時為 `true`；記帳頁顯示紅色補登橫條。
- `errorMessage: String?`：結帳失敗時的錯誤訊息，顯示於 `CheckoutDialog` 底部並透過 Snackbar 同步提示。
- `pdfPrinterEnabled: Boolean`（v1.2.15）：同步自 DataStore，確認收款時判斷是否自動存收據 PDF。
- `pdfPrinterTreeUri: String`（v1.2.15）：SAF tree URI；空白時存至系統下載目錄。

### ReportUiState 關鍵欄位（v1.2.7 新增）

- `openOrders: List<OrderEntity>`：持續監聽 `getAllOpenOrders()`，報表頁頂部提示卡使用。

### 備份 / 匯出

`BackupManager`（util）使用 Android SAF（`ActivityResultContracts.CreateDocument` / `OpenDocument`）。整個 SQLite 資料庫 WAL checkpoint 後打包為 `.zip` 匯出；匯入時覆蓋整個資料庫並自動重啟。匯出/匯入 UI 位於 `SettingsScreen`。

**v1.2.7 新增 `autoBackupBeforeImport(context, db)`**：匯入前於私有目錄 `files/auto_backup/` 建立 `auto-pre-import-yyyyMMdd-HHmmss.zip`（保留最新 5 份，FIFO 輪替）。`SettingsViewModel.restoreDb` 匯入前自動呼叫此函式。

**v1.2.12 新增雲端備份**：`AutoBackupManager` 支援「第2備份資料夾（雲端硬碟）」，備份策略為 Local Cache First——本機備份完成後 best-effort 複製到使用者透過 SAF 選取的雲端硬碟資料夾（`copyToCloudBestEffort`）。設定 UI 位於 `SettingsScreen` 自動儲存區塊，含 Switch + SAF 資料夾選擇器。

## 報表匯出 / 列印

`ReportViewModel.exportCsv(context, uri)` 依當前 UI 篩選後的資料組裝多區段 CSV：**檔頭 → 總覽 → 品項銷售排行 → 群組銷售排行 → 訂單明細**。寫入 UTF-8 + BOM 給 Excel 中文直開。

`ReportViewModel.printCurrentReport(context)` 會以目前 `ReportUiState` 建立列印快照，呼叫 `UsbPrinterManager.printReport(...)` 透過 USB 熱感印表機列印相同篩選範圍的報表。`ReportUiState.isPrintingReport` 用於避免重複送印；`UsbPrinterManager` 會將長報表分段渲染為 Bitmap，降低單張 Bitmap 過高的風險。

按鈕位於 `ReportScreen` 篩選區：`報表列印` 在 `匯出報表` 左側；自訂日期模式會分成日期套用列與報表動作列，避免小螢幕擁擠。若目前報表超過 10 筆且日期範圍超過 1 天，按下 `報表列印` 會先跳出確認，讓使用者選擇 `列印明細`、`只印總覽` 或取消，以避免誤印大量紙張。

## PDF 存檔（ReportPdfBuilder）

`util/ReportPdfBuilder.kt` 使用 Android 內建 `PdfDocument` API，不依賴第三方函式庫，輸出 A4 尺寸 PDF。

- **報表 PDF**：`build / buildToDownloads / buildToTreeUri`（`ReportUiState` 驅動，含總覽、排行、明細）。
- **收據 PDF**（v1.2.15）：`buildReceiptToDownloads / buildReceiptToTreeUri`，接受 `ReceiptData`（orderId、tableName、createdAt、remark、items、total）。檔名格式：`receipt-yyyyMMdd-HHmmss-{orderId}-{tableName}.pdf`；桌號中的非法字元由 `sanitizeFilename()` 替換為底線，中文保留。
- **測試 PDF**（v1.2.15）：`buildTestPdfToDownloads / buildTestPdfToTreeUri`，產生示範用 `test-yyyyMMdd-HHmmss.pdf`。
- **觸發時機**：報表列印時（`PDF_PRINTER_ENABLED=true`）自動儲存報表 PDF；確認收款時（`pdfPrinterEnabled=true`）自動儲存收據 PDF；設定頁「測試PDF檔存檔」按鈕可手動驗證目錄寫入。

## 排行圓餅圖

`ReportScreen.PieChart`：純 `Canvas` + `drawArc` 實作。`品項銷售排行` / `群組銷售排行` 卡片依 `LocalConfiguration.orientation` 自適應：**橫式**左清單右圓餅；**直式**清單在上、圓餅在下置中。切片顏色取自 `PosColors.chartBars`，清單排名色點 / 橫條也共用同索引顏色。

## 規劃文件

完整功能規格：`PLANS/plan-hotPotPosApp.prompt.md`

---

## ⚠️ 重要事項

**每次異動程式架構或導航流程時，必須同步更新以下三份文件：**

- **`README.md`**：功能總覽、畫面截圖說明（使用者導向）
- **`DEVELOPER.md`**：技術規格、建置指令、資料庫結構、版本歷程（開發者導向）
- **`CLAUDE.md`**：分層結構、導航流程、資料庫結構、重要常數、備份說明（AI 指引）

需要更新的常見異動情境：

- 新增或移除底部分頁（BottomTab）
- 新增或移除 Screen / Route
- 新增或修改 Room Entity / DAO
- 新增或修改 DataStore 設定鍵
- 新增或移除 Repository / ViewModel
- 變更備份 / 還原機制
- 版號遞增（同步更新 `gradle.properties` 的 `APP_VERSION_CODE` 與 `APP_VERSION_NAME`）

