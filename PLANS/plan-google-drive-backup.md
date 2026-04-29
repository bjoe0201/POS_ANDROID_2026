# Google Drive 自動備份功能規劃

> 狀態：⛔ 擱置（已評估後決定不實作 AccountManager + GoogleAuthUtil 方案）
> 目標版本：待定
> 建立日期：2026-04-30
> 最後更新：2026-04-30

---

## ⛔ 擱置原因（2026-04-30 評估）

### 問題 1：對一般使用者太複雜

原方案（`AccountManager + GoogleAuthUtil`）的技術前置條件對終端使用者（店家）完全不透明：

| 需要誰操作 | 操作內容 | 難度 |
|-----------|---------|------|
| **開發者**（一次性） | Google Cloud Console 建立專案、啟用 Drive API、設定 OAuth 同意畫面、建立 Android Client ID、取得 SHA-1 指紋 | 高，需要技術背景 |
| **開發者** | 下載 `google-services.json` 放入 `app/` 目錄並重新打包 APK | 高 |
| **一般使用者** | 開啟開關 → 選帳號 → 完成 | 低（才是正確方向） |

即使開發者完成了所有前置設定，若 App 分發給多個店家（不同 Android 裝置、不同 Google 帳號），仍可能遭遇：
- 裝置沒有 Google Play Services（工業平板常見）
- `UserRecoverableAuthException` 需要使用者手動補授權，體驗差
- OAuth 同意畫面未通過 Google 審核，僅限測試帳號可用

### 問題 2：安全性不足

- `GoogleAuthUtil.getToken()` 需要 `GET_ACCOUNTS` 或帳號選擇器，在部分 Android 版本行為不一致
- OAuth 同意畫面若為「外部」且未發布審核，使用者看到「此 App 未經驗證」的警告，造成信任問題
- `google-services.json` 雖非機密，但含 API Key，若管理不當可能被濫用

### 問題 3：維護成本高

- Google Play Services API 版本迭代快，`AccountPicker`、`GoogleAuthUtil` 已逐漸被 Credential Manager 取代
- Drive API OAuth scope 若未通過 Google 安全審查，未來可能被限制

---

## ✅ 建議替代方案（未來評估）

### 方案 A：讓使用者自行用 Google Drive App 備份（零開發）
- 店家在 Android 設定中開啟「Google 帳戶備份」或使用「Files by Google」手動上傳
- App 只負責產生本機 ZIP 備份檔（已實作），由使用者自行管理雲端同步
- **優點**：零開發、無 OAuth 複雜度、安全性由 Google 自身保障
- **缺點**：需要使用者手動操作

### 方案 B：WebDAV / 自架 NAS 備份
- 使用者輸入 WebDAV URL + 帳號密碼（例如 Synology NAS、Nextcloud）
- App 用 OkHttp 上傳 ZIP
- **優點**：不依賴 Google、可完全掌控
- **缺點**：需要使用者有自架伺服器

### 方案 C：使用 Google Drive API + 服務帳戶（Server-side Proxy）
- 建立一個後端 API（例如 Firebase Functions），App 只傳 ZIP 給後端，後端用服務帳戶上傳 Drive
- **優點**：使用者完全無感、無 OAuth 流程
- **缺點**：需要後端基礎設施、增加維護複雜度

### 方案 D（推薦）：等待 Android Credential Manager + Drive Authorization 成熟
- Google 正在推進 Credential Manager + `AuthorizationClient` 整合，未來 Drive 授權流程會更簡單
- 待 API 穩定後重新評估實作

---

---

## 1. 功能概述

在「設定」頁面的自動備份區段下方，新增「Google 雲端備份」區塊。每次本機自動備份完成後，可選擇同步上傳至 Google Drive。

### 使用者故事

1. 使用者在設定頁開啟「Google 雲端備份」開關
2. 系統檢查是否已有 Google 授權；若無，啟動 Google 帳號選擇與 OAuth 授權
3. 使用者可選擇裝置上的任一 Google 帳號，或透過瀏覽器登入其他 Google 帳號；**不得假設一定使用 Android 系統預設帳號**
4. 授權成功後，App 儲存「已授權帳號識別（accountName/email）+ 授權方式」，並在 UI 顯示目前連線帳號
5. 每次自動備份完成後，都使用該已授權帳號的 token 上傳 ZIP 到 Google Drive
6. 若當天 Google 備份失敗，設定分頁顯示警示徽章 + 設定頁顯示橘色警告區塊

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
   - **Credential Manager / Google Sign-In**：通常使用「Android」Client ID，需設定 package name 與 SHA-1 / SHA-256 憑證指紋
   - **AppAuth + Custom Tabs**：依 Google Cloud Console 與 redirect URI 設定選擇對應的 Android / Native client；redirect URI 必須與 `AndroidManifest.xml` 完全一致
   - 若同時保留多種登入方案，必須清楚區分各方案使用的 Client ID，避免用 Android Client 啟動 Web flow 或用 Web Client 呼叫 Android Sign-In
   - 記下 Client ID、redirect URI、scope；程式碼與文件需一致

