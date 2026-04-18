# 🍲 火鍋店 POS 記帳程式 — 架構與開發計畫

> **專案：** POS_ANDROID_2026  
> **技術棧：** Kotlin · Jetpack Compose · Room (SQLite) · Navigation Compose · ViewModel · Gson  
> **最後更新：** 2026-04-18

---

## 一、頁面結構與導航流程

```
啟動 App
   │
   ▼
┌─────────────────┐
│  LoginScreen    │  ← 密碼輸入頁（PIN 碼驗證，預設 1234）
└────────┬────────┘
         │ 驗證成功
         ▼
┌─────────────────┐
│  HomeScreen     │  ← 底部 TabBar 主畫面
│  (底部導航)      │
└──┬──┬──┬──┬────┘
   │  │  │  │
   │  │  │  └──► ReportScreen          報表頁
   │  │  └─────► TableSettingScreen    桌號設定頁
   │  └────────► ItemSettingScreen     品項設定頁
   └───────────► OrderScreen           記帳（點餐）頁
```

---

## 二、各頁面說明

### 1. 🔐 LoginScreen（密碼登入頁）
- 顯示 App Logo / 店名「火鍋店記帳系統」
- 數字鍵盤 PIN 碼輸入（預設密碼：`1234`）
- 驗證失敗顯示錯誤訊息，連續 3 次失敗短暫鎖定 30 秒
- 密碼儲存：Room `settings` 資料表（SHA-256 Hash）
- 密碼可於品項設定頁修改

---

### 2. 🛒 OrderScreen（記帳頁面）— 主核心

**上方：桌號選擇列**
- 水平捲動按鈕列，點選桌號（桌號名稱最多 20 字）

**中央：品項點選區**
- 依分類 Tab 篩選（鍋底 / 肉類 / 海鮮 / 蔬菜 / 飲料 / 其他）
- Grid 排列品項按鈕（名稱 + 價格），點選累加數量，長按可從清單移除
- 各品項顯示小計

**下方：結帳摘要區**
- 已點品項清單（品項 × 數量 = 小計）
- 備註輸入框（選填）
- 總金額顯示（大字）
- 【送出結帳】按鈕 → 寫入資料庫並清空當前點單

**資料記錄欄位：**

| 欄位 | 說明 |
|------|------|
| id | 自動遞增主鍵 |
| date_time | 結帳日期時間（ISO 8601） |
| table_no | 桌號名稱 |
| order_items | 點餐內容（JSON 字串） |
| total_amount | 總金額（元） |
| remark | 備註 |

---

### 3. 🥩 ItemSettingScreen（品項設定頁）
- 顯示現有品項清單（名稱、分類、價格、啟用狀態）
- 新增 / 編輯 / 刪除品項
- 品項欄位：
  - `名稱`（必填）
  - `價格`（必填，整數）
  - `分類`：鍋底 / 肉類 / 海鮮 / 蔬菜 / 飲料 / 其他
  - `排序`（整數，決定顯示順序）
  - `是否啟用`（開關）
- 修改登入 PIN 碼入口（輸入舊密碼後設定新密碼）

---

### 4. 🪑 TableSettingScreen（桌號設定頁）
- 顯示現有桌號清單
- 新增 / 編輯 / 刪除桌號
- 桌號欄位：
  - `桌號名稱`（必填，最多 20 字，如：A、A1、1號桌、孔明桌）
  - `座位數`（選填）
  - `備註`（選填）
  - `是否啟用`（開關）

---

### 5. 📊 ReportScreen（報表頁面）
- 日期範圍篩選（今日 / 本週 / 本月 / 自訂日期區間）
- 統計摘要卡片：
  - 總營業額
  - 總筆數
  - 平均客單價
- 各桌號業績排行（長條圖或清單）
- 各品項銷售排行（數量 + 金額）
- 逐筆訂單明細列表（可展開查看詳細點餐內容）
- 【匯出 JSON 備份】按鈕 → 輸出 `.json` 備份檔
- 【匯出 CSV 報表】按鈕 → 輸出 `.csv` 供 Excel 開啟
- 【匯入 JSON 備份】按鈕 → 從 `.json` 檔還原資料

---

## 三、資料庫設計（Room / SQLite）

### Table: `orders`（訂單）
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | INTEGER PK AUTOINCREMENT | 主鍵 |
| date_time | TEXT | ISO 8601 格式時間戳 |
| table_no | TEXT | 桌號名稱 |
| order_items | TEXT | JSON 陣列（品項+數量+單價） |
| total_amount | INTEGER | 總金額（元） |
| remark | TEXT | 備註 |

### Table: `menu_items`（品項）
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | INTEGER PK AUTOINCREMENT | 主鍵 |
| name | TEXT | 品項名稱 |
| price | INTEGER | 售價 |
| category | TEXT | 鍋底/肉類/海鮮/蔬菜/飲料/其他 |
| sort_order | INTEGER | 排序（小的優先） |
| is_active | INTEGER | 1=啟用，0=停用 |

### Table: `tables`（桌號）
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | INTEGER PK AUTOINCREMENT | 主鍵 |
| table_name | TEXT | 桌號名稱（最多 20 字） |
| seats | INTEGER | 座位數（可空） |
| remark | TEXT | 備註（可空） |
| is_active | INTEGER | 1=啟用，0=停用 |

### Table: `settings`（系統設定）
| 欄位 | 型別 | 說明 |
|------|------|------|
| key | TEXT PK | 設定鍵（如 `pin_hash`） |
| value | TEXT | 設定值 |

