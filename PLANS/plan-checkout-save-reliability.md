# Plan: 改善結帳儲存可靠性

## 背景

客戶常反應「沒有看到當天的記錄」，初判為「確認收款」沒被按下儲存。
經程式流程分析，找出多個可能造成「結帳未成功儲存」或「儲存了但今日報表看不到」的情境（依風險排序）。

> 報表是用 `OrderEntity.createdAt` 篩日期、用 `status = 'PAID'` 過濾（見 `ui/report/ReportViewModel.kt` 的 `recompute`），任一條件不符今日報表就會「看不到」。

---

## 風險清單（程式分析）

| # | 等級 | 問題描述 |
|---|------|---------|
| 1 | 🔴 高 | 結帳對話框被誤關，訂單仍停在 OPEN |
| 2 | 🔴 高 | 用 `selectedDate` 補登過去日期，`createdAt` 被寫成過去，今日報表抓不到 |
| 3 | 🟡 中 | `payOrder` 在 `order?.id == null` 時靜默失敗，無錯誤訊息 |
| 4 | 🟡 中 | 「取消訂單」按鈕容易誤按，訂單 status 變 CANCELLED 從報表消失 |
| 5 | 🟡 中 | 報表頁「軟刪除」後，預設 `showDeleted=false` 看不到，使用者不知道 |
| 6 | 🟡 中 | 設定頁匯入備份直接覆蓋整個資料庫，今天資料全消失 |
| 7 | 🟢 低 | 報表日期篩選器停在非今日，誤以為今日無記錄 |
| 8 | 🟢 低 | App 進程被 kill 在 `viewModelScope.launch` 期間，極端情況未寫入 |
| 9 | 🟢 低 | 桌號被停用，OPEN 訂單孤立，無法再被結帳 |

### 詳細說明

#### 1【高】結帳對話框被誤關，訂單仍停在 OPEN
- `ui/order/OrderScreen.kt` 的 `CheckoutDialog` 使用預設 `AlertDialog`，
  `onDismissRequest = onDismiss` 直接關閉。
- 客人按下「送出結帳」後，若**點對話框外**或**按返回鍵**，對話框無聲關閉，
  訂單仍是 `status='OPEN'`，**不會**進入今日報表。

#### 2【高】補登過去日期，`createdAt` 被寫成過去
- `ui/order/OrderViewModel.kt` `addItem`：第一筆品項建立 `OrderEntity` 時，
  `createdAt = if (selectedDate == today) now else selectedDate`。
- 客人若不小心點了 TopBar 日期按鈕選了昨天/前幾天，訂單 `createdAt` 是過去日期；
  雖成功儲存且 PAID，但「今日報表」抓不到。

#### 3【中】`payOrder` 靜默失敗
- 罕見競態下 `_uiState.value.order?.id` 為 null，`payOrder` 提早 return，
  且**無任何錯誤訊息**，使用者不知道結帳未完成。

#### 4【中】「取消訂單」按鈕容易誤按
- `OrderPanel` 右上紅字「取消訂單」一鍵彈確認，confirm 後 `cancelOrder`
  把 status 改 `CANCELLED`，從報表消失。
- 客人可能以為「取消訂單」是「取消這次結帳對話框」。

#### 5【中】報表頁「軟刪除」導致資料消失
- `softDeleteOrder` 將 `isDeleted=1`；預設 `showDeleted=false` 使資料從報表消失，
  需手動開啟「顯示已刪除」才看得到。

#### 6【中】備份/還原誤操作覆蓋當日資料
- `util/BackupManager.kt` 透過 SAF 匯入舊 zip 會整個覆蓋資料庫並重啟；
  客人誤匯入舊備份，今天記錄就會被覆蓋。

---

## 改善 Steps（本次實作範圍 Step 1 ~ 6）

### Step 1：避免結帳對話框被誤關

**目標檔案：** `app/src/main/java/com/pos/app/ui/order/OrderScreen.kt`

**做法：**
- 對 `CheckoutDialog` 的 `AlertDialog` 加上：
  ```kotlin
  properties = DialogProperties(
      dismissOnClickOutside = false,
      dismissOnBackPress = false
  )
  ```
- 確保使用者只能透過「✓ 確認收款」或「返回修改」兩個按鈕離開對話框，
  無法從對話框外或返回鍵意外關閉。

**驗收：**
- 點擊對話框外部，對話框不關閉。
- 按返回鍵，對話框不關閉。
- 點「返回修改」，對話框正常關閉，訂單保留 OPEN。
- 點「✓ 確認收款」，對話框關閉，訂單 status 變 PAID。

---

### Step 2：補登過去日期的顯著警示

**目標檔案：**
- `app/src/main/java/com/pos/app/ui/order/OrderViewModel.kt`
- `app/src/main/java/com/pos/app/ui/order/OrderScreen.kt`

**做法：**
1. `OrderUiState` 新增 `isBackfillMode: Boolean`（selectedDate ≠ today）。
2. 在 `OrderScreen` 桌號選擇列**上方**（目前只有 TopBar 有小紅字）新增常駐紅色警示橫條：
   ```
   ⚠️  補登模式：MM/dd　　　　　　[回到今天]
   ```
   橫條顯示條件：`uiState.selectedDate != today`
3. 第一次以非今日 `selectedDate` 呼叫 `addItem` 時，透過
   `SharedFlow<String>` event 通知 UI 彈出確認對話框：
   「您正在補登 MM/dd 的訂單，確認繼續？」
   （取消則不新增品項）
4. `addItem` 開始執行後不再重複彈出（每次切換日期重新觸發一次即可）。

