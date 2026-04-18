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

## 架構

**套件：** `com.pos.app` | **Min SDK：** 29（Android 10）| **Target SDK：** 35

### 分層結構

```
data/
  datastore/SettingsDataStore.kt   — 以 Jetpack DataStore 儲存 PIN 雜湊（SHA-256）
  db/
    entity/                        — Room entities（4 張資料表）
    dao/                           — Room DAOs
    AppDatabase.kt                 — 單例；首次建立時預植入預設菜單與 8 張桌號
  repository/                      — 單一資料真實來源；透過 Hilt 注入至 ViewModels
ui/
  navigation/NavGraph.kt           — 根導航：Login → Home；Home 包含底部分頁的巢狀導航
  login / order / menu / table / report / settings / theme
util/BackupManager.kt              — 透過 SAF（Storage Access Framework）進行 JSON + CSV 匯出/匯入
```

### 導航流程

`LoginScreen` →（PIN 驗證通過）→ `HomeWithBottomNav`（4 個分頁）→ Settings（疊加於上層）

底部分頁：**記帳**（`OrderScreen`）· **品項設定**（`MenuManagementScreen`）· **桌號設定**（`TableSettingScreen`）· **報表**（`ReportScreen`）

Settings 僅可由記帳與報表分頁的圖示進入。

### 資料庫結構

| Table | Key fields |
|-------|-----------|
| `menu_items` | id, name, price, category, isAvailable, sortOrder |
| `orders` | id, **tableId** (FK→tables), **tableName** (snapshot), remark, createdAt, closedAt, status |
| `order_items` | id, orderId, menuItemId, name/price (snapshot), quantity |
| `tables` | id, tableName (≤20 chars), seats, remark, isActive, sortOrder |

`OrderEntity.tableName` 是快照欄位——即使桌號後續被重新命名或刪除，仍可維持可讀性。

### DI（Hilt）

所有 DAOs、Repositories 與 `SettingsDataStore` 皆在 `AppModule` 以 `@Singleton` 提供。ViewModels 使用 `@HiltViewModel`。應用程式進入點為 `POSApplication`（`@HiltAndroidApp`）與 `MainActivity`（`@AndroidEntryPoint`）。

### 重要常數

- `CATEGORIES` 清單（順序 + 顯示名稱）位於 `ui/order/OrderViewModel.kt`，並由 menu 與其他畫面匯入使用。
- 預設 PIN：`1234`（SHA-256 雜湊）。連續輸入錯誤 3 次會鎖定 30 秒。
- 預設桌號：8 張（預植入為「1號桌」至「8號桌」）；可於 TableSettingScreen 進行 CRUD 調整。

### 備份 / 匯出

`BackupManager`（util）使用 Android SAF（`ActivityResultContracts.CreateDocument` / `OpenDocument`）。JSON 匯出包含 menu items + 全部 orders + order items。CSV 匯出僅包含 orders。匯入會覆蓋全部 menu items（不覆蓋 orders）。匯出/匯入 UI 位於 `ReportScreen`；PIN 變更位於 `SettingsScreen`。

## 規劃文件

完整功能規格：`PLANS/plan-hotPotPosApp.prompt.md`