**預設設定：**
- `pin_hash` = SHA-256("1234")

---

## 四、備份匯入/匯出流程

### 匯出 JSON（完整備份）
```
使用者點擊「匯出 JSON 備份」
  └─ 讀取 orders / menu_items / tables / settings
  └─ 序列化為 JSON 物件
  └─ 使用 Storage Access Framework (SAF) 讓使用者選擇儲存路徑
  └─ 寫入 pos_backup_YYYYMMDD_HHmmss.json
```

### 匯出 CSV（報表用）
```
使用者點擊「匯出 CSV 報表」
  └─ 依選定日期範圍查詢 orders
  └─ 產生 CSV（日期,桌號,品項明細,總金額,備註）
  └─ 使用 SAF 寫入 pos_report_YYYYMMDD.csv
```

### 匯入 JSON（資料還原）
```
使用者點擊「匯入 JSON 備份」
  └─ 使用 SAF 讓使用者選取 .json 檔
  └─ 反序列化 JSON
  └─ 彈出確認對話框（覆蓋 / 合併 / 取消）
  └─ 寫入 Room 資料庫
  └─ 顯示匯入結果摘要
```

---

## 五、專案目錄結構

```
app/src/main/java/com/example/pos_android_2026/
├── MainActivity.kt
├── navigation/
│   └── AppNavigation.kt              ← Navigation Compose 路由
├── ui/
│   ├── login/
│   │   ├── LoginScreen.kt
│   │   └── LoginViewModel.kt
│   ├── order/
│   │   ├── OrderScreen.kt
│   │   └── OrderViewModel.kt
│   ├── item/
│   │   ├── ItemSettingScreen.kt
│   │   └── ItemSettingViewModel.kt
│   ├── table/
│   │   ├── TableSettingScreen.kt
│   │   └── TableSettingViewModel.kt
│   ├── report/
│   │   ├── ReportScreen.kt
│   │   └── ReportViewModel.kt
│   └── theme/                        ← 現有 Theme（保留）
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt            ← Room Database（單例）
│   │   ├── dao/
│   │   │   ├── OrderDao.kt
│   │   │   ├── MenuItemDao.kt
│   │   │   ├── TableDao.kt
│   │   │   └── SettingDao.kt
│   │   └── entity/
│   │       ├── OrderEntity.kt
│   │       ├── MenuItemEntity.kt
│   │       ├── TableEntity.kt
│   │       └── SettingEntity.kt
│   ├── repository/
│   │   ├── OrderRepository.kt
│   │   ├── MenuItemRepository.kt
│   │   ├── TableRepository.kt
│   │   └── SettingRepository.kt
│   └── backup/
│       └── BackupManager.kt          ← JSON / CSV 匯入匯出
└── util/
    ├── HashUtil.kt                   ← SHA-256 工具
    └── Extensions.kt
```

---

## 六、依賴套件（需新增）

| 套件 | 版本 | 用途 |
|------|------|------|
| `androidx.room:room-runtime` | 2.7.x | SQLite ORM |
| `androidx.room:room-ktx` | 2.7.x | Room Coroutines 支援 |
| `com.google.devtools.ksp` | 最新 | Room 程式碼生成（KSP） |
| `androidx.navigation:navigation-compose` | 2.9.x | 頁面導航 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.10.x | ViewModel in Compose |
| `com.google.code.gson:gson` | 2.11.x | JSON 序列化（備份） |

---

## 七、UI 風格規範

- **主色系：** 暖橘紅 `#E53935` / 深紅 `#B71C1C`
- **背景色：** 米白 `#FFF8E1`
- **卡片色：** 白色 `#FFFFFF`，陰影 elevation 2dp
- **字體大小：** 按鈕 ≥ 18sp，標題 22sp，總金額 32sp
- **品項按鈕：** 最小 80×80dp，圓角 12dp，適合觸控
- **底部 NavigationBar：** 4 個 Tab
  - 🛒 記帳
  - 🥩 品項設定
  - 🪑 桌號設定
  - 📊 報表
- **對話框：** 確認刪除、送出結帳、匯入覆蓋均需二次確認

---

## 八、開發順序

| 步驟 | 工作項目 |
|------|---------|
| 1 | 更新 `libs.versions.toml` + `build.gradle.kts`（Room, Navigation, Gson, KSP） |
| 2 | 建立 Entity → DAO → AppDatabase → Repository（資料層） |
| 3 | `AppNavigation.kt` + `MainActivity` 整合導航骨架 |
| 4 | `LoginScreen` — PIN 碼鍵盤 UI + SHA-256 驗證 |
| 5 | `ItemSettingScreen` — 品項 CRUD（含分類、排序、啟停用） |
| 6 | `TableSettingScreen` — 桌號 CRUD（名稱最多 20 字） |
| 7 | `OrderScreen` — 分類 Tab + 品項 Grid + 結帳摘要 |
| 8 | `ReportScreen` — 統計摘要 + 明細列表 |
| 9 | `BackupManager` — JSON 匯出/匯入 + CSV 匯出 |
| 10 | UI 美化 — 套用色系、測試觸控體驗 |

---

## 九、品項分類定義

| 分類代碼 | 顯示名稱 |
|---------|---------|
| `hotpot_base` | 🍲 鍋底 |
| `meat` | 🥩 肉類 |
| `seafood` | 🦐 海鮮 |
| `vegetable` | 🥬 蔬菜 |
| `drink` | 🧃 飲料 |
| `other` | 📦 其他 |

