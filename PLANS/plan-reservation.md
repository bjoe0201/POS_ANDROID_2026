# 訂位功能規格（Reservation Feature）

> 文件版本：1.0 | 建立日期：2026-04-22

---

## 一、功能概述

在現有 POS 系統中新增「訂位」分頁，讓店家可以管理每日桌次的訂位安排。
訂位資料以本地 Room 資料庫儲存，不需要網路。

---

## 二、底部導航整合

- 底部新增第五個分頁：**訂位**（emoji：📅），排列於「記帳」之後，位置為第 2 個
- 可於設定頁「功能頁面」中開啟 / 關閉此分頁（與現有其他分頁一致）
- 入口路由：`reservation`

---

## 三、畫面結構

```
訂位分頁
├── 月曆主畫面（ReservationCalendarScreen）
│   ├── 年月切換列（上方）
│   ├── 月曆格（左右滑動切換月份）
│   └── 日期格 → 點選 → 當日訂位畫面
└── 當日訂位畫面（ReservationDayScreen）
    ├── 標題列（顯示日期 + 返回鍵）
    ├── X 軸：桌次（從 tables 資料表讀取啟用中的桌次）
    ├── Y 軸：每小時時段（依設定的營業時間）
    ├── 訂位色塊
    └── 訂位子視窗（ReservationDialog）
```

---

## 四、月曆主畫面（ReservationCalendarScreen）

### 4.1 年月切換列

```
[ < ]  2026 年 04 月  [ > ]
```

- 左右箭頭切換上/下個月
- 點選「年」可跳出年份選擇器（±5 年範圍）
- 點選「月」可跳出月份選擇器（1–12 月）

### 4.2 月曆格

- 7 欄（日一二三四五六），`LazyRow` 或 `HorizontalPager` 實作左右滑動切換月份
- 每格顯示：
  - 日期數字
  - 當天若有訂位，顯示最多 3 條摘要（桌次 + 時間，如 `1號桌 18:00`），超過以 `+N` 表示
  - 今日日期以 accent 色圓形底色標記
  - 週日以錯誤色（紅）顯示

### 4.3 互動

- 點選任一日期格 → 進入當日訂位畫面

---

## 五、當日訂位畫面（ReservationDayScreen）

### 5.1 版面配置

- 標題列：`← YYYY年MM月DD日（週X）`
- 內容區：橫向可捲動（X 軸）× 縱向可捲動（Y 軸）的格狀時間表
  - **X 軸**：顯示所有啟用中的桌次（`tables.isActive = true`），欄寬固定（建議 80–100 dp）
  - **Y 軸**：每小時一格，依設定的營業起迄時間產生（例：11:00–22:00 → 12 格），左側固定顯示時間標籤

### 5.2 格狀時間表互動

| 區域 | 動作 |
|------|------|
| 空白格 | 點選 → 開啟「新增訂位子視窗」，預填點選的桌次與時間 |
| 訂位色塊 | 點選 → 開啟「編輯訂位子視窗」 |

### 5.3 訂位色塊

- 依重要性顯示不同底色：

| 重要性 | 色彩 | 建議色值 |
|--------|------|---------|
| 一般 | 綠色 | `#4CAF50` |
| 重要 | 黃色 | `#FFC107` |
| 非常重要 | 紅色 | `#F44336` |

- 色塊高度依預訂時間長度等比縮放（每 60 分鐘 = 1 格高）
- 色塊內顯示：姓名 + 電話後 4 碼（空間不足時僅顯示姓名）
- 同桌同時段若有重疊訂位，並排顯示並縮減寬度

---

## 六、訂位子視窗（ReservationDialog）

### 6.1 欄位

| 欄位 | 類型 | 備註 |
|------|------|------|
| 姓名 | 文字輸入 | 必填 |
| 電話 | 數字輸入 | 必填 |
| 桌次 | 下拉選單 | 從 `tables` 讀取啟用中桌次 |
| 開始時間 | 時間選擇器 | HH:mm，限營業時間內 |
| 結束時間 | 時間選擇器 | HH:mm，自動 = 開始 + 預設用餐時間 |
| 人數 | 數字輸入 | 選填 |
| 重要性 | 單選（一般 / 重要 / 非常重要） | 預設：一般 |
| 備註 | 多行文字 | 選填 |

### 6.2 按鈕

- **新增模式**：`取消` | `確認新增`
- **編輯模式**：`刪除`（左側，錯誤色）| `取消` | `儲存`

### 6.3 刪除確認

點選「刪除」後，彈出 `AlertDialog`：

```
標題：確認刪除訂位
內容：「確定要刪除 {姓名} 的訂位（{桌次} {時間}）？」
按鈕：取消 | 確定刪除（錯誤色）
```

---

## 七、設定頁新增項目（SettingsScreen）

在現有設定頁新增「訂位設定」`SectionCard`：

