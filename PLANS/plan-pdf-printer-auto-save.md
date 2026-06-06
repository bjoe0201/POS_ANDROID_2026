# PDF列印機(自動存放) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In Settings → 印表機, add a "PDF列印機(自動存放)" switch and a "PDF存檔目錄" folder picker; when enabled, every successful USB report print auto-saves a PDF copy to the SAF-selected folder.

**Architecture:** Add two DataStore keys (`pdf_printer_enabled`, `pdf_printer_tree_uri`) wired through Repository → SettingsViewModel → SettingsUiState; extend `ReportPdfBuilder` with a `buildToTreeUri()` helper that uses `DocumentFile` (already a dependency) to create a timestamped file in the SAF tree and delegate to the existing `build()`; `ReportViewModel` (which already injects `SettingsRepository`) reads the new settings and after a successful USB print calls `buildToTreeUri()` as a best-effort side-effect; `PrinterSection` in `SettingsScreen.kt` gains an `OpenDocumentTree` SAF launcher plus the new switch and folder-picker sub-card, following the same pattern as the cloud-backup folder picker.

**Tech Stack:** Jetpack DataStore Preferences, SAF `ActivityResultContracts.OpenDocumentTree`, `androidx.documentfile:documentfile:1.0.1` (already in `app/build.gradle.kts:94`), `android.graphics.pdf.PdfDocument` (already used in `ReportPdfBuilder`), Hilt DI, Kotlin Coroutines.

---

## File Structure

| Action | File | What changes |
|--------|------|-------------|
| Modify | `app/src/main/java/com/pos/app/data/datastore/SettingsDataStore.kt` | 2 new preference keys, 2 flows, 2 setters |
| Modify | `app/src/main/java/com/pos/app/data/repository/SettingsRepository.kt` | 2 new flow delegates, 2 setters |
| Modify | `app/src/main/java/com/pos/app/ui/settings/SettingsViewModel.kt` | 2 fields in `SettingsUiState`, init collection, 3 setter methods |
| Modify | `app/src/main/java/com/pos/app/util/ReportPdfBuilder.kt` | New `buildToTreeUri()` method + `DocumentFile` import |
| Modify | `app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt` | 2 fields in `ReportUiState`, init collection, updated `printCurrentReport` |
| Modify | `app/src/main/java/com/pos/app/ui/settings/SettingsScreen.kt` | `PrinterSection` signature, SAF launcher, new switch + sub-card UI |
| Modify | `gradle.properties` | Version bump |
| Modify | `CLAUDE.md` | Document 2 new DataStore keys |

---

### Task 1: DataStore keys → Repository → SettingsViewModel state

**Files:**
- Modify: `app/src/main/java/com/pos/app/data/datastore/SettingsDataStore.kt`
- Modify: `app/src/main/java/com/pos/app/data/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/pos/app/ui/settings/SettingsViewModel.kt`

**Context:** Existing printer keys in `SettingsDataStore` companion object end at line 43 (`PRINT_DETAIL_ENABLED`). Flows end at line 144, setters at line 148. `SettingsRepository` delegates follow the same order: flows at lines 31-33, setters at lines 56-58. `SettingsUiState` is at lines 19-47; `printDetailEnabled` is at line 45. In `SettingsViewModel.init` the `printDetailEnabled` collector ends at line 158. Setter `setPrintDetailEnabled` ends at line 294 (closing brace).

- [ ] **Step 1: Add 2 keys, 2 flows, and 2 setters to `SettingsDataStore.kt`**

In the `companion object`, after line 43 (`private val PRINT_DETAIL_ENABLED = ...`), add:

```kotlin
        private val PDF_PRINTER_ENABLED  = booleanPreferencesKey("pdf_printer_enabled")
        private val PDF_PRINTER_TREE_URI = stringPreferencesKey("pdf_printer_tree_uri")
```

After line 144 (`val printDetailEnabled: Flow<Boolean> = ...`), add:

```kotlin
    val pdfPrinterEnabled: Flow<Boolean> = context.dataStore.data.map { it[PDF_PRINTER_ENABLED]  ?: false }
    val pdfPrinterTreeUri: Flow<String>  = context.dataStore.data.map { it[PDF_PRINTER_TREE_URI] ?: "" }
```

After line 148 (`suspend fun setPrintDetailEnabled(v: Boolean)`), add:

```kotlin
    suspend fun setPdfPrinterEnabled(v: Boolean) { context.dataStore.edit { it[PDF_PRINTER_ENABLED]  = v } }
    suspend fun setPdfPrinterTreeUri(v: String)  { context.dataStore.edit { it[PDF_PRINTER_TREE_URI] = v } }
```

- [ ] **Step 2: Wire through `SettingsRepository.kt`**

