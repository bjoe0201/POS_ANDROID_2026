# CLAUDE.md

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

### 備份 / 匯出

`BackupManager`（util）使用 Android SAF（`ActivityResultContracts.CreateDocument` / `OpenDocument`）。整個 SQLite 資料庫 WAL checkpoint 後打包為 `.zip` 匯出；匯入時覆蓋整個資料庫並自動重啟。匯出/匯入 UI 位於 `SettingsScreen`。

## 規劃文件

完整功能規格：`PLANS/plan-hotPotPosApp.prompt.md`

---

## ⚠️ 重要事項

**每次異動程式架構或導航流程時，必須同步更新以下兩份文件：**

- **`README.md`**：功能總覽、畫面說明、導航結構、資料庫結構
- **`CLAUDE.md`**：分層結構、導航流程、資料庫結構、重要常數、備份說明

需要更新的常見異動情境：

- 新增或移除底部分頁（BottomTab）
- 新增或移除 Screen / Route
- 新增或修改 Room Entity / DAO
- 新增或修改 DataStore 設定鍵
- 新增或移除 Repository / ViewModel
- 變更備份 / 還原機制
- 版號遞增（同步更新 `gradle.properties` 的 `APP_VERSION_CODE` 與 `APP_VERSION_NAME`）

