# Plan: 點餐長按連續加減 + 速度設定

## TL;DR
在 `OrderScreen` 的菜單卡片加減量按鈕新增「長按 1 秒後依設定速度自動連續增/減」的互動；按住期間在卡片上方以 Popup 浮層顯示即時數字氣泡，放開即消失。+ 用亮黃、− 用亮綠以色調區分。於「設定」頁新增兩條 Slider：「連續計數速度」與「長按啟動延遲」，並配合觸覺回饋。

---

## 影響範圍
- `data/datastore/SettingsDataStore.kt`
- `data/repository/SettingsRepository.kt`
- `ui/settings/SettingsViewModel.kt`
- `ui/settings/SettingsScreen.kt`
- `ui/order/OrderViewModel.kt`
- `ui/order/OrderScreen.kt`
- `CLAUDE.md` / `README.md` / `CHANGELOG.md` / `gradle.properties`

---

## Steps

### 1. DataStore 新增兩個 key
檔案：`SettingsDataStore.kt`
- `QTY_REPEAT_INTERVAL_MS`（intPreferencesKey，預設 `100`，範圍 30–500）
- `QTY_REPEAT_INITIAL_DELAY_MS`（intPreferencesKey，預設 `1000`，範圍 300–2000）
- 各加 `Flow<Int>` getter 與 `suspend fun setXxx(v: Int)` setter。

### 2. Repository 透傳
檔案：`SettingsRepository.kt`
- 暴露 `qtyRepeatIntervalMs: Flow<Int>`、`qtyRepeatInitialDelayMs: Flow<Int>`
- 對應 `setQtyRepeatIntervalMs(v)` / `setQtyRepeatInitialDelayMs(v)`（呼叫 DataStore）。

### 3. SettingsViewModel
檔案：`SettingsViewModel.kt`
- `SettingsUiState` 新增：
  - `qtyRepeatIntervalMs: Int = 100`
  - `qtyRepeatInitialDelayMs: Int = 1000`
- `init` 內 `launchIn` 收集兩者。
- 新增 `setQtyRepeatIntervalMs(v)` / `setQtyRepeatInitialDelayMs(v)`，內含 `coerceIn` 邊界保護。

### 4. SettingsScreen 新增「點餐操作」區塊
檔案：`SettingsScreen.kt`
- 在「功能頁面」之後加入一張 Card「點餐操作」，包含兩條 Slider：
  - 「連續計數速度」30–500ms（步進 10）。副標：`間隔 {ms}ms（每秒約 {1000/ms} 次）`。左右端註記「快 / 慢」。
  - 「長按啟動延遲」300–2000ms（步進 100）。副標：`{ms/1000.0} 秒後開始連續`。
- onValueChangeFinished 寫回 ViewModel。

### 5. OrderViewModel 注入 Settings
檔案：`OrderViewModel.kt`
- 建構子注入 `SettingsRepository`。
- 將兩值合併入 `OrderUiState`（新增 `qtyRepeatIntervalMs`、`qtyRepeatInitialDelayMs`），或暴露獨立 `StateFlow<RepeatConfig>`。
- 使用 `combine` 將原本的 state flow 與這兩個 flow 合併，`stateIn(viewModelScope, SharingStarted.Eagerly, initial)`。

### 6. OrderScreen：RepeatableQtyButton
檔案：`OrderScreen.kt`
- 新增 private Composable `RepeatableQtyButton`：
  - 參數：`label: String`（"+"/"−"）、`isPlus: Boolean`、`intervalMs: Int`、`initialDelayMs: Int`、`onTrigger: () -> Unit`、`onPressStart: () -> Unit`、`onPressEnd: () -> Unit`。
  - 顏色：
    - `+`：背景 `Color(0xFFFFD600).copy(alpha = 0.18f)`、文字 `Color(0xFFFFC400)`
    - `−`：背景 `Color(0xFF00E676).copy(alpha = 0.18f)`、文字 `Color(0xFF00C853)`
  - 互動：使用 `Modifier.pointerInput(intervalMs, initialDelayMs)` 內 `awaitEachGesture`：
    1. `awaitFirstDown()` → 立即 `onPressStart()`、`onTrigger()`（單次）。
    2. 啟動 coroutine：`delay(initialDelayMs)` 後迴圈 `onTrigger(); delay(intervalMs)`。
    3. `waitForUpOrCancellation()` 後取消 coroutine、`onPressEnd()`。
  - 觸覺：使用 `LocalHapticFeedback.current`：
    - 首次進入連續模式：`HapticFeedbackType.LongPress`
    - 每 5 次重複：`HapticFeedbackType.TextHandleMove`（避免高頻噪擾）

