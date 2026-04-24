# 🍲 火鍋店 POS 記帳系統

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Android 平板專用的火鍋餐廳點餐記帳 App，支援多桌管理、品項設定、結帳流程及營業報表。

> 文件版號：`1.1.4`（對應 App v1.1.4 / versionCode 6）
>
> 最後更新：`2026-04-22`

---

## 功能總覽

| 功能 | 說明 |
|------|------|
| 🔐 PIN 登入 | 4 位數字密碼，SHA-256 加密儲存，連續錯誤 3 次鎖定 30 秒 |
| 🛒 記帳點餐 | 橫向桌號列切換，分類 Tab 篩選品項，Grid 按鈕點選累加，確認結帳收款；**支援補登歷史日期**（3 分鐘無操作自動恢復今天） |
| 📅 訂位管理 | 月曆月覽，每天依時段統計桌數（雙色塊 chip），可上下滾動；點擊日期進入當日訂位列表，支援新增 / 編輯 / 刪除；月曆每行 chip 數可於設定頁調整 |
| 🥩 品項管理 | 新增 / 編輯 / 刪除品項，設定價格、分類、啟停用 |
| 📂 群組管理 | 新增 / 修改 / 刪除菜單群組，支援手動排序；刪除時顯示群組內品項數量警告 |
| 🪑 桌號管理 | 自訂桌號名稱（最多 20 字）、座位數、備註，可啟停用，支援手動排序 |
| 📊 報表 | 今日 / **昨天** / 本週 / 本月 / 全部統計，品項銷售排行，**群組銷售排行**，逐筆訂單明細，支援軟刪除篩選 |
| 🗑️ 訂單軟刪除 | 報表中可將訂單標記「已刪除」，勾選「已刪除」才列入統計計算 |
| 💾 資料備份 | 整個 SQLite 資料庫打包 ZIP 匯出 / 匯入還原（設定頁） |
| ⚙️ 設定 | 底部獨立分頁；修改 PIN 碼、功能頁面開關、**訂位設定**（營業時間 / 休息時間 / 用餐時長 / 月曆每行時段數）、資料備份匯出 / 匯入、**資料庫初始化**（兩步驟確認） |

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

### Release 簽章設定（開發者）

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
    └── HomeScreen（底部導航，共 6 個分頁）
            ├── 🛒 記帳（OrderScreen）
            │       桌號橫列 → 分類 Tab（群組）→ 品項 Grid → 訂單摘要 → 結帳確認收款
            ├── 📅 訂位（ReservationCalendarScreen）
            │       月曆月覽（時段桌數 chip）→ 點擊日期 → 當日訂位列表 → 新增 / 編輯 / 刪除訂位
            ├── 🥩 品項設定（MenuManagementScreen）
            │       群組管理（新增/修改/排序/刪除）→ 分類篩選 → 品項列表（啟停用 / 編輯 / 刪除）→ 新增品項
            ├── 🪑 桌號設定（TableSettingScreen）
            │       桌號列表（啟停用 / 編輯 / 刪除 / 上下排序）→ 新增桌號
            ├── 📊 報表（ReportScreen）
            │       日期範圍篩選 / 已刪除勾選 → 統計卡片 → 品項排行 → 群組排行 → 訂單明細（單筆刪除）
            └── ⚙️ 設定（SettingsScreen）
                        PIN 修改 / 功能頁面開關 / 訂位設定 / 資料備份 / 資料庫初始化
```

> 訂位與報表以外的分頁可於設定頁個別開關；「記帳」與「設定」為必要分頁，永遠顯示。

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

---

## 授權條款（License）

本專案採用 **MIT License** 授權釋出，詳細條款請見 [`LICENSE`](LICENSE) 檔案。

```
MIT License

Copyright (c) 2026 POS_ANDROID_2026 Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