---

## 3. 技術方案選擇

### 3.1 Google 登入方式

| 方案 | 優點 | 缺點 | 適用情境 |
|------|------|------|----------|
| **AccountManager + GoogleAuthUtil（GMS 裝置主方案）** | 可直接讓使用者從裝置 Google 帳號中挑選；`GoogleAuthUtil.getToken()` 會依指定帳號取得 Drive scope 的短期 access token；可避免誤用系統預設帳號 | 依賴 Google Play Services；不適用無 GMS 裝置；需處理 `UserRecoverableAuthException`、token 失效與帳號被移除 | 已確認 POS 平板有 GMS，且主要使用裝置內 Google 帳號 |
| **Credential Manager** | 原生帳號選擇 / 身分驗證 UI，Google 官方推薦用於登入身分 | **不等同 Drive OAuth 授權**；取得 Google ID token 不代表可呼叫 Drive API；仍需額外 Authorization / OAuth 流程 | 只需登入身分，或搭配其他授權方案使用 |
| **Google Sign-In / AuthorizationClient** | GMS 原生體驗，可要求 Drive scope，通常支援帳號選擇 | 依賴 GMS；API 版本差異較大；需確認 access token / scope 授權取得方式 | 有 GMS 且希望使用較新的 Play services 授權 API |
| **AppAuth + Custom Tabs** | 標準 OAuth 2.0 + PKCE；可登入不在裝置上的 Google 帳號；可保存 `AuthState` 與 refresh token | UX 稍差（跳轉瀏覽器）；需手動管理 token、redirect URI 與 AuthState | 無 GMS 裝置、工業平板、或需要完整標準 OAuth fallback |
| **兩者兼顧** | 最大相容性 | 實作複雜度較高；需清楚分流 token 儲存與錯誤處理 | 不確定目標裝置是否有 GMS |

**修訂建議**：若目標 POS 裝置確定有 Google Play Services，採用 **AccountManager + GoogleAuthUtil + 強制帳號選擇器** 作為第一版主方案；若目標裝置可能沒有 GMS，保留 **AppAuth + Custom Tabs** 作為 fallback。Credential Manager 可用於登入身分，但不可單獨視為 Drive API access token 來源。

> 關於 `setAlwaysShowAccountPicker(true)`：此概念的目標是「每次連線 / 切換帳號時都強制顯示帳號選擇器」，避免自動套用 Android 系統預設帳號。實作時需依實際 API 使用正確寫法：
> - 使用 `AccountPicker.newChooseAccountIntent(...)` 時，應使用其「always prompt / always show account picker」參數或等效設定，強制彈出帳號選擇畫面。
> - 若改用 Google Sign-In，只有在所採 SDK 版本確實提供 `setAlwaysShowAccountPicker(true)` 或等效 API 時才使用；不要在不存在的 Builder 上硬寫此方法。
> - 無論使用哪個 API，授權完成後都必須保存「使用者實際選定的帳號」，後續上傳不可重新猜測預設帳號。

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
playServicesAuth = "21.2.0" # AccountManager / GoogleAuthUtil / Google Sign-In / AuthorizationClient

[libraries]
# 主方案：AccountManager + GoogleAuthUtil（GMS）
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "playServicesAuth" }

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

<!-- AccountPicker / GoogleAuthUtil 主方案不建議直接列舉帳號；優先透過 Google 帳號選擇器取得使用者明確選定的帳號。 -->
<!-- 若未來實作確實需要直接讀取裝置帳號，才評估 GET_ACCOUNTS；避免預設加入危險權限。 -->

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
| `util/GoogleDriveBackupManager.kt` | Google Drive 上傳/管理核心邏輯：帳號選擇、`GoogleAuthUtil` token 取得、Drive API 呼叫、失敗追蹤 |

只新增 1 個檔案，保持簡單。Google 帳號授權邏輯與 Drive API 呼叫都封裝在同一個 Manager 中；若後續加入 AppAuth fallback，可在同一 Manager 內用 `authMethod` 分流。

