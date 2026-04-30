# 開發者文件 — 火鍋店 POS 系統

> **重要提醒：** 此文件為技術開發參考，使用者操作說明請見 [`README.md`](README.md)。
> 每次異動程式架構或導航流程時，必須同步更新本文件與 [`CLAUDE.md`](CLAUDE.md)。

---

## 技術規格

- **語言：** Kotlin
- **UI：** Jetpack Compose + Material 3
- **資料庫：** Room (SQLite)，目前版本 **v4**（TRUNCATE journal + synchronous=FULL，crash-safe）
- **依賴注入：** Hilt
- **設定儲存：** Jetpack DataStore
- **導航：** Navigation Compose（底部 TabBar）
- **最低 Android 版本：** Android 10（API 29）
- **目標 SDK：** 35
- **套件：** `com.pos.app`
- **螢幕方向：** 支援自動旋轉（fullSensor）

---

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

> Windows 上請使用 `gradlew.bat` 取代 `./gradlew`。

### 環境需求

- Android Studio Ladybug（或更新版本）
- JDK 11+
- 已連接 Android 裝置或模擬器（Android 10+）

---

## 版本更新方式

版本參數統一維護在 `gradle.properties`：

- `APP_VERSION_CODE`：整數，每次發版都要遞增。
- `APP_VERSION_NAME`：顯示版號，建議使用 `major.minor.patch`（例如 `1.0.1`）。

`app/build.gradle.kts` 會讀取上述參數並寫入 `defaultConfig.versionCode` 與 `defaultConfig.versionName`。

更新流程：

1. 修改 `gradle.properties` 的 `APP_VERSION_CODE` 與 `APP_VERSION_NAME`。
2. 重新建置（Windows：`gradlew.bat assembleDebug` 或 `gradlew.bat assembleRelease`）。
3. 登入頁與主程式畫面的版號顯示會自動跟著更新。
4. 同步更新 `README.md` 與 `CLAUDE.md` 的文件版號。

---

## Release 簽章設定

正式版 APK（`assembleRelease`）需要提供 keystore 才能產生已簽章的安裝檔。若專案根目錄**未放置** `keystore.properties`，release build 會略過簽章並產生 `-unsigned.apk`（無法安裝）。

1. 產生 release keystore（僅需一次）：

   ```powershell
   keytool -genkeypair -v -keystore pos-release.jks -alias pos-release `
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. 在專案根目錄建立 `keystore.properties`（已於 `.gitignore` 排除，**勿提交**）：

   ```properties
   storeFile=pos-release.jks
   storePassword=你的store密碼
   keyAlias=pos-release
   keyPassword=你的key密碼
   ```

3. 建置正式版：

   ```bash
   ./gradlew assembleRelease
   # 輸出：app/build/outputs/apk/release/app-release.apk
   ```

> ⚠️ 請妥善保管 `pos-release.jks` 與密碼，**遺失後無法更新已發佈的 App**。

---

