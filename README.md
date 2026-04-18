# 🍲 火鍋店 POS 記帳系統

Android 平板專用的火鍋餐廳點餐記帳 App，支援多桌管理、品項設定、結帳流程及營業報表。

> 文件版號：`1.1.0`（對應 App v1.1.0 / versionCode 2）
>
> 最後更新：`2026-04-18`

---

## 功能總覽

| 功能 | 說明 |
|------|------|
| 🔐 PIN 登入 | 4 位數字密碼，SHA-256 加密儲存，連續錯誤 3 次鎖定 30 秒 |
| 🛒 記帳點餐 | 橫向桌號列切換，分類 Tab 篩選品項，Grid 按鈕點選累加，確認結帳收款 |
| 🥩 品項管理 | 新增 / 編輯 / 刪除品項，設定價格、分類、啟停用 |
| 📂 群組管理 | 新增 / 修改 / 刪除菜單群組，支援手動排序；刪除時顯示群組內品項數量警告 |
| 🪑 桌號管理 | 自訂桌號名稱（最多 20 字）、座位數、備註，可啟停用，支援手動排序 |
| 📊 報表 | 今日 / 本週 / 本月 / 全部統計，品項銷售排行，**群組銷售排行**，逐筆訂單明細，支援軟刪除篩選 |
| 🗑️ 訂單軟刪除 | 報表中可將訂單標記「已刪除」，勾選「已刪除」才列入統計計算 |
| 💾 資料備份 | 整個 SQLite 資料庫打包 ZIP 匯出 / 匯入還原（設定頁） |
| ⚙️ 設定 | 修改 PIN 碼、資料備份匯出 / 匯入 |

### 品項分類（預設群組）

鍋底 · 肉類 · 海鮮 · 蔬菜 · 飲料 · 其他

> 群組可於**菜單管理 → 群組**頁面自由新增、修改、排序、刪除。

---

## 技術規格

- **語言：** Kotlin
- **UI：** Jetpack Compose + Material 3
- **資料庫：** Room (SQLite)，目前版本 **v3**
- **依賴注入：** Hilt
- **設定儲存：** Jetpack DataStore
- **導航：** Navigation Compose（底部 TabBar）
- **最低 Android 版本：** Android 10（API 29）
- **目標 SDK：** 35
- **螢幕方向：** 支援自動旋轉（fullSensor）

---

## 快速開始

### 環境需求

- Android Studio Ladybug（或更新版本）
- JDK 11+
- 已連接 Android 裝置或模擬器（Android 10+）

### 建置與執行

```bash
# 建置 Debug APK
./gradlew assembleDebug

# 安裝至已連接裝置
./gradlew installDebug

# 建置 Release APK
./gradlew assembleRelease
```

> Windows 請使用 `gradlew.bat` 取代 `./gradlew`

### 預設 PIN 碼

首次啟動登入密碼為 **`1234`**，請於設定頁修改。

### 版本更新方式

版本參數統一維護在 `gradle.properties`：

- `APP_VERSION_CODE`：整數，每次發版都要遞增。
- `APP_VERSION_NAME`：顯示版號，建議使用 `major.minor.patch`（例如 `1.0.1`）。

`app/build.gradle.kts` 會讀取上述參數並寫入 `defaultConfig.versionCode` 與 `defaultConfig.versionName`。

更新流程：

1. 修改 `gradle.properties` 的 `APP_VERSION_CODE` 與 `APP_VERSION_NAME`。
2. 重新建置（Windows：`gradlew.bat assembleDebug` 或 `gradlew.bat assembleRelease`）。
3. 登入頁與主程式畫面的版號顯示會自動跟著更新。

---

## 畫面說明

```
LoginScreen（PIN 登入）
    └── HomeScreen（底部導航）
            ├── 🛒 記帳（OrderScreen）
            │       桌號橫列 → 分類 Tab（群組）→ 品項 Grid → 訂單摘要 → 結帳確認收款
            ├── 🥩 品項設定（MenuManagementScreen）
            │       群組管理（新增/修改/排序/刪除）→ 分類篩選 → 品項列表（啟停用 / 編輯 / 刪除）→ 新增品項
            ├── 🪑 桌號設定（TableSettingScreen）
            │       桌號列表（啟停用 / 編輯 / 刪除 / 上下排序）→ 新增桌號
            └── 📊 報表（ReportScreen）
                        日期範圍篩選 / 已刪除勾選 → 統計卡片 → 品項排行 → 群組排行 → 訂單明細（單筆刪除）
```

設定頁（⚙️）可從記帳頁與報表頁的齒輪圖示進入，提供 PIN 修改與資料備份。

---

## 預設資料

App 首次安裝時自動建立：

- **菜單群組：** 鍋底、肉類、海鮮、蔬菜、飲料、其他（共 6 組，可自由管理）
- **菜單品項：** 鴛鴦鍋、麻辣鍋、梅花豬肉片、鮮蝦、高麗菜、台灣啤酒等共 17 項
- **桌號：** 1 號桌 ～ 8 號桌（可於桌號設定頁自由新增、修改、排序、刪除）

---

## 備份說明

備份功能位於**設定頁（⚙️）**，使用 Android SAF（Storage Access Framework）由使用者選擇路徑。

| 動作 | 格式 | 說明 |
|------|------|------|
| 備份匯出 | `.zip` | 整個 SQLite 資料庫（含所有訂單、菜單、桌號），WAL checkpoint 後打包 |
| 備份匯入 | `.zip` | 還原資料庫，還原完成後 App 自動關閉，重新開啟後生效 |

> ⚠️ 備份匯入會**完整覆蓋**現有資料庫，操作前請先確認備份匯出。

---

## 資料庫結構

| 表格 | 主要欄位 |
|------|---------|
| `menu_items` | id, name, price, category, isAvailable, sortOrder |
| `menu_groups` | code, name, sortOrder, isActive |
| `orders` | id, tableId (FK), tableName (快照), remark, createdAt, closedAt, status, **isDeleted** |
| `order_items` | id, orderId, menuItemId, name/price（快照）, **menuGroupCode/menuGroupName（快照）**, quantity |
| `tables` | id, tableName, seats, remark, isActive, sortOrder |

- `OrderEntity.status`：`OPEN` / `PAID` / `CANCELLED`
- `OrderEntity.isDeleted`：軟刪除旗標，`true` 時預設不計入報表統計

### 資料庫版本歷程

| 版本 | 變更 |
|------|------|
| v1 | 初始建立（menu_items, orders, order_items, tables） |
| v2 | `orders` 新增 `isDeleted INTEGER NOT NULL DEFAULT 0` |
| v3 | 新增 `menu_groups`；`order_items` 新增 `menuGroupCode`、`menuGroupName` 快照欄位 |

---

## 專案文件

- **功能規格：** [`PLANS/plan-hotPotPosApp.prompt.md`](PLANS/plan-hotPotPosApp.prompt.md)
- **開發指引：** [`CLAUDE.md`](CLAUDE.md)
