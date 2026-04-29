# Google Drive 自動備份功能規劃

> 狀態：規劃中（尚未實作）
> 目標版本：待定
> 建立日期：2026-04-30

---

## 1. 功能概述

在「設定」頁面的自動備份區段下方，新增「Google 雲端備份」區塊。每次本機自動備份完成後，可選擇同步上傳至 Google Drive。

### 使用者故事

1. 使用者在設定頁開啟「Google 雲端備份」開關
2. 系統檢查是否已有 Google 授權；若無，開啟瀏覽器進行 OAuth 授權
3. 授權成功後，每次自動備份完成會自動上傳 ZIP 到 Google Drive
4. 若當天 Google 備份失敗，設定分頁顯示警示徽章 + 設定頁顯示橘色警告區塊

---

## 2. 前置條件：Google Cloud 專案設定

實作前需先在 Google Cloud Console 完成以下設定：

1. **建立或選擇 Google Cloud 專案**
   - 前往 https://console.cloud.google.com/
   - 建立新專案（如 `POS-Backup`）

2. **啟用 Google Drive API**
   - API 與服務 → 啟用 API → 搜尋「Google Drive API」→ 啟用

3. **設定 OAuth 同意畫面**
   - OAuth 同意畫面 → 外部 → 填入應用程式名稱
   - 新增範圍：`https://www.googleapis.com/auth/drive.file`（僅限 App 建立的檔案）
   - 新增測試使用者（若尚未發布）

4. **建立 OAuth 2.0 Client ID**
   - 憑證 → 建立憑證 → OAuth 用戶端 ID
   - **應用程式類型選擇「Android」**（若使用 Credential Manager / Google Sign-In）
   - 或選擇 **「網頁應用程式」**（若使用 AppAuth + Custom Tabs）
   - 記下 Client ID，程式碼中需要使用

---

## 3. 技術方案選擇

### 3.1 Google 登入方式

| 方案 | 優點 | 缺點 | 適用情境 |
|------|------|------|----------|
| **Credential Manager（推薦）** | 原生 UI、自動管理 token、Google 官方推薦 | 需要 Google Play Services (GMS) | 一般 Android 平板 |
| **AppAuth + Custom Tabs** | 不依賴 GMS、任何有瀏覽器的裝置可用 | UX 稍差（跳轉瀏覽器）、需手動管理 token | 無 GMS 的 IoT/工業裝置 |
| **兩者兼顧** | 最大相容性 | 實作複雜度較高 | 不確定目標裝置 |

**建議**：先確認目標 POS 裝置是否有 GMS 再決定。大多數 Android 平板都有 GMS，選 Credential Manager 即可。

### 3.2 Drive API 呼叫方式

| 方案 | 優點 | 缺點 |
|------|------|------|
| **OkHttp + REST API v3（推薦）** | 輕量、APK 增量小、只需 5 個 endpoint | 需手動組裝 HTTP 請求 |
| **google-api-services-drive** | 官方封裝、型別安全 | 依賴樹龐大、APK 增量明顯 |

**推薦 OkHttp + REST**，因為只需要：建立資料夾、上傳檔案、列出檔案、刪除檔案、檢查資料夾是否存在。

---

## 4. 新增依賴

```toml
# gradle/libs.versions.toml 新增

[versions]
appauth = "0.11.1"          # OpenID AppAuth（若選 AppAuth 方案）
okhttp = "4.12.0"           # HTTP client for Drive REST API
credentialManager = "1.5.0" # （若選 Credential Manager 方案）
googleId = "1.1.1"          # （若選 Credential Manager 方案）

[libraries]
# 方案 A：Credential Manager
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentialManager" }
androidx-credentials-play = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentialManager" }
google-id = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleId" }

# 方案 B：AppAuth
appauth = { group = "net.openid", name = "appauth", version.ref = "appauth" }

# 共用
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
```

---

## 5. AndroidManifest.xml 變更

```xml
<!-- 新增網路權限 -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- 若使用 AppAuth，需新增 redirect activity -->
<activity
    android:name="net.openid.appauth.RedirectUriReceiverActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="com.pos.app" android:host="oauth2callback" />
    </intent-filter>
</activity>
```

---

## 6. 新增 / 修改檔案清單

### 6.1 新增檔案

| 檔案 | 用途 |
|------|------|
| `util/GoogleDriveBackupManager.kt` | Google Drive 上傳/管理核心邏輯：OAuth 流程、token 管理、Drive API 呼叫、失敗追蹤 |

只新增 1 個檔案，保持簡單。OAuth 邏輯與 Drive API 呼叫都封裝在同一個 Manager 中。