| 設定項目 | 類型 | 預設值 | 說明 |
|----------|------|--------|------|
| 營業開始時間 | 時間選擇器 | `11:00` | 當日訂位畫面 Y 軸起點 |
| 營業結束時間 | 時間選擇器 | `22:00` | 當日訂位畫面 Y 軸終點 |
| 休息開始時間 | 時間選擇器（可關閉） | 關閉 | 休息時間在格狀表以灰色遮罩顯示，不可訂位 |
| 休息結束時間 | 時間選擇器（可關閉） | 關閉 | 同上 |
| 預設用餐時間 | 下拉選單 | `90 分鐘` | 選項：30 / 45 / 60 / 90 / 120 分鐘 |

---

## 八、資料庫設計

### 8.1 新增資料表：`reservations`

```kotlin
@Entity(tableName = "reservations")
data class ReservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,                  // FK → tables.id
    val tableName: String,              // 快照，桌次顯示名稱
    val guestName: String,              // 姓名
    val guestPhone: String,             // 電話
    val guestCount: Int = 0,            // 人數
    val date: String,                   // 日期，格式 "yyyy-MM-dd"
    val startTime: String,              // 開始時間，格式 "HH:mm"
    val endTime: String,                // 結束時間，格式 "HH:mm"
    val importance: Int = 0,            // 0=一般, 1=重要, 2=非常重要
    val remark: String = "",            // 備註
    val createdAt: Long = System.currentTimeMillis()
)
```

### 8.2 DAO：`ReservationDao`

```kotlin
@Dao
interface ReservationDao {
    @Query("SELECT * FROM reservations WHERE date = :date ORDER BY startTime ASC")
    fun getByDate(date: String): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE date LIKE :yearMonth || '%' ORDER BY date ASC, startTime ASC")
    fun getByMonth(yearMonth: String): Flow<List<ReservationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reservation: ReservationEntity): Long

    @Delete
    suspend fun delete(reservation: ReservationEntity)
}
```

### 8.3 AppDatabase 更新

- 在 `AppDatabase` 的 `@Database` 註解中加入 `ReservationEntity::class`
- 版本號從現有版本 +1，並加入對應 `Migration`

---

## 九、DataStore 設定 Key（新增至 SettingsDataStore）

```kotlin
val BUSSINESS_START  = stringPreferencesKey("biz_start")   // 預設 "11:00"
val BUSSINESS_END    = stringPreferencesKey("biz_end")      // 預設 "22:00"
val BREAK_START      = stringPreferencesKey("break_start")  // 預設 "" (空=無休息)
val BREAK_END        = stringPreferencesKey("break_end")    // 預設 ""
val DEFAULT_DURATION = intPreferencesKey("default_duration")// 預設 90
```

---

## 十、架構分層

```
data/
  db/
    entity/ReservationEntity.kt
    dao/ReservationDao.kt
  repository/ReservationRepository.kt

ui/
  reservation/
    ReservationCalendarScreen.kt     — 月曆主畫面
    ReservationDayScreen.kt          — 當日格狀訂位畫面
    ReservationDialog.kt             — 新增/編輯子視窗
    ReservationViewModel.kt          — 共用 ViewModel（月曆+當日）
```

---

## 十一、導航路由更新

```kotlin
// Screen 新增
object Reservation : Screen("reservation")

// bottomTabs 順序（訂位排第 2）
val bottomTabs = listOf(
    BottomTab(Screen.Order,       "記帳",   "🛒"),  // 1：永遠開啟
    BottomTab(Screen.Reservation, "訂位",   "📅"),  // 2：可在設定關閉
    BottomTab(Screen.ItemSetting, "菜單管理","🥩"),  // 3：可在設定關閉
    BottomTab(Screen.TableSetting,"桌號設定","🪑"),  // 4：可在設定關閉
    BottomTab(Screen.Report,      "報表",   "📊"),  // 5：可在設定關閉
)

// NavHost 新增
composable(Screen.Reservation.route) { ReservationCalendarScreen() }
```

---

## 十二、備份 / 還原

- `BackupManager` 的 JSON 匯出 / 匯入需加入 `reservations` 資料表
- 匯出：序列化全部訂位記錄
- 匯入：覆蓋全部訂位記錄（與現有 menu items 邏輯一致）

---

## 十三、實作優先順序

| 優先級 | 項目 |
|--------|------|
| P0 | 資料庫 schema（Entity + DAO + Migration） |
| P0 | ReservationRepository |
| P1 | 設定頁「訂位設定」（營業時間 + 預設用餐時間） |
| P1 | 當日訂位畫面（格狀時間表 + 色塊） |
| P1 | 訂位子視窗（新增 / 編輯 / 刪除） |
| P2 | 月曆主畫面（月份摘要顯示） |
| P2 | 底部導航整合 + 設定開關 |
| P3 | 備份 / 還原支援 |