After line 33 (`val printDetailEnabled: Flow<Boolean> = dataStore.printDetailEnabled`), add:

```kotlin
    val pdfPrinterEnabled: Flow<Boolean> = dataStore.pdfPrinterEnabled
    val pdfPrinterTreeUri: Flow<String>  = dataStore.pdfPrinterTreeUri
```

After line 58 (`suspend fun setPrintDetailEnabled(v: Boolean) = dataStore.setPrintDetailEnabled(v)`), add:

```kotlin
    suspend fun setPdfPrinterEnabled(v: Boolean) = dataStore.setPdfPrinterEnabled(v)
    suspend fun setPdfPrinterTreeUri(v: String)  = dataStore.setPdfPrinterTreeUri(v)
```

- [ ] **Step 3: Add 2 fields to `SettingsUiState` in `SettingsViewModel.kt`**

After line 45 (`val printDetailEnabled: Boolean = false,`), add:

```kotlin
    val pdfPrinterEnabled: Boolean = false,
    val pdfPrinterTreeUri: String = "",
```

- [ ] **Step 4: Collect both new flows in `SettingsViewModel.init`**

After line 158 (`.launchIn(viewModelScope)` that closes the `printDetailEnabled` collection block), add:

```kotlin
        settingsRepository.pdfPrinterEnabled
            .onEach { v -> _uiState.update { it.copy(pdfPrinterEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.pdfPrinterTreeUri
            .onEach { v -> _uiState.update { it.copy(pdfPrinterTreeUri = v) } }
            .launchIn(viewModelScope)
```

- [ ] **Step 5: Add 3 setter methods to `SettingsViewModel`**

After line 294 (closing `}` of `fun setPrintDetailEnabled`), add:

```kotlin
    fun setPdfPrinterEnabled(v: Boolean) {
        viewModelScope.launch { settingsRepository.setPdfPrinterEnabled(v) }
    }

    fun setPdfPrinterTreeUri(uri: String) {
        viewModelScope.launch {
            settingsRepository.setPdfPrinterTreeUri(uri)
            _uiState.update { it.copy(message = "已設定 PDF 存檔目錄") }
        }
    }

    fun clearPdfPrinterTreeUri() {
        viewModelScope.launch {
            settingsRepository.setPdfPrinterTreeUri("")
            _uiState.update { it.copy(message = "已移除 PDF 存檔目錄") }
        }
    }
```

- [ ] **Step 6: Verify build**

Run (Windows): `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pos/app/data/datastore/SettingsDataStore.kt \
        app/src/main/java/com/pos/app/data/repository/SettingsRepository.kt \
        app/src/main/java/com/pos/app/ui/settings/SettingsViewModel.kt
git commit -m "feat: add pdf_printer_enabled/tree_uri DataStore keys + SettingsViewModel wiring"
```

---

### Task 2: ReportPdfBuilder.buildToTreeUri

**Files:**
- Modify: `app/src/main/java/com/pos/app/util/ReportPdfBuilder.kt`

**Context:** `ReportPdfBuilder` is an `object` at `com.pos.app.util`. The `build()` suspend function (line 32) writes a PDF to an explicit `Uri` using `context.contentResolver.openOutputStream(uri)`. The new `buildToTreeUri()` uses `DocumentFile.fromTreeUri(context, treeUri).createFile("application/pdf", "report-$ts")` to allocate a new file URI inside the SAF folder, then delegates to `build()`. `androidx.documentfile:documentfile:1.0.1` is already in `app/build.gradle.kts:94`. Existing imports already include `android.content.Context`, `android.net.Uri`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`, `java.text.SimpleDateFormat`, `java.util.Date`, `java.util.Locale`.

- [ ] **Step 1: Add `DocumentFile` import to `ReportPdfBuilder.kt`**

After `import android.net.Uri` (line 8), add:

```kotlin
import androidx.documentfile.provider.DocumentFile
```

- [ ] **Step 2: Add `buildToTreeUri()` method after `build()` (after line 106)**

```kotlin
    /**
     * 將報表 PDF 自動存入 [treeUri] 所代表的 SAF 資料夾。
     * 以當下時間戳命名，例如 report-20260606-183000.pdf。
     * 內部建立 [DocumentFile] 後委派給 [build]。
     */
    suspend fun buildToTreeUri(
        context: Context,
        treeUri: Uri,
        state: ReportUiState,
        includeOrderDetails: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("無法開啟 PDF 存檔目錄")
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val file = dir.createFile("application/pdf", "report-$ts")
                ?: error("無法在目錄中建立 PDF 檔案")
            build(context, file.uri, state, includeOrderDetails).getOrThrow()
        }
    }
```

- [ ] **Step 3: Verify build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pos/app/util/ReportPdfBuilder.kt
git commit -m "feat: ReportPdfBuilder.buildToTreeUri for SAF folder auto-save"
```