### 6.2 修改檔案

| 檔案 | 變更內容 |
|------|----------|
| `AndroidManifest.xml` | 加 `INTERNET` 權限 + AppAuth redirect activity（若需要） |
| `gradle/libs.versions.toml` | 加 okhttp、appauth/credentials 版本與 library |
| `app/build.gradle.kts` | 加 implementation 宣告 |
| `data/datastore/SettingsDataStore.kt` | 加 4 個新 DataStore key（見第 7 節） |
| `data/repository/SettingsRepository.kt` | 暴露新 key 的 Flow 與 setter |
| `util/AutoBackupManager.kt` | 備份成功後呼叫 callback 通知 Google 上傳 |
| `ui/settings/SettingsViewModel.kt` | 新增 Google 備份狀態欄位與操作方法 |
| `ui/settings/SettingsScreen.kt` | 新增 `GoogleDriveBackupSection` 組合函式 |
| `ui/navigation/NavGraph.kt`（或 HomeScreen） | 設定分頁 icon 加上失敗警示徽章 |
| `di/AppModule.kt` | 提供 `GoogleDriveBackupManager` singleton |
| `CLAUDE.md` | 同步文件 |
| `README.md` | 同步文件 |

---

## 7. 新增 DataStore Keys

```kotlin
// SettingsDataStore.kt companion object 新增：

// Google 雲端備份開關
private val GOOGLE_BACKUP_ENABLED = booleanPreferencesKey("google_backup_enabled")

// OAuth AuthState JSON（含 access token + refresh token + expiry）
private val GOOGLE_AUTH_STATE = stringPreferencesKey("google_auth_state")

// 最近一次成功上傳的 ISO 時間戳
private val GOOGLE_LAST_BACKUP_AT = stringPreferencesKey("google_last_backup_at")

// 最近一次失敗的日期 "yyyy-MM-dd"，用於判斷今日是否需要顯示警告
private val GOOGLE_LAST_FAILURE_DATE = stringPreferencesKey("google_last_failure_date")
```

---

## 8. UI 設計

### 8.1 Google 雲端備份區塊（SettingsScreen）

位於現有「自動備份」區段下方，獨立的 SectionCard。

**狀態 A — 開關關閉（預設）**
```
┌─────────────────────────────────────────┐
│ Google 雲端備份                          │
│                                         │
│ ┌─ Row ─────────────────────────────┐   │
│ │ 啟用 Google 雲端備份    [Switch]  │   │
│ └───────────────────────────────────┘   │
│                                         │
│ 每次自動備份完成後，同步上傳至 Google    │
│ Drive「火鍋店POS備份」資料夾。           │
└─────────────────────────────────────────┘
```

**狀態 B — 開關開啟，尚未授權**
```
┌─────────────────────────────────────────┐
│ Google 雲端備份                          │
│                                         │
│ ┌─ Row ─────────────────────────────┐   │
│ │ 啟用 Google 雲端備份    [Switch]  │   │
│ └───────────────────────────────────┘   │
│                                         │
│ ┌─ Status Box ──────────────────────┐   │
│ │  狀態：未連線                      │   │
│ │  [連線 Google 帳號] (Button)       │   │
│ └───────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

**狀態 C — 已連線，正常運作**
```
┌─────────────────────────────────────────┐
│ Google 雲端備份                          │
│                                         │
│ ┌─ Row ─────────────────────────────┐   │
│ │ 啟用 Google 雲端備份    [Switch]  │   │
│ └───────────────────────────────────┘   │
│                                         │
│ ┌─ Status Box (綠色邊框) ───────────┐   │
│ │  狀態：已連線                      │   │
│ │  最近上傳：2026-04-30 14:32       │   │
│ └───────────────────────────────────┘   │
│                                         │
│ [取消連線] (OutlinedButton)              │
└─────────────────────────────────────────┘
```

**狀態 D — 今日備份失敗**
```
┌─────────────────────────────────────────┐
│ Google 雲端備份                          │
│                                         │
│ ┌─ Row ─────────────────────────────┐   │
│ │ 啟用 Google 雲端備份    [Switch]  │   │
│ └───────────────────────────────────┘   │
│                                         │
│ ┌─ Warning Box (橘色邊框) ──────────┐   │
│ │  今日 Google 備份失敗              │   │
│ │  請檢查網路連線或重新授權          │   │
│ │  [重新授權]  [立即上傳]            │   │
│ └───────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

### 8.2 設定分頁徽章

當 `googleFailedToday == true` 時，底部導航「設定」分頁 icon 顯示小圓點警示徽章（與現有報表頁未結帳徽章相同模式）。

---

## 9. 核心流程