### 6.2 修改檔案

| 檔案 | 變更內容 |
|------|----------|
| `AndroidManifest.xml` | 加 `INTERNET` 權限 + AppAuth redirect activity（若需要） |
| `gradle/libs.versions.toml` | 加 okhttp、play-services-auth；appauth/credentials 視 fallback 方案加入 |
| `app/build.gradle.kts` | 加 implementation 宣告 |
| `data/datastore/SettingsDataStore.kt` | 加 Google 備份與已授權帳號相關 DataStore key（見第 7 節） |
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

// 主方案（AccountManager + GoogleAuthUtil）不保存 refresh token；只保存使用者選定的帳號。
private val GOOGLE_ACCOUNT_NAME = stringPreferencesKey("google_account_name") // 通常為 email，例如 user@gmail.com

// UI 顯示用 email；多數情況與 accountName 相同，但保留欄位避免未來不同 provider / API 回傳格式差異
private val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")

// 已授權帳號的穩定識別（若取得得到，例如 Google user id / sub；AccountManager 不一定直接提供，可為 null）
private val GOOGLE_ACCOUNT_ID = stringPreferencesKey("google_account_id")

// 授權方案標記，例如 "google_auth_util" 或 "appauth"，方便未來 fallback / migration
private val GOOGLE_AUTH_METHOD = stringPreferencesKey("google_auth_method")

// AppAuth fallback 才需要：OAuth AuthState JSON（含 access token + refresh token + expiry）
private val GOOGLE_AUTH_STATE = stringPreferencesKey("google_auth_state")

// 最近一次成功上傳的 ISO 時間戳
private val GOOGLE_LAST_BACKUP_AT = stringPreferencesKey("google_last_backup_at")

// 最近一次失敗的日期 "yyyy-MM-dd"，用於判斷今日是否需要顯示警告
private val GOOGLE_LAST_FAILURE_DATE = stringPreferencesKey("google_last_failure_date")
```

> 主方案使用 `GoogleAuthUtil.getToken(context, account, scope)` 取得短期 access token；token 過期時重新呼叫 `getToken`，不要長期保存 access token 或 refresh token。若 `getToken` 拋出 `UserRecoverableAuthException`，需啟動 exception 內的 intent 讓使用者補授權。

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
│ │  帳號：backup@example.com          │   │
│ │  最近上傳：2026-04-30 14:32       │   │
│ └───────────────────────────────────┘   │
│                                         │
│ [切換帳號]  [取消連線]                   │
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
│ │  目前帳號：backup@example.com      │   │
│ │  請檢查網路連線或重新授權          │   │
│ │  [重新授權]  [切換帳號]  [立即上傳] │   │
│ └───────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

> UI 必須顯示目前已授權的 Google 帳號 email，避免店家誤以為備份會上傳到 Android 系統預設帳號。`切換帳號` 必須重新開啟帳號選擇器，而且需強制顯示選擇畫面，不可靜默沿用上一次或預設帳號。

### 8.2 設定分頁徽章

當 `googleFailedToday == true` 時，底部導航「設定」分頁 icon 顯示小圓點警示徽章（與現有報表頁未結帳徽章相同模式）。

---

## 9. 核心流程

### 9.1 啟用與授權流程

```
使用者開啟 Switch
    → 檢查 GOOGLE_ACCOUNT_NAME 是否存在
    → 存在：顯示「已連線」與帳號 email，下一次上傳時再即時檢查 token / scope
    → 不存在：顯示「未連線」+ 「連線 Google 帳號」按鈕
        → 使用者點擊按鈕
        → 啟動 AccountPicker，強制顯示帳號選擇器（不可自動使用系統預設帳號）
        → 使用者選擇 Google 帳號 accountName/email
        → 以該帳號呼叫 GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive.file")
            → 成功取得 access token：儲存 accountName/email/authMethod，顯示「已連線」
            → 拋出 UserRecoverableAuthException：啟動 exception.intent 讓使用者授權 Drive scope
                → 使用者同意後再次 getToken
                → 成功：儲存 accountName/email/authMethod，顯示「已連線」
            → 使用者取消 / 授權失敗：顯示錯誤 Snackbar，Switch 保持開啟但狀態仍為「未連線」
