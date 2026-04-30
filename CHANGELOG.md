# 更新紀錄（Changelog）

本檔案記錄 **火鍋店 POS 記帳系統** 的各版本變更歷程。
版本格式遵循 [Semantic Versioning](https://semver.org/lang/zh-TW/)：`MAJOR.MINOR.PATCH`。

> 最新版本完整功能說明請見 [`README.md`](README.md)。

---

## [Unreleased]

_尚無未發佈變更。_

---

## [v1.2.9] - 2026-04-30

**versionCode:** 17 · **下載：** [GitHub Release](https://github.com/bjoe0201/POS_ANDROID_2026/releases/tag/v1.2.9)

### 📖 使用說明全面改版

- **內容更新至 v1.2.8**：補充長按連續加減、補登模式、訂位滑動切月 / 今天按鈕 / 時段格線拖曳、圓餅圖、CSV 匯出、USB 報表列印、今年篩選、觸覺回饋設定等所有新功能說明。
- **全新視覺設計**：Hero 漸層標題區、目錄 TOC、圓形數字章節標、步驟流程 steps、FAQ 卡片、四色提示區塊（tip / warn / note / info）、版本 tag、斑馬條紋表格。
- **目錄錨點跳轉**：TOC 各項目改為可點選的錨點連結，點擊後平滑滾動至對應章節。
- **返回頂端按鈕**：右下角新增固定紅色圓形「↑」按鈕，點擊後平滑回到頂端（純 CSS，無需 JavaScript）。

### 📄 文件重構

- **`README.md`** 改寫為一般使用者導向，嵌入 20 張實機截圖並以白話說明各功能操作流程，移除所有技術內容。
- **新增 `DEVELOPER.md`**：集中所有技術開發文件（建置指令、架構分層、資料庫結構 / 版本歷程、DataStore keys、備份機制、Release 簽章等）。
- **`CLAUDE.md`** 最前面加上三條重要提醒，指向 `README.md` 與 `DEVELOPER.md`，並擴充文件同步清單。

---

## [v1.2.8] - 2026-04-30

**versionCode:** 16

### 📅 訂位月曆操作優化

- **左右滑動切月**：在月曆區域向右滑動切換上個月、向左滑動切換下個月（位移超過 200px 才觸發，避免誤觸）。
- **「今天」快速按鈕**：月曆頂部功能列「<」左側新增「今天」按鈕。
  - 若目前已在當月 → 亮黃色顯示，點擊直接開啟今天的訂位日視圖。
  - 若目前在其他月份 → 灰色顯示，點擊跳回當月月曆。

---

## [v1.2.7] - 2026-04-28

**versionCode:** 15

### 🔒 結帳可靠性改善（防止記錄消失）

針對客戶回報「當天記錄不見了」的根本原因進行多項防護。

#### Step 1 — 結帳對話框防誤關
- `CheckoutDialog` 加入 `DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)`。
- 使用者只能透過「✓ 確認收款」或「返回修改」兩個按鈕離開，無法點外部或按返回鍵意外關閉，徹底避免訂單卡在 OPEN 狀態。

#### Step 2 — 補登過去日期顯著警示
- `OrderUiState` 新增 `isBackfillMode`；日期切換到非今日時即時設為 `true`。
- 桌號列上方新增常駐紅色橫條「⚠️ 補登模式：MM/dd　今日報表不計入」並附「回到今天」按鈕，比 TopBar 小紅字更明顯。
- 第一次以非今日日期新增品項時，`OrderViewModel` 透過 `Channel` 通知 UI 彈出確認對話框「您正在補登 MM/dd 的訂單，確認繼續？」；取消則不加品項；切換日期後需重新確認。
- `resetToToday()` 供「回到今天」按鈕及 ViewModel 內 3 分鐘計時器共用，確保 `isBackfillMode` 同步重置。

#### Step 3 — `payOrder` 失敗回饋
- `payOrder` 改為 `runCatching`；`order?.id == null` 或 DAO 例外時設 `errorMessage` 並**不呼叫** `onDone()`，對話框保持開啟。
- `CheckoutDialog` 底部顯示紅色錯誤框「⚠️ …」；同時透過 `Snackbar` 輔助顯示。
- 新增 `clearErrorMessage()` 供關閉對話框時清除殘留訊息。

#### Step 4 — 降低「取消訂單」誤觸
- `OrderPanel` 的「取消訂單」由紅色 `TextButton` 改為灰色邊框 `OutlinedButton`，視覺優先級降低。
- 新增 `CancelOrderDialog`：確認對話框加入紅框警示「⚠️ 此操作無法復原，訂單將從報表中消失」；確認按鈕文字改為「確定取消訂單」並加 **0.5 秒 delay** 防止快速誤點。

#### Step 5 — OPEN 訂單提示
- `ReportUiState` 新增 `openOrders: List<OrderEntity>`，持續監聽 `getAllOpenOrders()`。
- 報表頁頂部新增橙色提示卡「🔔 今日仍有 N 桌訂單尚未結帳」，列出所有桌名，有未結帳訂單才顯示。
- 記帳頁 TopBar 加紅色徽章「🔔 N 桌未結帳」，由 `openOrderTotals` 計算，即時更新。

#### Step 6 — 匯入備份前自動安全備份
- `BackupManager` 新增 `autoBackupBeforeImport(context, db)`：匯入前在私有目錄 `files/auto_backup/` 建立 `auto-pre-import-yyyyMMdd-HHmmss.zip`，保留最新 5 份（FIFO 輪替）。
- `SettingsViewModel.restoreDb` 改為先呼叫 `autoBackupBeforeImport`，匯入失敗時 Snackbar 訊息加上「安全備份已保留於裝置私有目錄」說明。
- `SettingsScreen` 匯入確認對話框改為 `dismissOnClickOutside = false`，標題改為「⚠️ 確認匯入備份」（紅色），新增紅色警示框「匯入備份將完整覆蓋目前所有資料！」並說明自動安全備份機制；確認按鈕文字改為「確定匯入」。

---

## [v1.2.6] - 2026-04-28

**versionCode:** 14

### 🖨️ 報表列印
- 報表頁新增「報表列印」按鈕，位於「匯出報表」左側，可直接列印目前篩選後的總覽、品項排行、群組排行與訂單明細。
- 當報表超過 10 筆且日期範圍超過 1 天時，按下「報表列印」會先詢問是否列印「訂單明細」，可選擇只印總覽 / 排行以避免誤印大量紙張。
- `ReportViewModel` 新增 `isPrintingReport` 與 `printCurrentReport(context)`，避免重複送印並統一 Snackbar 成功 / 失敗訊息。
- `UsbPrinterManager` 新增 `printReport(...)` 與長報表分段 Bitmap 列印，降低單張 Bitmap 過高造成的記憶體或傳輸風險。

### 📝 文件與規劃
- 新增 `PLANS/project-review-and-roadmap.md`：彙整專案現況、架構觀察、以往開發脈絡、主要風險、P0/P1/P2/P3 功能建議與後續 Roadmap。
- 明確列出後續建議：備份健康檢查、Release Checklist、付款方式 / 日結、折扣 / 招待 / 服務費、桌況與訂位整合等。
- 新增 USB 熱感印表機報表列印規劃文件與操作說明。

---

## [v1.2.5] - 2026-04-27

**versionCode:** 13

### ⚡ 點餐長按連續加減
- 記帳頁菜單卡片的 `+` / `−` 改為**長按連續觸發**：按住超過設定的「長按啟動延遲」（預設 1 秒）後，依「連續計數速度」（預設 100ms）自動連續加減；放開立刻停止。
- 兩顆按鈕以亮色色調區分：**`+` 亮黃**（`#FFC400`）、**`−` 亮綠**（`#00C853`）。
- 按住期間於卡片正上方浮現大字 **數量氣泡**（與按鈕色一致），即時顯示當下數量。
- **單擊也會顯示氣泡**：放開後保留 600ms 才隱藏，連續短點會重置計時。
- 點卡片本體加菜也會顯示氣泡並提供觸覺回饋。

### 📳 觸覺回饋策略 + 開關
- 進入連續模式時 `LongPress` 強震一次，連續期間每 5 次以 `TextHandleMove` 輕震動點綴；單擊時亦會輕震一次。
- **新增「觸覺回饋（震動）」開關**（設定 → 點餐操作）：可整體關閉所有點餐相關震動，預設開啟，DataStore key `haptic_enabled`。

### 🛠 設定新增「點餐操作」區塊
- **觸覺回饋** Switch（最上方）。
- **連續計數速度** Slider：30 – 500ms（步進 10），即時顯示「每秒約 N 次」。
- **長按啟動延遲** Slider：300 – 2000ms（步進 100），顯示秒數。
- 設定值透過 `SettingsDataStore` 持久化（`qty_repeat_interval_ms` / `qty_repeat_initial_delay_ms` / `haptic_enabled`）。

---

## [v1.2.4] - 2026-04-25

**versionCode:** 12

### 📤 報表匯出 CSV
- 報表頁「套用」右側新增「📥 匯出報表」按鈕（非自訂模式同樣可見）。
- CSV 依畫面版面多區段輸出：檔頭、**總覽**（總營業額 / 總筆數 / 平均客單）、**品項銷售排行**、**群組銷售排行**、**訂單明細**（含訂單 ID、桌號、建立時間、狀態、已刪除、品項、群組、數量、單價、小計）。
- 寫入 UTF-8 BOM，Excel / Google Sheets / Numbers 皆可直接開啟中文不亂碼。
- 預設檔名依日期模式自動產生（如 `report_today_20260425.csv`、`report_20260423_20260425.csv`）。

### 🥧 排行圓餅圖
- 「品項銷售排行」與「群組銷售排行」卡片加入圓餅圖，保留原排行清單。
- **橫式**：左清單（`weight 1f`）＋ 右圓餅（200dp）。
- **直式**：清單在上、圓餅在下置中。
- 清單排名號碼前加入色點，與圓餅切片顏色一致；進度橫條也改用對應切片色，視覺上一眼可對應。
- 實作為純 `Canvas` + `drawArc`，無新增依賴。

### 🧹 清理
- `ReportViewModel` 不再使用 `BackupManager.exportCsv`（原 dead code 未上線），改為自行組裝多區段 CSV 並在 IO 執行緒寫入。

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
| v1.2.9 | 17 | 2026-04-30 | 使用說明改版 + 錨點跳轉 + 文件重構 |
| v1.2.8 | 16 | 2026-04-30 | 訂位月曆左右滑動切月 + 今天按鈕 |
| v1.2.7 | 15 | 2026-04-28 | 結帳可靠性改善（防止記錄消失）|
| v1.2.6 | 14 | 2026-04-28 | USB 報表列印 + 大量明細列印確認 |
| v1.2.5 | 13 | 2026-04-27 | 點餐長按連續加減 + 觸覺回饋設定 |
| v1.2.4 | 12 | 2026-04-25 | 報表 CSV 匯出 + 排行圓餅圖 |
| v1.2.3 | 11 | 2026-04-24 | 自動備份 UI 即時刷新修正 |
| v1.2.2 | 10 | 2026-04-24 | 當機保護 + 自動儲存 + 收款音效 |
| v1.2.1 |  9 | 2026-04-24 | 自動儲存改放系統下載目錄 |
| v1.2.0 |  8 | 2026-04-24 | 新增自動儲存（閒置備份） |
| v1.1.5 |  7 | 2026-04-24 | 崩潰保護先期修補 |
| v1.1.4 |  6 | 2026-04-22 | 訂位管理 |
| v1.0.0 |  1 | — | 初版公開（MIT License） |