### 9.1 啟用與授權流程

```
使用者開啟 Switch
    → 檢查 GOOGLE_AUTH_STATE 是否有效
    → 有效：顯示「已連線」，結束
    → 無效/空：顯示「未連線」+ 「連線 Google 帳號」按鈕
        → 使用者點擊按鈕
        → 啟動 OAuth 流程（Credential Manager 或 AppAuth Custom Tabs）
        → scope: https://www.googleapis.com/auth/drive.file
        → 授權成功：儲存 AuthState 到 DataStore，顯示「已連線」
        → 授權失敗/取消：顯示錯誤 Snackbar，Switch 保持開啟但狀態仍為「未連線」
```

### 9.2 自動上傳流程

```
AutoBackupManager.performBackupInternal() 完成
    → 呼叫 GoogleDriveBackupManager.onLocalBackupCompleted(entry)
    → 檢查 google_backup_enabled == true？否 → return
    → 檢查 AuthState 有效？否 → 記錄失敗日期 → return
    → Token 過期？嘗試 refresh → 失敗 → 記錄失敗日期 → return
    → 確保 Drive 上有「火鍋店POS備份」資料夾（不存在就建立）
    → 上傳 ZIP（同名檔案覆蓋）
    → 成功：更新 GOOGLE_LAST_BACKUP_AT，清除 GOOGLE_LAST_FAILURE_DATE
    → 失敗：設定 GOOGLE_LAST_FAILURE_DATE = 今天日期
    → 清理 Drive 上的舊檔案（保留天數同本機設定）
```

### 9.3 Drive API 呼叫細節

所有呼叫使用 OkHttp + Bearer token，目標 endpoint：

| 操作 | HTTP 方法 | URL |
|------|-----------|-----|
| 查詢資料夾 | GET | `https://www.googleapis.com/drive/v3/files?q=name='火鍋店POS備份' and mimeType='application/vnd.google-apps.folder' and trashed=false` |
| 建立資料夾 | POST | `https://www.googleapis.com/drive/v3/files` (metadata only) |
| 上傳檔案 | POST | `https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart` |
| 更新檔案（同日覆蓋） | PATCH | `https://www.googleapis.com/upload/drive/v3/files/{fileId}?uploadType=multipart` |
| 列出檔案 | GET | `https://www.googleapis.com/drive/v3/files?q='{folderId}' in parents` |
| 刪除舊檔案 | DELETE | `https://www.googleapis.com/drive/v3/files/{fileId}` |

### 9.4 錯誤處理

| 錯誤情境 | 處理方式 |
|----------|----------|
| 無網路 | 靜默跳過，設定 failure date |
| Token 過期但 refresh 成功 | 透明重試，使用者無感 |
| Token 過期且 refresh 失敗（已撤銷） | 清除 AuthState，設定 failure date，UI 顯示「未連線」+ 警告 |
| Drive API 403/404/500 | 設定 failure date，UI 顯示警告 |
| 上傳逾時 | 設定 failure date，UI 顯示警告 |

### 9.5 失敗提醒機制

- **不使用 Android 系統通知**（避免需要通知權限）
- 使用 **App 內 UI 提醒**：
  - 設定分頁 icon 顯示小圓點徽章（從任何頁面都看得到）
  - 設定頁 Google 區塊顯示橘色警告框 + 「重新授權」「立即上傳」按鈕
- 判斷邏輯：`GOOGLE_LAST_FAILURE_DATE == 今天日期字串`
- 成功上傳後自動清除

---

## 10. SettingsUiState 新增欄位

```kotlin
// 在 SettingsUiState data class 新增：
val googleBackupEnabled: Boolean = false,
val googleConnected: Boolean = false,       // AuthState 含有效 refresh token
val googleLastBackupAt: String? = null,     // 格式化的時間字串
val googleFailedToday: Boolean = false,     // failure date == today
val googleUploading: Boolean = false,       // 防止重複點擊
```

---

## 11. SettingsViewModel 新增方法

```kotlin
fun setGoogleBackupEnabled(enabled: Boolean)  // 切換開關
fun startGoogleAuth(launcher: ActivityResultLauncher)  // 發起 OAuth
fun handleGoogleAuthResult(result: ActivityResult)     // 處理 OAuth 回調
fun disconnectGoogle()                                 // 取消連線（清除 AuthState）
fun retryGoogleUpload()                                // 手動重試上傳
```

---

## 12. AutoBackupManager 修改

在 `performBackupInternal()` 成功後，加入 callback 通知：