```

### 9.1.1 切換帳號流程

```
使用者點擊「切換帳號」或「重新授權」
    → 清除目前 GOOGLE_ACCOUNT_NAME / GOOGLE_ACCOUNT_EMAIL / GOOGLE_ACCOUNT_ID / GOOGLE_AUTH_METHOD
    → 啟動 AccountPicker，強制顯示帳號選擇器
    → 使用者選擇新帳號
    → 以新帳號執行 getToken + scope 授權
    → 成功後覆寫已授權帳號資訊
    → 下一次自動備份與手動立即上傳都使用新帳號 token
```

> 即使裝置只有一個 Google 帳號，連線與切換流程仍建議顯示帳號確認畫面，讓使用者明確知道備份目的地帳號。

### 9.2 自動上傳流程

```
AutoBackupManager.performBackupInternal() 完成
    → 呼叫 GoogleDriveBackupManager.onLocalBackupCompleted(entry)
    → 檢查 google_backup_enabled == true？否 → return
    → 讀取 GOOGLE_ACCOUNT_NAME；不存在 → 記錄失敗日期 → return
    → 建立 Account(accountName, "com.google")
    → 呼叫 GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
        → 成功：使用回傳 access token 呼叫 Drive API
        → UserRecoverableAuthException：記錄失敗日期，UI 顯示「需重新授權」
        → GoogleAuthException / IOException：記錄失敗日期，依錯誤顯示網路或授權問題
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
| access token 過期 | 重新呼叫 `GoogleAuthUtil.getToken()` 取得新 token，使用者無感 |
| `UserRecoverableAuthException`（尚未授權 Drive scope / 授權需更新） | 設定 failure date，UI 顯示「需重新授權」，使用者點擊後啟動 exception intent |
| `GoogleAuthException`（帳號不存在、授權被撤銷、GMS 問題） | 清除已連線狀態或標記需重新連線，設定 failure date，UI 顯示「未連線」+ 警告 |
| Drive API 403/404/500 | 設定 failure date，UI 顯示警告 |
| 上傳逾時 | 設定 failure date，UI 顯示警告 |
| 裝置 Google 帳號被移除 | 清除 `GOOGLE_ACCOUNT_NAME` / email，設定 failure date，要求重新選擇帳號 |

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
data class SettingsUiState(
    val googleBackupEnabled: Boolean = false,
    val googleConnected: Boolean = false,       // 已保存使用者選定的 Google accountName/email
    val googleAccountEmail: String? = null,     // UI 顯示目前上傳目的地帳號
    val googleAuthMethod: String? = null,       // 例如 "google_auth_util" / "appauth"
    val googleLastBackupAt: String? = null,     // 格式化的時間字串
    val googleFailedToday: Boolean = false,     // failure date == today
    val googleUploading: Boolean = false,       // 防止重複點擊
    val googleNeedsReauth: Boolean = false,     // UserRecoverableAuthException / scope 失效時顯示重新授權
)
```

---

## 11. SettingsViewModel 新增方法

```kotlin
fun setGoogleBackupEnabled(enabled: Boolean)  // 切換開關
fun startGoogleAccountPicker(launcher: ActivityResultLauncher<Intent>) // 發起帳號選擇；需強制顯示 picker
fun handleGoogleAccountPicked(result: ActivityResult)                  // 保存使用者選定 accountName/email，並嘗試 getToken
fun handleGoogleRecoverableAuthResult(result: ActivityResult)          // 處理 UserRecoverableAuthException 授權回來後的結果
fun reauthorizeGoogle(launcher: ActivityResultLauncher<Intent>)        // 對目前帳號重新要求 Drive scope 授權
fun switchGoogleAccount(launcher: ActivityResultLauncher<Intent>)      // 清除舊帳號後重新開啟 picker
fun disconnectGoogle()                                                 // 取消連線（清除帳號資訊與 fallback AuthState）
fun retryGoogleUpload()                                                // 手動重試上傳
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

    // === Google 帳號 / 授權管理 ===
    fun buildAccountPickerIntent(alwaysShow: Boolean = true): Intent // 建立帳號選擇 intent；必須強制顯示 picker
    suspend fun handleAccountPicked(intent: Intent)                  // 儲存 accountName/email，並嘗試取得 Drive scope token
    suspend fun handleRecoverableAuthResult()                        // 使用者同意 scope 後重試 getToken
    suspend fun getFreshAccessToken(): String?                       // 以保存的 accountName 即時呼叫 GoogleAuthUtil.getToken
    fun isConnected(): Boolean                                       // 檢查是否已保存 accountName/email
    suspend fun disconnect()                                         // 清除帳號資訊、AuthState fallback、狀態旗標
    suspend fun switchAccount(accountPickerIntent: Intent)           // 重新選擇帳號並覆寫已授權帳號

    // === Drive 操作 ===
    override suspend fun onBackupCompleted(entry: BackupEntry) // 自動上傳入口
    suspend fun uploadNow()                        // 手動觸發上傳
    private suspend fun ensureFolder(): String     // 確保資料夾存在，回傳 folderId
    private suspend fun uploadFile(folderId: String, file: File)
    private suspend fun cleanupOldFiles(folderId: String, keepDays: Int)

    // === 狀態 ===
    private suspend fun recordSuccess()            // 更新 GOOGLE_LAST_BACKUP_AT
    private suspend fun recordFailure(needsReauth: Boolean = false) // 設定 GOOGLE_LAST_FAILURE_DATE / reauth 狀態
}
```

> `GoogleDriveBackupManager` 不應在背景自行呼叫系統預設帳號，也不應用 `AccountManager.getAccountsByType("com.google").first()` 之類的方式猜測帳號。所有 Drive API 呼叫都必須以 `GOOGLE_ACCOUNT_NAME` 指定的帳號取得 token。

---

## 14. 實作步驟（建議順序）

### Phase 1：基礎建設
1. 新增依賴到 `libs.versions.toml` 和 `app/build.gradle.kts`：主方案至少需要 `play-services-auth`、`okhttp`
2. `AndroidManifest.xml` 加 `INTERNET` 權限（+ AppAuth redirect activity 若需要 fallback）
3. `SettingsDataStore.kt` 加 Google 備份、已授權帳號、失敗狀態相關 key + Flow + setter
4. `SettingsRepository.kt` 加 pass-through

### Phase 2：Google 核心邏輯
5. 建立 `GoogleDriveBackupManager.kt`（AccountPicker + GoogleAuthUtil + Drive API）
6. `AppModule.kt` 提供 singleton
7. `AutoBackupManager.kt` 加 `BackupCompletionListener` callback

### Phase 3：ViewModel 整合
8. `SettingsViewModel.kt` 注入 `GoogleDriveBackupManager`
9. 新增 UiState 欄位 + 操作方法
10. 連接 DataStore Flow 到 UiState

### Phase 4：UI
11. `SettingsScreen.kt` 新增 `GoogleDriveBackupSection`
12. 加入 AccountPicker result launcher 與 UserRecoverableAuthException 授權 result launcher
13. UI 顯示目前連線 Google 帳號 email，並提供「切換帳號」「重新授權」「立即上傳」
14. 設定分頁失敗警示徽章

### Phase 5：收尾
15. 更新 `CLAUDE.md` 和 `README.md`
16. 手動測試完整流程，特別是非系統預設帳號與帳號切換

---

## 15. 測試驗證

### 手動測試項目
- [ ] 開啟 Switch → 顯示「未連線」+ 連線按鈕
- [ ] 點擊連線 → 強制顯示 Google 帳號選擇器，而不是靜默使用系統預設帳號
- [ ] 選擇「非系統預設」Google 帳號 → 授權成功 → 回到 App → 顯示「已連線」與該帳號 email
- [ ] 觸發自動備份 → 本機備份完成 → ZIP 檔案出現在已選定帳號的 Google Drive，而不是系統預設帳號
- [ ] 同日再次備份 → Drive 上的檔案被覆蓋（不產生重複）
- [ ] 斷網後觸發備份 → Drive 上傳失敗 → 設定分頁出現徽章 → 設定頁出現橘色警告
- [ ] 點擊「立即上傳」→ 恢復網路後上傳成功 → 警告消失
- [ ] 到 Google 帳戶撤銷授權 → 下次備份失敗 → 顯示「未連線」+ 警告
- [ ] 點擊「重新授權」→ 啟動 `UserRecoverableAuthException` 對應授權畫面或重新 getToken → 成功 → 立即上傳
- [ ] 點擊「切換帳號」→ 再次強制顯示帳號選擇器 → 選擇另一個帳號 → 後續上傳改到新帳號 Drive
- [ ] 從 Android 系統移除已授權 Google 帳號 → 下次備份失敗 → 清除連線狀態並要求重新選擇帳號
- [ ] 點擊「取消連線」→ 清除 accountName/email/AuthState fallback → Switch 維持開啟但顯示「未連線」
- [ ] 關閉 Switch → Google 區塊收合，不再觸發上傳

---

## 16. Google認證流程說明

### 16.1 核心原則

1. **不使用系統預設帳號假設**  
   Android 裝置可能登入多個 Google 帳號，店家要備份的帳號不一定是系統預設帳號。App 只認使用者在本 App 內明確選擇並授權的帳號。

2. **每次連線 / 切換帳號都強制顯示帳號選擇器**  
   採用 `AccountPicker` 或等效 API 時，必須使用「always prompt / always show account picker」設定。Gemini 建議的 `setAlwaysShowAccountPicker(true)` 可視為設計要求；實作時需依實際 SDK API 名稱套用，若該方法不存在則改用 AccountPicker 的等效參數。

3. **保存帳號識別，不保存長期 token**  
   主方案只保存 `GOOGLE_ACCOUNT_NAME` / `GOOGLE_ACCOUNT_EMAIL` / 可取得時的 `GOOGLE_ACCOUNT_ID`。上傳前以該帳號呼叫 `GoogleAuthUtil.getToken()` 即時取得短期 access token。不要把 access token 當成永久憑證，也不要自行保存 refresh token。

4. **Drive API 永遠使用已授權帳號 token**  
   上傳、查詢資料夾、覆蓋檔案、刪除舊檔案都必須使用已保存帳號取得的 Bearer token，不可在背景改用 `AccountManager` 第一個帳號或 Google Play services 預設帳號。

### 16.2 首次連線流程

```
使用者點擊「連線 Google 帳號」
    → 開啟 AccountPicker（強制顯示）
    → 使用者選擇帳號 A（可以不是系統預設帳號）
    → App 以帳號 A 呼叫 GoogleAuthUtil.getToken(DRIVE_SCOPE)
        → 若需要使用者同意 Drive scope，捕捉 UserRecoverableAuthException 並啟動其 intent
        → 使用者同意後重新 getToken
    → 成功後保存帳號 A 的 accountName/email/authMethod
    → UI 顯示「已連線：帳號 A」