---

### Task 3: ReportViewModel PDF auto-save after USB print

**Files:**
- Modify: `app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt`

**Context:** `ReportUiState` is at lines 35-52; `openOrders` is the last field at line 51. `ReportViewModel` already injects `SettingsRepository` (line 57) and collects `printDetailEnabled` from it (lines 74-76). `printCurrentReport` starts at line 230; after the `UsbPrinterManager.printReport()` call (line 245) the result update block is lines 246-255. `android.net.Uri` is already imported (line 4). `ReportPdfBuilder` is already imported (line 12).

- [ ] **Step 1: Add 2 fields to `ReportUiState`**

After line 51 (`val openOrders: List<OrderEntity> = emptyList()`), add:

```kotlin
    val pdfPrinterEnabled: Boolean = false,
    val pdfPrinterTreeUri: String = "",
```

- [ ] **Step 2: Collect both new settings flows in `ReportViewModel.init`**

After lines 74-76 (the `settingsRepository.printDetailEnabled` block ending with `.launchIn(viewModelScope)`), add:

```kotlin
        settingsRepository.pdfPrinterEnabled
            .onEach { v -> _uiState.update { it.copy(pdfPrinterEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.pdfPrinterTreeUri
            .onEach { v -> _uiState.update { it.copy(pdfPrinterTreeUri = v) } }
            .launchIn(viewModelScope)
```

- [ ] **Step 3: Replace the final `_uiState.update` in `printCurrentReport` (lines 246-255)**

Replace:

```kotlin
            _uiState.update {
                it.copy(
                    isPrintingReport = false,
                    message = if (result.isSuccess) {
                        "報表已送出列印"
                    } else {
                        "報表列印失敗：${result.exceptionOrNull()?.message ?: "未知錯誤"}"
                    }
                )
            }
```

With:

```kotlin
            val usbMsg = if (result.isSuccess) "報表已送出列印"
                         else "報表列印失敗：${result.exceptionOrNull()?.message ?: "未知錯誤"}"
            val message = if (result.isSuccess
                && state.pdfPrinterEnabled
                && state.pdfPrinterTreeUri.isNotBlank()
            ) {
                val pdfResult = ReportPdfBuilder.buildToTreeUri(
                    context, Uri.parse(state.pdfPrinterTreeUri), state, includeOrderDetails
                )
                if (pdfResult.isSuccess) "$usbMsg，PDF 已自動存檔"
                else "$usbMsg（PDF 存檔失敗：${pdfResult.exceptionOrNull()?.message ?: "未知錯誤"}）"
            } else {
                usbMsg
            }
            _uiState.update { it.copy(isPrintingReport = false, message = message) }
```

- [ ] **Step 4: Verify build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pos/app/ui/report/ReportViewModel.kt
git commit -m "feat: auto-save PDF after USB report print when pdf_printer_enabled"
```

---

### Task 4: SettingsScreen PrinterSection UI

**Files:**
- Modify: `app/src/main/java/com/pos/app/ui/settings/SettingsScreen.kt`

**Context:** The `PrinterSection` composable is declared at line 544 (private fun). Its call site is lines 376-383. Inside `PrinterSection`, `val context = LocalContext.current` is at line 552 and `val scope = rememberCoroutineScope()` is at line 553. The `if (printerTestPassed)` block contains the existing two Switch rows; the 明細列印 `Row` ends at line 672, then line 673 closes `if (printerTestPassed)`, line 674 closes `SectionCard { }`, line 675 closes `PrinterSection`. The SAF folder-picker pattern (with `takePersistableUriPermission`) is used for cloud backup at lines 896-910 — follow that pattern exactly. `DocumentFile.fromTreeUri` used in `remember` block to show the folder display name. `Arrangement.spacedBy`, `OutlinedButton`, `BorderStroke`, `RoundedCornerShape`, `Column`, `Row`, `Spacer`, `Switch`, `SwitchDefaults`, `Text`, `Modifier.clip`, `Modifier.border`, `Modifier.background`, `Modifier.padding` are all already used in this file.

- [ ] **Step 1: Update `PrinterSection` function signature (line 544)**

Change:

```kotlin
private fun PrinterSection(
    t: PosColors,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel,
    printerTestPassed: Boolean,
    printCheckoutEnabled: Boolean,
    printDetailEnabled: Boolean
) {
```

To:

```kotlin
private fun PrinterSection(
    t: PosColors,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel,
    printerTestPassed: Boolean,
    printCheckoutEnabled: Boolean,
    printDetailEnabled: Boolean,
    pdfPrinterEnabled: Boolean,
    pdfPrinterTreeUri: String
) {
```

- [ ] **Step 2: Add `pickPdfFolderLauncher` inside `PrinterSection` body**

After `val scope = rememberCoroutineScope()` (line 553), add:

```kotlin
    val pickPdfFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.setPdfPrinterTreeUri(uri.toString())
        }
    }