## 分層結構

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
```

---

## 導航流程

`LoginScreen` →（PIN 驗證通過）→ `HomeWithBottomNav`（**6 個**底部分頁）

底部分頁（依序）：**記帳**（`OrderScreen`）· **訂位**（`ReservationScreen`）· **菜單管理**（`MenuManagementScreen`）· **桌號設定**（`TableSettingScreen`）· **報表**（`ReportScreen`）· **設定**（`SettingsScreen`）

- **記帳** 與 **設定** 為必要分頁，永遠顯示。
- 其餘分頁可於「設定 → 功能頁面」個別開關；停用分頁若正在顯示，系統自動跳回記帳頁。

---

## 資料庫結構

| Table | Key fields |
|-------|-----------|
| `menu_groups` | code, name, sortOrder, isActive |
| `menu_items` | id, name, price, category, isAvailable, sortOrder |
| `orders` | id, **tableId** (FK→tables), **tableName** (snapshot), remark, createdAt, closedAt, status, **isDeleted** |
| `order_items` | id, orderId, menuItemId, name/price (snapshot), **menuGroupCode/menuGroupName (snapshot)**, quantity |
| `tables` | id, tableName (≤20 chars), seats, remark, isActive, sortOrder |
| `reservations` | id, tableId, tableName, guestName, guestPhone, guestCount, date, startTime, endTime, importance, remark, createdAt |

- `OrderEntity.tableName` 是快照欄位——即使桌號後續被重新命名或刪除，仍可維持可讀性。
- `OrderEntity.status`：`OPEN` / `PAID` / `CANCELLED`
- `OrderEntity.isDeleted`：軟刪除旗標，`true` 時預設不計入報表統計

### 資料庫版本歷程

| 版本 | 變更 |
|------|------|
| v1 | 初始建立（menu_items, orders, order_items, tables） |
| v2 | `orders` 新增 `isDeleted INTEGER NOT NULL DEFAULT 0` |
| v3 | 新增 `menu_groups`；`order_items` 新增 `menuGroupCode`、`menuGroupName` 快照欄位 |
| v4 | 新增 `reservations`；Journal 模式改為 TRUNCATE，synchronous=FULL |

---

## DI（Hilt）

所有 DAOs、Repositories 與 `SettingsDataStore` 皆在 `AppModule` 以 `@Singleton` 提供。ViewModels 使用 `@HiltViewModel`。應用程式進入點為 `POSApplication`（`@HiltAndroidApp`）與 `MainActivity`（`@AndroidEntryPoint`）。

---

## 重要常數

- `CATEGORIES` 清單（順序 + 顯示名稱）位於 `ui/order/OrderViewModel.kt`，並由 menu 與其他畫面匯入使用。
- 預設 PIN：`1234`（SHA-256 雜湊）。連續輸入錯誤 3 次會鎖定 30 秒。
- 預設桌號：8 張（預植入為「1號桌」至「8號桌」）；可於 TableSettingScreen 進行 CRUD 調整。
- **長按連續加減**：記帳頁 `+` / `−` 按鈕長按超過 `qty_repeat_initial_delay_ms`（預設 1000ms）後，依 `qty_repeat_interval_ms`（預設 100ms）連續觸發；按住或單擊時卡片上方以 Popup 顯示數字氣泡（+ 亮黃 / − 亮綠），單擊放開後保留 600ms 才隱藏。觸覺回饋可於設定 `haptic_enabled` 整體開關，使用 `LocalHapticFeedback`。實作位於 `OrderScreen.kt` 的 `RepeatableQtyButton` 與 `MenuCard`。

---

## DataStore keys（`SettingsDataStore`）

| Key | 說明 |
|-----|------|
| PIN | PIN 雜湊（SHA-256） |
| Tab 開關 | 各功能頁面顯示/隱藏 |
| 營業時間 | 訂位用 |
| 訂位設定 | 休息時間、用餐時長、月曆每行時段數 |
| 自動備份 | 閒置時間、保留天數、目標資料夾 |
| `QTY_REPEAT_INTERVAL_MS` | 點餐長按連續加減間隔（預設 100ms） |
| `QTY_REPEAT_INITIAL_DELAY_MS` | 長按啟動延遲（預設 1000ms） |
| `HAPTIC_ENABLED` | 觸覺回饋開關（預設開啟） |
| `PRINTER_TEST_PASSED` | 印表機測試已通過 |
| `PRINT_CHECKOUT_ENABLED` | 收款結帳自動列印 |
| `PRINT_DETAIL_ENABLED` | 報表明細列印按鈕 |

---

## OrderUiState 關鍵欄位（v1.2.7+）

- `isBackfillMode: Boolean`：`selectedDate` 不是今日時為 `true`；記帳頁顯示紅色補登橫條。
- `errorMessage: String?`：結帳失敗時的錯誤訊息，顯示於 `CheckoutDialog` 底部並透過 Snackbar 同步提示。

## ReportUiState 關鍵欄位（v1.2.7+）

- `openOrders: List<OrderEntity>`：持續監聽 `getAllOpenOrders()`，報表頁頂部提示卡使用。

---

## 備份 / 匯出技術細節

`BackupManager`（util）使用 Android SAF（`ActivityResultContracts.CreateDocument` / `OpenDocument`）。整個 SQLite 資料庫 WAL checkpoint 後打包為 `.zip` 匯出；匯入時覆蓋整個資料庫並自動重啟。匯出/匯入 UI 位於 `SettingsScreen`。

**`autoBackupBeforeImport(context, db)`**：匯入前於私有目錄 `files/auto_backup/` 建立 `auto-pre-import-yyyyMMdd-HHmmss.zip`（保留最新 5 份，FIFO 輪替）。`SettingsViewModel.restoreDb` 匯入前自動呼叫此函式。

### 崩潰保護

- Room 採用 `TRUNCATE` journal + `synchronous=FULL`：每筆交易直接 fsync 到主 DB 檔，不再依賴 WAL 合併，斷電 / 系統崩潰不會遺失已結帳的訂單。
- `OrderRepository` 在 `addOrUpdateItem` / `removeItem` / `payOrder` / `cancelOrder` 結束後主動 `PRAGMA wal_checkpoint(TRUNCATE)`（雙保險）。
- `MainActivity.onPause` 也 checkpoint，App 進入背景即刻落地。

---

## 報表匯出 / 列印技術細節

`ReportViewModel.exportCsv(context, uri)` 依當前 UI 篩選後的資料組裝多區段 CSV：**檔頭 → 總覽 → 品項銷售排行 → 群組銷售排行 → 訂單明細**。寫入 UTF-8 + BOM 給 Excel 中文直開。

`ReportViewModel.printCurrentReport(context)` 會以目前 `ReportUiState` 建立列印快照，呼叫 `UsbPrinterManager.printReport(...)` 透過 USB 熱感印表機列印相同篩選範圍的報表。`ReportUiState.isPrintingReport` 用於避免重複送印；`UsbPrinterManager` 會將長報表分段渲染為 Bitmap，降低單張 Bitmap 過高的風險。

---

## 排行圓餅圖

`ReportScreen.PieChart`：純 `Canvas` + `drawArc` 實作。`品項銷售排行` / `群組銷售排行` 卡片依 `LocalConfiguration.orientation` 自適應：**橫式**左清單右圓餅；**直式**清單在上、圓餅在下置中。切片顏色取自 `PosColors.chartBars`，清單排名色點 / 橫條也共用同索引顏色。

---

## 規劃文件

- **功能規格：** [`PLANS/plan-hotPotPosApp.prompt.md`](PLANS/plan-hotPotPosApp.prompt.md)
- **更新紀錄：** [`CHANGELOG.md`](CHANGELOG.md)

---

## ⚠️ 文件同步提醒

**每次異動程式架構或導航流程時，必須同步更新以下文件：**

- **`README.md`**：功能總覽、畫面截圖說明
- **`CLAUDE.md`**：分層結構、導航流程、資料庫結構、重要常數、備份說明
- **`DEVELOPER.md`**（本文件）：技術規格、建置指令、DB 版本歷程

需要更新的常見異動情境：

- 新增或移除底部分頁（BottomTab）
- 新增或移除 Screen / Route
- 新增或修改 Room Entity / DAO
- 新增或修改 DataStore 設定鍵
- 新增或移除 Repository / ViewModel
- 變更備份 / 還原機制
- 版號遞增（同步更新 `gradle.properties` 的 `APP_VERSION_CODE` 與 `APP_VERSION_NAME`）