```

### 16.3 自動備份認證流程

```
本機自動備份 ZIP 完成
    → 讀取已保存 accountName/email
    → 以該 accountName 建立 Account
    → 呼叫 GoogleAuthUtil.getToken() 取得短期 access token
    → 使用 Bearer token 呼叫 Drive REST API
    → 成功：更新最後上傳時間並清除失敗警告
    → 失敗：依錯誤類型標記今日失敗 / 需重新授權 / 需重新選擇帳號
```

### 16.4 帳號切換流程

```
使用者點擊「切換帳號」
    → 清除舊帳號資訊
    → 強制顯示 AccountPicker
    → 使用者選擇帳號 B
    → 以帳號 B 完成 Drive scope 授權
    → 保存帳號 B
    → 後續上傳只使用帳號 B
```

### 16.5 授權失效與重新授權

| 情境 | App 行為 |
|------|----------|
| access token 過期 | 重新呼叫 `GoogleAuthUtil.getToken()` |
| 尚未同意 Drive scope / scope 需更新 | 捕捉 `UserRecoverableAuthException`，提示使用者點「重新授權」 |
| 使用者在 Google 帳戶安全頁撤銷授權 | 標記需重新授權；若無法恢復則清除帳號資訊 |
| Android 系統移除該 Google 帳號 | 清除 `GOOGLE_ACCOUNT_NAME` / email，要求重新選擇帳號 |
| Google Play Services 不可用或過舊 | 顯示 GMS 錯誤；若目標裝置常見此問題，改走 AppAuth fallback |

### 16.6 與其他方案的關係

- **Credential Manager**：可協助登入身分，但不代表已取得 Drive API access token；不可只靠 ID token 呼叫 Drive。
- **AppAuth fallback**：若裝置沒有 GMS，改用瀏覽器 OAuth + PKCE；此時才保存 `GOOGLE_AUTH_STATE`，並使用 refresh token 更新 access token。
- **Google Sign-In / AuthorizationClient**：若未來改用此方案，也必須維持同樣原則：強制帳號選擇、保存使用者選定帳號、Drive API 使用該帳號授權 token。

---

## 17. OAuth Scope 說明

使用 `https://www.googleapis.com/auth/drive.file` 而非完整 Drive 權限：
- 只能存取 App 自己建立的檔案
- 看不到使用者 Drive 中的其他檔案
- 最小權限原則，使用者更容易信任授權