```

- [ ] **Step 3: Add PDF switch + folder picker sub-card inside `if (printerTestPassed)`**

After the closing `}` of the 明細列印 `Row` (line 672), before the closing `}` of `if (printerTestPassed)` (line 673), insert:

```kotlin
            Spacer(Modifier.height(10.dp))

            // PDF 列印機（自動存放）
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("PDF列印機(自動存放)", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("列印時自動儲存一份 PDF 至指定資料夾", color = t.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = pdfPrinterEnabled,
                    onCheckedChange = { viewModel.setPdfPrinterEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = t.accent, checkedTrackColor = t.accentDim2,
                        uncheckedThumbColor = t.textMuted, uncheckedTrackColor = t.border
                    )
                )
            }

            if (pdfPrinterEnabled) {
                Spacer(Modifier.height(8.dp))
                val folderDesc = remember(pdfPrinterTreeUri) {
                    if (pdfPrinterTreeUri.isBlank()) ""
                    else runCatching {
                        androidx.documentfile.provider.DocumentFile
                            .fromTreeUri(context, android.net.Uri.parse(pdfPrinterTreeUri))?.name
                            ?: pdfPrinterTreeUri
                    }.getOrDefault(pdfPrinterTreeUri)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(t.surface)
                        .border(1.dp, t.border, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("PDF 存檔目錄", color = t.textMuted, fontSize = 12.sp)
                    if (folderDesc.isNotBlank()) {
                        Text(folderDesc, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text("尚未設定", color = t.textMuted, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pickPdfFolderLauncher.launch(null) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, t.accent),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("選擇資料夾", color = t.accent, fontSize = 12.sp) }
                        if (folderDesc.isNotBlank()) {
                            OutlinedButton(
                                onClick = { viewModel.clearPdfPrinterTreeUri() },
                                border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("移除", color = t.textSub, fontSize = 12.sp) }
                        }
                    }
                }
            }
```

- [ ] **Step 4: Update the `PrinterSection` call site (lines 376-383)**

Change:

```kotlin
                PrinterSection(
                    t = t,
                    snackbarHostState = snackbarHostState,
                    viewModel = viewModel,
                    printerTestPassed = uiState.printerTestPassed,
                    printCheckoutEnabled = uiState.printCheckoutEnabled,
                    printDetailEnabled = uiState.printDetailEnabled
                )
```

To:

```kotlin
                PrinterSection(
                    t = t,
                    snackbarHostState = snackbarHostState,
                    viewModel = viewModel,
                    printerTestPassed = uiState.printerTestPassed,
                    printCheckoutEnabled = uiState.printCheckoutEnabled,
                    printDetailEnabled = uiState.printDetailEnabled,
                    pdfPrinterEnabled = uiState.pdfPrinterEnabled,
                    pdfPrinterTreeUri = uiState.pdfPrinterTreeUri
                )
```

- [ ] **Step 5: Verify build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pos/app/ui/settings/SettingsScreen.kt
git commit -m "feat: PrinterSection PDF列印機 switch + PDF存檔目錄 folder picker"
```

---

### Task 5: Version bump + CLAUDE.md update

**Files:**
- Modify: `gradle.properties`
- Modify: `CLAUDE.md`

**Context:** Current version in `gradle.properties` is `APP_VERSION_CODE=21`, `APP_VERSION_NAME=1.2.13`. The DataStore keys bullet is at `CLAUDE.md:236`; it currently ends with `CLOUD_BACKUP_TREE_URI`（雲端備份 SAF tree URI）。`.

- [ ] **Step 1: Bump version in `gradle.properties`**

Change:
```
APP_VERSION_CODE=21
APP_VERSION_NAME=1.2.13
```
To:
```
APP_VERSION_CODE=22
APP_VERSION_NAME=1.2.14
```

- [ ] **Step 2: Append 2 new keys to the DataStore keys bullet in `CLAUDE.md` (line 236)**

At the end of the `- **DataStore keys**` bullet (currently ending with `CLOUD_BACKUP_TREE_URI`（雲端備份 SAF tree URI）。`), replace the trailing `。` with:

```
、`PDF_PRINTER_ENABLED`（PDF列印機自動存放開關，預設關閉）、`PDF_PRINTER_TREE_URI`（PDF 自動存檔目錄 SAF tree URI）。
```

- [ ] **Step 3: Verify build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add gradle.properties CLAUDE.md
git commit -m "chore: bump version to v1.2.14 (versionCode 22); document PDF_PRINTER_ENABLED/TREE_URI keys"
```
