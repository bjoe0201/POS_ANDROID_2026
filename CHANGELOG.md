# 更新紀錄（Changelog）

本檔案記錄 **火鍋店 POS 記帳系統** 的各版本變更歷程。
版本格式遵循 [Semantic Versioning](https://semver.org/lang/zh-TW/)：`MAJOR.MINOR.PATCH`。

> 最新版本完整功能說明請見 [`README.md`](README.md)。

---

## [v1.2.3] - 2026-04-24

**versionCode:** 11 · **下載：** [GitHub Release](https://github.com/bjoe0201/POS_ANDROID_2026/releases/tag/v1.2.3)

### 🔁 自動備份 UI 刷新修正
- 背景觸發的自動備份 / 同秒內重複備份後，「最近自動備份」時間與備份列表未即時更新。
- 修正：
  - `AutoBackupManager` 新增 `backupTick` StateFlow，每次備份成功強制 emit（不受秒級時間戳精度影響）。
  - `SettingsViewModel` 訂閱 `backupTick`，每次備份完成都重新掃描備份清單、更新最近時間與資料夾描述。
  - `SettingsScreen.AutoBackupSection` 進入時透過 `LaunchedEffect` 主動刷新，背景備份完回到設定頁即可看到最新狀態。
  - `refreshAutoBackupFiles()` 同步更新「最近自動備份」時間戳。

---

## [v1.2.2] - 2026-04-24

**versionCode:** 10 · **下載：** [GitHub Release](https://github.com/bjoe0201/POS_ANDROID_2026/releases/tag/v1.2.2)

### 🛡️ 資料保護（Crash-safe DB）
- Room 由 WAL 改為 **`TRUNCATE` journal + `synchronous=FULL`**，每筆交易立即 fsync 到主 DB 檔，避免系統崩潰 / 斷電造成當日資料遺失。
- `OrderRepository` 在加減菜 / `payOrder` / `cancelOrder` 後主動 `flush()`（`PRAGMA wal_checkpoint(TRUNCATE)`）。
- `MainActivity.onPause` 加入 WAL checkpoint，App 切入背景即刻落地。
- 修正客戶回報：「當機後當天資料整批消失」。

### 🔔 收款完成音效
- 按「✓ 確認收款」並完成 DB 寫入後，自動播放 **C5-E5-G5-C6 歡快上行琶音**。
- `AudioTrack` 即時合成，無需音檔資源；使用 `USAGE_NOTIFICATION_EVENT`，跟隨系統通知音量。

### 📖 文件
- `README.md`、設定頁「使用說明」同步更新至 v1.2.2。

---

## [v1.2.1] - 2026-04-24

**versionCode:** 9

### 💽 自動儲存（閒置備份）位置優化
- 預設備份位置由「App 私有目錄」改為 **系統「下載 / 火鍋店POS備份」**（透過 MediaStore）。
  - **App 解除安裝後檔案仍保留**，解決先前解除安裝即遺失備份的問題。
- 使用者可透過 SAF「選擇其他資料夾」指定任意位置，自動取得 persistable tree URI 權限。
- 設定頁新增資料夾顯示卡片、選擇／改回預設 按鈕。
- 新增依賴：`androidx.documentfile:documentfile:1.0.1`。

---

## [v1.2.0] - 2026-04-24

**versionCode:** 8

### 💾 自動儲存（閒置備份）— 初版
- 新增「設定 → 自動儲存」功能：使用者閒置指定時間後自動備份整個資料庫為 ZIP。
- 可調：
  - 閒置觸發時間（1 / 3 / 5 / 10 / 15 / 30 / 60 分鐘，預設 5）
  - 保留天數（1 / 3 / 5 / 7 / 14 / 30 天，預設 3）
- 檔名：`pos_auto_YYYYMMDD.zip`，每天最多 1 份，同日觸發覆寫。
- 使用者觸控 / 按鍵都會重置閒置計時器；App 切入背景會立即觸發備份。
- 設定頁提供備份列表，可直接「還原」或「刪除」；另提供「立即備份」按鈕。
- 新增 `AutoBackupManager`、`SoundEffects`（工具），`SettingsDataStore` 加入自動備份相關偏好設定。

> ⚠️ v1.2.0 的備份存放於 App 私有目錄，App 解除安裝會一併刪除。v1.2.1 已改為 Downloads 公用目錄。

---

## [v1.1.5] - 2026-04-24

**versionCode:** 7

### 🛡️ 崩潰保護（先期修補）
- 客戶回報「當機後整晚 3 筆訂單消失」。
- 初版修復：`MainActivity.onPause` 呼叫 WAL checkpoint；`OrderRepository` 在結帳後 flush。
- 後續於 v1.2.2 完整改為 TRUNCATE journal 才真正徹底解決。

---

## [v1.1.4] - 2026-04-22

**versionCode:** 6

### 📅 訂位管理
- 新增月曆月覽，以「時段 + 桌數」色塊 chip 呈現，當日可上下滾動。
- 點日期 → 當日訂位列表（新增 / 編輯 / 刪除）。
- 設定頁加入「訂位設定」：營業時間、休息時間、預設用餐時長、月曆每行時段數（1～4）。

### 🗄️ 資料庫
- 新增 `reservations` 表，由 Room 遷移至 v3（後於 v1.2.2 升到 v4）。

---

## [v1.1.x] 系列（早期功能迭代）

- 📊 **報表增強**：加入「昨天」選項、群組銷售排行、訂單明細軟刪除與篩選。
- 📂 **菜單群組管理**：新增 `menu_groups` 表（Room v3），`order_items` 加入群組名稱與代碼快照。
- 🪑 **桌號設定**：可自訂桌號、座位數、備註，支援排序與啟停用。
- 🎨 **啟動圖示更新**：新設計 + monochrome 變體。
- 🔄 **資料庫初始化**：設定頁加入兩步驟確認的重置功能（`withTransaction` 原子操作）。

---

## [v1.0.0] - 初版公開

**chore: initial public release (MIT License)**

### 🎉 核心功能
- 🔐 **PIN 登入**：4 位數字密碼，SHA-256 雜湊儲存，連續錯誤 3 次鎖定 30 秒。
- 🛒 **記帳點餐**：桌號橫列、分類 Tab、品項 Grid、結帳收款。
- 🥩 **品項管理**：新增 / 編輯 / 刪除 / 啟停用。
- 📊 **營業報表**：今日 / 本週 / 本月 / 全部，品項銷售排行。
- 💾 **資料備份**：SAF 匯出 / 匯入整個資料庫（ZIP 打包）。
- ⚙️ **設定頁**：PIN 修改、功能頁面開關、備份管理。

### 🛠️ 技術堆疊
- Kotlin · Jetpack Compose · Material 3
- Room v1（SQLite）· Hilt · DataStore · Navigation Compose
- Min SDK 29（Android 10）· Target SDK 35

---

## 版本對照表

| 版本 | versionCode | 發行日 | 主題 |
|------|:----------:|--------|------|
| v1.2.3 | 11 | 2026-04-24 | 自動備份 UI 即時刷新修正 |
| v1.2.2 | 10 | 2026-04-24 | 當機保護 + 自動儲存 + 收款音效 |
| v1.2.1 |  9 | 2026-04-24 | 自動儲存改放系統下載目錄 |
| v1.2.0 |  8 | 2026-04-24 | 新增自動儲存（閒置備份） |
| v1.1.5 |  7 | 2026-04-24 | 崩潰保護先期修補 |
| v1.1.4 |  6 | 2026-04-22 | 訂位管理 |
| v1.0.0 |  1 | — | 初版公開（MIT License） |