---

## 18. 安全考量

- 主方案不長期保存 access token / refresh token；DataStore 只保存已授權帳號識別、授權方式與備份狀態
- 若使用 AppAuth fallback，`AuthState` 存在 DataStore（App 私有目錄），第三方 App 無法存取
- AppAuth fallback 使用 PKCE（Proof Key for Code Exchange）防止授權碼攔截攻擊
- `drive.file` scope 確保即使 token 洩漏，也只能存取 App 建立的檔案
- 所有 HTTP 通訊走 HTTPS

---

## 19. 遠端備份替代方案彙整（2026-04-30 補記）

### 19.1 緣由

第 1～18 節的 Google Drive OAuth 主方案以及曾評估過的 Gmail API（`gmail.send` scope）方案，對店家終端使用者而言仍顯複雜：

- 開發者必須事先在 Google Cloud Console 建立 OAuth Client、登錄 SHA-1、申請 Sensitive Scope 審查
- 使用者首次設定需要 Google Sign-In + 額外授權同意畫面
- 維運過程中可能遇到「未驗證的應用程式」警告、token 撤銷、GMS 缺失等問題

因此另外列出五個「設定一次就好、不需要 OAuth 審查」的替代路線，供未來決策時挑選。本節只做方案比較與骨架設計，**尚未排入實作排程**。