```kotlin
// 新增 interface
interface BackupCompletionListener {
    suspend fun onBackupCompleted(entry: BackupEntry)
}

// 新增 property
var completionListener: BackupCompletionListener? = null

// 在 performBackupInternal() 的 entry 回傳前加入：
runCatching { completionListener?.onBackupCompleted(entry) }
```

`GoogleDriveBackupManager` 實作此 interface，在 `AppModule` 或 init 中連接。

---

## 13. GoogleDriveBackupManager.kt 架構

```kotlin
@Singleton
class GoogleDriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val settingsDataStore: SettingsDataStore
) : AutoBackupManager.BackupCompletionListener {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)  // ZIP 上傳可能較慢
        .build()

    // === OAuth 管理 ===
    suspend fun buildAuthIntent(): Intent          // 建立 OAuth intent
    suspend fun handleAuthResponse(intent: Intent) // 處理回調，儲存 AuthState
    suspend fun getFreshAccessToken(): String?     // 取得有效 token（自動 refresh）
    fun isConnected(): Boolean                     // 檢查是否有有效 AuthState
    suspend fun disconnect()                       // 清除所有 token

    // === Drive 操作 ===
    override suspend fun onBackupCompleted(entry: BackupEntry) // 自動上傳入口
    suspend fun uploadNow()                        // 手動觸發上傳
    private suspend fun ensureFolder(): String     // 確保資料夾存在，回傳 folderId
    private suspend fun uploadFile(folderId: String, file: File)
    private suspend fun cleanupOldFiles(folderId: String, keepDays: Int)

    // === 狀態 ===
    private suspend fun recordSuccess()            // 更新 GOOGLE_LAST_BACKUP_AT
    private suspend fun recordFailure()            // 設定 GOOGLE_LAST_FAILURE_DATE
}
```

---

## 14. 實作步驟（建議順序）

### Phase 1：基礎建設
1. 新增依賴到 `libs.versions.toml` 和 `app/build.gradle.kts`
2. `AndroidManifest.xml` 加 `INTERNET` 權限（+ AppAuth redirect activity 若需要）
3. `SettingsDataStore.kt` 加 4 個新 key + Flow + setter
4. `SettingsRepository.kt` 加 pass-through

### Phase 2：Google 核心邏輯
5. 建立 `GoogleDriveBackupManager.kt`（OAuth + Drive API）
6. `AppModule.kt` 提供 singleton
7. `AutoBackupManager.kt` 加 `BackupCompletionListener` callback

### Phase 3：ViewModel 整合
8. `SettingsViewModel.kt` 注入 `GoogleDriveBackupManager`
9. 新增 UiState 欄位 + 操作方法
10. 連接 DataStore Flow 到 UiState

### Phase 4：UI
11. `SettingsScreen.kt` 新增 `GoogleDriveBackupSection`
12. OAuth result launcher 處理
13. 設定分頁失敗警示徽章

### Phase 5：收尾
14. 更新 `CLAUDE.md` 和 `README.md`
15. 手動測試完整流程

---

## 15. 測試驗證

### 手動測試項目
- [ ] 開啟 Switch → 顯示「未連線」+ 連線按鈕
- [ ] 點擊連線 → 跳轉瀏覽器 OAuth → 授權成功 → 回到 App → 顯示「已連線」
- [ ] 觸發自動備份 → 本機備份完成 → Google Drive 出現 ZIP 檔案
- [ ] 同日再次備份 → Drive 上的檔案被覆蓋（不產生重複）
- [ ] 斷網後觸發備份 → Drive 上傳失敗 → 設定分頁出現徽章 → 設定頁出現橘色警告
- [ ] 點擊「立即上傳」→ 恢復網路後上傳成功 → 警告消失
- [ ] 到 Google 帳戶撤銷授權 → 下次備份失敗 → 顯示「未連線」+ 警告
- [ ] 點擊「重新授權」→ 重新 OAuth → 成功 → 立即上傳
- [ ] 點擊「取消連線」→ 清除 AuthState → Switch 維持開啟但顯示「未連線」
- [ ] 關閉 Switch → Google 區塊收合，不再觸發上傳

---

## 16. OAuth Scope 說明

使用 `https://www.googleapis.com/auth/drive.file` 而非完整 Drive 權限：
- 只能存取 App 自己建立的檔案
- 看不到使用者 Drive 中的其他檔案
- 最小權限原則，使用者更容易信任授權

---

## 17. 安全考量

- OAuth token 存在 DataStore（App 私有目錄），第三方 App 無法存取
- 使用 PKCE（Proof Key for Code Exchange）防止授權碼攔截攻擊
- `drive.file` scope 確保即使 token 洩漏，也只能存取 App 建立的檔案
- 所有 HTTP 通訊走 HTTPS