**驗收：**
- 日期選今日，橫條不顯示。
- 日期選過去，橫條顯示；第一次點品項彈出確認；確認後正常加入。
- 點「回到今天」按鈕，日期重設回今日，橫條消失。

---

### Step 3：`payOrder` 失敗時給使用者明確回饋

**目標檔案：**
- `app/src/main/java/com/pos/app/ui/order/OrderViewModel.kt`
- `app/src/main/java/com/pos/app/ui/order/OrderScreen.kt`

**做法：**
1. `OrderUiState` 新增 `errorMessage: String?`。
2. `payOrder` 改為：
   - `order?.id == null` 時，`_uiState.update { it.copy(errorMessage = "無可結帳訂單，請重新選桌後再試") }` 並 return，**不呼叫** `onDone()`，讓對話框繼續顯示。
   - DAO 例外也 catch 並設 `errorMessage`。
3. `CheckoutDialog` 在 `confirmButton` 上方顯示 `errorMessage`（紅色 Text）。
4. `OrderScreen` 同時接收 `errorMessage` 以 `Snackbar` 輔助顯示。

**驗收：**
- 正常情況結帳成功，無 error 訊息。
- 模擬 `order == null` 的情況，點「確認收款」後對話框不關閉並顯示錯誤。

---

### Step 4：降低「取消訂單」誤觸

**目標檔案：** `app/src/main/java/com/pos/app/ui/order/OrderScreen.kt`（`OrderPanel`）

**做法：**
1. 「取消訂單」按鈕樣式：縮小字體、改成 `OutlinedButton` + 灰色邊框，視覺上降低優先級。
2. 確認對話框文字強化：
   ```
   確定取消 1號桌 的全部點餐？
   ⚠️ 此操作無法復原，訂單將從報表中消失。
   ```
3. 確認按鈕文字從「確定」改為「確定取消訂單」，並加入 0.5 秒 delay 防止快速誤點。

**驗收：**
- 按鈕視覺不再搶眼。
- 確認對話框文字明確傳達後果。
- 快速連點確認鍵，不會立即觸發。

---

### Step 5：顯示尚未結帳的 OPEN 訂單提示

**目標檔案：**
- `app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt`
- `app/src/main/java/com/pos/app/ui/report/ReportScreen.kt`
- `app/src/main/java/com/pos/app/ui/order/OrderScreen.kt`（TopBar 徽章）

**做法：**
1. `ReportViewModel` 新增 `openOrders: List<OrderEntity>` 到 `ReportUiState`，
   持續監聽 `orderRepository.getAllOpenOrders()`。
2. `ReportScreen` 頂部新增提示卡（僅在 `openOrders.isNotEmpty()` 時顯示）：
   ```
   ⚠️  今日仍有 N 桌訂單尚未結帳
   1號桌 NT$1,200 ｜ 3號桌 NT$800 ｜ ...
   ```
3. 記帳頁 `OrderScreen` TopBar 右側（在日期按鈕旁）加 OPEN 桌數紅色徽章：
   ```
   [🟡 3桌未結帳]
   ```
   資料來自 `uiState.openOrderTotals`（已存在，只需計算 count）。

**驗收：**
- 有 OPEN 訂單時，報表頁顯示提示卡；全部結帳後提示卡消失。
- TopBar 徽章數字即時更新。

---

### Step 6：匯入備份前的安全機制

**目標檔案：**
- `app/src/main/java/com/pos/app/util/BackupManager.kt`
- `app/src/main/java/com/pos/app/ui/settings/SettingsScreen.kt`

**做法：**
1. `BackupManager` 新增 `autoBackupBeforeImport(context)` function：
   - 在應用私有目錄 `context.filesDir/auto_backup/` 建立
     `auto-pre-import-yyyyMMdd-HHmmss.zip`（WAL checkpoint 後打包）。
   - 目錄內只保留最新 5 份（FIFO 輪替）。
2. `importBackup` 執行流程改為：
   ```
   先執行 autoBackupBeforeImport()
     ↓ 成功
   再進行覆蓋匯入
     ↓ 失敗
   Snackbar 提示「匯入失敗，已保留自動備份於裝置私有目錄」
   ```
3. `SettingsScreen` 匯入備份按鈕彈出加強確認對話框：
   ```
   ⚠️  匯入備份將覆蓋目前所有資料！
   系統會在覆蓋前自動建立安全備份。
   確定繼續匯入？
   [確定匯入]  [取消]
   ```

**驗收：**
- 點「確定匯入」後，私有目錄有新的 `auto-pre-import-*.zip`。
- 超過 5 份時，最舊的自動刪除。
- 匯入失敗時，提示中有安全備份路徑說明。

---

## 文件同步更新（實作後必做）

實作完成後需同步更新：

- **`README.md`**：補充匯入前自動備份說明、補登警示機制、OPEN 訂單徽章說明
- **`CLAUDE.md`**：
  - DataStore keys 無新增（本次無新 key）
  - `BackupManager` 段落補充 `autoBackupBeforeImport` 說明
  - `OrderUiState` 補充 `isBackfillMode`、`errorMessage` 欄位說明
  - `ReportUiState` 補充 `openOrders` 欄位說明
- **版號遞增**：`gradle.properties` 的 `APP_VERSION_CODE` + `APP_VERSION_NAME`

---

## 優先實作順序建議

```
Step 1（誤關對話框）→ Step 3（靜默失敗回饋）
  → Step 4（取消訂單防誤觸）
    → Step 2（補登日期警示）
      → Step 5（OPEN 訂單提示）
        → Step 6（匯入前自動備份）
```

Step 1 + Step 3 最快且效益最高，建議優先上線。