### 7. MenuCard 加入 Popup 氣泡
檔案：`OrderScreen.kt`
- `MenuCard` 內 state：
  - `var bubbleVisible by remember { mutableStateOf(false) }`
  - `var bubbleIsPlus by remember { mutableStateOf(true) }`
- 兩顆按鈕的 `onPressStart` 設定 `bubbleVisible=true` + sign，`onPressEnd` 設為 `false`。
- 使用 `androidx.compose.ui.window.Popup`：
  - `alignment = Alignment.TopCenter`、`offset = IntOffset(0, -160)`（約 -48dp 換算）
  - `properties = PopupProperties(focusable = false, dismissOnClickOutside = false)`
  - 內容為圓角 12dp 的 Box，背景同按鈕主色（黃/綠），文字顯示當前 `qty`（24sp Bold、白色）。
- 邊界 fallback：以 `BoxWithConstraints` 判斷卡片在 grid 上方時改 offset 顯示在卡片下方（避免被狀態列裁切）。

### 8. 簽名串接
- `MenuCard` 新增參數 `repeatIntervalMs: Int`、`repeatInitialDelayMs: Int`。
- `OrderScreen` 由 `uiState.qtyRepeatIntervalMs` / `uiState.qtyRepeatInitialDelayMs` 取得後傳入 `MenuCard`。
- 將 `RepeatableQtyButton` 取代原本的 `QtyBtn`（保留 QtyBtn 或刪除）。

### 9. 文件 / 版本同步
- `CLAUDE.md`：
  - `SettingsDataStore` 區塊補上 `QTY_REPEAT_INTERVAL_MS`、`QTY_REPEAT_INITIAL_DELAY_MS`。
  - 新增「點餐互動」說明：長按連續加減 + 氣泡 + 觸覺。
- `README.md`：點餐頁操作說明與設定項。
- `gradle.properties`：`APP_VERSION_CODE` +1、`APP_VERSION_NAME` patch +1。
- `CHANGELOG.md`：新增條目「點餐連續加減 + 設定速度與長按延遲」。

---

## 設計細節

### 顏色（亮色調）
| 按鈕 | 主色（文字/邊框） | 背景（淡） | 氣泡背景 |
|------|-------------------|------------|----------|
| `+`  | `#FFC400`         | `#FFD600` α 0.18 | `#FFC400` |
| `−`  | `#00C853`         | `#00E676` α 0.18 | `#00C853` |

### 互動參數預設
| 設定 | 預設 | 範圍 | 步進 |
|------|------|------|------|
| 連續計數速度 (ms) | 100 | 30 – 500 | 10 |
| 長按啟動延遲 (ms) | 1000 | 300 – 2000 | 100 |

### 觸覺策略
- 首次進入連續模式：`LongPress`（重）
- 連續觸發每 5 次：`TextHandleMove`（輕）
- 單擊（未進連續模式）：不額外加震動

### 氣泡顯示規則
- 觸發時機：`down` 即顯示（含單擊也會閃一下，與快速反饋一致）
- 隱藏：`up` 或 `cancel` 立刻隱藏
- 內容：當前該品項 `qty` 即時更新；隨 ViewModel 重組
- 位置：預設卡片上方 -48dp；若卡片在第一列改顯示卡片下方

---

## 驗收標準
1. 短按 `+` / `−`：行為與舊版一致（單次加減）。
2. 按住 `+` / `−` 超過設定的「長按啟動延遲」：開始依「連續計數速度」自動連續加減。
3. 連續期間：卡片上方出現對應顏色氣泡顯示即時數量；放開立刻消失。
4. 設定頁兩條 Slider 可即時改變速度與啟動延遲，重啟後保留。
5. 觸覺回饋符合策略；無 ANR 或卡頓。
6. 數量歸 0 後再按 `−` 不應造成負數（沿用既有保護）。