### 19.2 候選方案比較

| 編號 | 方案 | 使用者一次性設定 | 客戶端工程量 | 主要限制 |
|---|---|---|---|---|
| **A** | **Telegram Bot 推送** | BotFather 建 Bot 取 token → 加入群組或私聊取 chat_id | 極低（單一 HTTPS multipart POST 至 `sendDocument`） | 單檔 ≤ 50 MB；需有 Telegram 帳號 |
| **B** | **Discord Webhook** | Discord 頻道「整合 → Webhook」複製 URL | 極低（multipart POST 至 webhook URL） | 一般伺服器 8 MB、Nitro 25 MB |
| **C** | **第三方郵件 API（Resend / Brevo / Mailgun）** | 註冊帳號、貼一組 API key、驗證寄件網域或寄件信箱 | 低（單一 REST 呼叫，附件以 base64 編碼） | Resend 免費約 3,000 封/月；附件 ≤ 25–40 MB |
| **D** | **Google Apps Script Web App 中繼寄信** | 使用者部署 GAS Web App（以自身 Gmail 為寄件人），複製 URL | 低（POST zip 至 GAS URL） | 客戶端零 OAuth；單檔 ≤ 50 MB；Gmail 100 封/日 |
| **E** | **Google Drive 自動上傳**（即第 1～18 節主方案） | App 內 Google Sign-In，授權 `drive.file` 範圍 | 中（Drive REST + OAuth） | 需 OAuth 審查；不發信，僅同步至 Drive |

### 19.3 推薦組合

- **最省事** → **B（Discord Webhook）**：貼一個 URL 就完成，無帳號登入。適合老闆已有 Discord 群組。
- **想用 Email 收備份** → **C（Resend / Brevo）**：一組 API key 寄到任意信箱，不必動 Gmail OAuth。
- **想保留時間軸式雲端版本** → **E**：搭配 A/B 任一做即時通知最完整。
- **不想暴露任何金鑰、且已有 Google 帳號** → **D**：金鑰留在 GAS，App 端僅持有 Web App URL。

### 19.4 共通骨架（任選方案展開後一致）

1. **DataStore 新增鍵值**：在 [SettingsDataStore.kt](app/src/main/java/com/pos/app/data/datastore/SettingsDataStore.kt) 新增該方案專屬欄位
   - A：`telegram_bot_token`、`telegram_chat_id`
   - B：`discord_webhook_url`
   - C：`mail_api_provider`、`mail_api_key`、`mail_recipient`、`mail_sender`
   - D：`gas_webapp_url`、`gas_shared_secret`（可選，作為 `Authorization` header）
   - E：第 7 節既有設計
   - 共通：`remote_backup_enabled`、`remote_backup_last_at`、`remote_backup_last_failure_date`

2. **抽象介面**：建立 `app/src/main/java/com/pos/app/util/RemoteBackupSender.kt`
   ```kotlin
   interface RemoteBackupSender {
       val id: String                 // "telegram" / "discord" / "mail" / "gas" / "drive"
       suspend fun isConfigured(): Boolean
       suspend fun send(entry: BackupEntry): Result<Unit>
   }
   ```
   每個方案各自實作（`TelegramBackupSender`、`DiscordWebhookBackupSender`、`MailApiBackupSender`、`GasWebAppBackupSender`、`GoogleDriveBackupManager`）。

3. **AutoBackupManager 串接**：在 [AutoBackupManager.kt](app/src/main/java/com/pos/app/util/AutoBackupManager.kt) `performBackupInternal()` 寫檔成功後，迭代啟用中的 `RemoteBackupSender` 呼叫 `send(entry)`；錯誤吞掉只記 log 與 failure date，不影響本機備份。

4. **Settings UI**：於 [SettingsScreen.kt](app/src/main/java/com/pos/app/ui/settings/SettingsScreen.kt) 自動儲存區塊新增「遠端備份」群組
   - 啟用 Switch
   - 方案下拉選單（Telegram / Discord / Mail API / GAS / Drive）
   - 對應方案專屬欄位（token、URL、收件人…）以遮罩顯示且可清除
   - 「測試傳送」按鈕：送出 1 KB 假 zip 驗證設定
   - 顯示最近成功時間 / 失敗警示徽章

5. **同步文件**：完成實作時更新 [README.md](README.md)、[CLAUDE.md](CLAUDE.md)、[CHANGELOG.md](CHANGELOG.md)，遞增 [gradle.properties](gradle.properties) 的 `APP_VERSION_CODE` / `APP_VERSION_NAME`。

### 19.5 各方案 API 要點

#### 方案 A：Telegram Bot
- Endpoint：`POST https://api.telegram.org/bot<TOKEN>/sendDocument`
- Body：`multipart/form-data`，欄位 `chat_id` + `document`（zip）+ 可選 `caption`
- 限制：單檔 ≤ 50 MB；無需 OAuth；token 一旦洩漏必須在 BotFather 重新產生
- App 端僅需 OkHttp 或 `HttpURLConnection`

#### 方案 B：Discord Webhook
- Endpoint：`POST <webhook_url>` （URL 自帶 token）
- Body：`multipart/form-data`，`payload_json` + `files[0]`（zip）
- 限制：一般伺服器附件 8 MB、Nitro 25 MB；URL 即金鑰，需安全保管
- 適合「只想知道備份存在 + 偶爾下載查看」

#### 方案 C：第三方郵件 API（以 Resend 為例）
- Endpoint：`POST https://api.resend.com/emails`
- Header：`Authorization: Bearer <API_KEY>`
- Body：JSON，含 `from`、`to`、`subject`、`html`、`attachments[].filename` + `attachments[].content`（base64 zip）
- 寄件網域必須先驗證 SPF / DKIM；個人試用可直接以 Resend 提供的測試寄件網域
- Brevo / Mailgun 結構類似

#### 方案 D：Google Apps Script Web App
- 使用者於 [script.google.com](https://script.google.com) 部署一段 `doPost(e)` 程式：
  ```javascript
  function doPost(e) {
    var token = e.parameter.token;
    if (token !== 'SHARED_SECRET') return ContentService.createTextOutput('forbidden');
    var blob = Utilities.newBlob(Utilities.base64Decode(e.parameter.zip),
                                 'application/zip', e.parameter.name);
    GmailApp.sendEmail('owner@example.com', '[POS] 自動備份 ' + e.parameter.name,
                       '附件為今日備份', { attachments: [blob] });
    return ContentService.createTextOutput('ok');
  }
  ```
- 部署為「以我身份執行、任何人可存取」→ 取得 `https://script.google.com/macros/s/.../exec`
- App 端僅需 POST `token`、`name`、`zip`（base64）三個欄位
- 客戶端零 OAuth；金鑰留在 GAS 端，較安全

#### 方案 E：Google Drive
- 詳見第 1–18 節；本節不重複。

### 19.6 安全注意事項

- Bot token、Webhook URL、API key、GAS URL 皆等同「拿到即可濫用」的祕密，UI 必須以遮罩顯示、可清除
- DataStore 屬於 App 私有目錄，第三方 App 無法直接存取，但 root 過的裝置可能讀取；如有更高需求可改用 `EncryptedSharedPreferences`
- 所有 HTTP 通訊強制 HTTPS
- 建議於設定頁標示「請勿將上述金鑰外流」之提示

### 19.7 後續決策

待店家實際使用情境確認後，再從上述 5 個方案挑 1～2 個展開。決策關鍵：

1. 老闆習慣的查看通道（Email 收件匣 / IM 訊息 / 雲端硬碟）
2. 是否願意註冊第三方服務帳號
3. 預期備份檔大小（影響 Discord 8 MB 上限是否堪用）
4. 是否需要保留多日歷史版本（Drive / Email 搜尋 vs Telegram/Discord 訊息流）
