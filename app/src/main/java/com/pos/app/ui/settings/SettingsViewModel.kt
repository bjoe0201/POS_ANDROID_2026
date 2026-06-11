package com.pos.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.pos.app.data.db.AppDatabase
import com.pos.app.data.repository.SettingsRepository
import com.pos.app.util.AutoBackupManager
import com.pos.app.util.BackupEntry
import com.pos.app.util.BackupManager
import com.pos.app.util.PrinterDevice
import com.pos.app.util.PrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val isDefaultPin: Boolean = true,
    val tabMenuEnabled: Boolean = true,
    val tabTableEnabled: Boolean = true,
    val tabReportEnabled: Boolean = true,
    val tabReservationEnabled: Boolean = true,
    val bizStart: String = "11:00",
    val bizEnd: String = "22:00",
    val breakStart: String = "",
    val breakEnd: String = "",
    val defaultDuration: Int = 90,
    val calendarChipsPerRow: Int = 2,
    val autoBackupEnabled: Boolean = true,
    val autoBackupIdleMinutes: Int = 5,
    val autoBackupRetentionDays: Int = 3,
    val autoBackupLastAt: Long? = null,
    val autoBackupFiles: List<BackupEntry> = emptyList(),
    val autoBackupStorageDesc: String = "下載／火鍋店POS備份",
    val autoBackupUsingCustom: Boolean = false,
    val cloudBackupEnabled: Boolean = false,
    val cloudBackupStorageDesc: String = "",
    val qtyRepeatIntervalMs: Int = 100,
    val qtyRepeatInitialDelayMs: Int = 1000,
    val hapticEnabled: Boolean = true,
    val printerTestPassed: Boolean = false,
    val printCheckoutEnabled: Boolean = false,
    val printDetailEnabled: Boolean = false,
    val pdfPrinterEnabled: Boolean = false,
    val pdfPrinterTreeUri: String = "",
    // ── 印表機偵測 ──
    val selectedPrinterType: String = "",
    val selectedPrinterId: String = "",
    val selectedPrinterName: String = "",
    val selectedPrinterError: String? = null,
    val scannedDevices: List<PrinterDevice> = emptyList(),
    val isScanning: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appDatabase: AppDatabase,
    private val autoBackupManager: AutoBackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val pinHash: StateFlow<String> = settingsRepository.pinHash
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        settingsRepository.isDefaultPin
            .onEach { isDefault -> _uiState.update { it.copy(isDefaultPin = isDefault) } }
            .launchIn(viewModelScope)
        settingsRepository.tabMenuEnabled
            .onEach { v -> _uiState.update { it.copy(tabMenuEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.tabTableEnabled
            .onEach { v -> _uiState.update { it.copy(tabTableEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.tabReportEnabled
            .onEach { v -> _uiState.update { it.copy(tabReportEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.tabReservationEnabled
            .onEach { v -> _uiState.update { it.copy(tabReservationEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.bizStart
            .onEach { v -> _uiState.update { it.copy(bizStart = v) } }
            .launchIn(viewModelScope)
        settingsRepository.bizEnd
            .onEach { v -> _uiState.update { it.copy(bizEnd = v) } }
            .launchIn(viewModelScope)
        settingsRepository.breakStart
            .onEach { v -> _uiState.update { it.copy(breakStart = v) } }
            .launchIn(viewModelScope)
        settingsRepository.breakEnd
            .onEach { v -> _uiState.update { it.copy(breakEnd = v) } }
            .launchIn(viewModelScope)
        settingsRepository.defaultDuration
            .onEach { v -> _uiState.update { it.copy(defaultDuration = v) } }
            .launchIn(viewModelScope)
        settingsRepository.calendarChipsPerRow
            .onEach { v -> _uiState.update { it.copy(calendarChipsPerRow = v) } }
            .launchIn(viewModelScope)
        settingsRepository.autoBackupEnabled
            .onEach { v -> _uiState.update { it.copy(autoBackupEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.autoBackupIdleMinutes
            .onEach { v -> _uiState.update { it.copy(autoBackupIdleMinutes = v) } }
            .launchIn(viewModelScope)
        settingsRepository.autoBackupRetentionDays
            .onEach { v -> _uiState.update { it.copy(autoBackupRetentionDays = v) } }
            .launchIn(viewModelScope)
        settingsRepository.autoBackupExternalTreeUri
            .onEach { uri ->
                _uiState.update {
                    it.copy(
                        autoBackupUsingCustom = uri.isNotBlank(),
                        autoBackupStorageDesc = autoBackupManager.storageDescription(),
                        autoBackupFiles = loadAutoBackupFiles()
                    )
                }
            }
            .launchIn(viewModelScope)
        settingsRepository.cloudBackupEnabled
            .onEach { v -> _uiState.update { it.copy(cloudBackupEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.cloudBackupTreeUri
            .onEach { uri ->
                _uiState.update {
                    it.copy(cloudBackupStorageDesc = if (uri.isNotBlank()) autoBackupManager.cloudStorageDescription() else "")
                }
            }
            .launchIn(viewModelScope)
        autoBackupManager.lastBackupAt
            .onEach { t -> _uiState.update { it.copy(autoBackupLastAt = t, autoBackupFiles = loadAutoBackupFiles()) } }
            .launchIn(viewModelScope)
        // 每次備份成功都強制刷新一次（就算秒級時間戳沒變也要更新）
        autoBackupManager.backupTick
            .onEach {
                _uiState.update {
                    it.copy(
                        autoBackupLastAt = autoBackupManager.lastBackupAt.value,
                        autoBackupFiles = loadAutoBackupFiles(),
                        autoBackupStorageDesc = autoBackupManager.storageDescription()
                    )
                }
            }
            .launchIn(viewModelScope)
        settingsRepository.qtyRepeatIntervalMs
            .onEach { v -> _uiState.update { it.copy(qtyRepeatIntervalMs = v) } }
            .launchIn(viewModelScope)
        settingsRepository.qtyRepeatInitialDelayMs
            .onEach { v -> _uiState.update { it.copy(qtyRepeatInitialDelayMs = v) } }
            .launchIn(viewModelScope)
        settingsRepository.hapticEnabled
            .onEach { v -> _uiState.update { it.copy(hapticEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.printerTestPassed
            .onEach { v -> _uiState.update { it.copy(printerTestPassed = v) } }
            .launchIn(viewModelScope)
        settingsRepository.printCheckoutEnabled
            .onEach { v -> _uiState.update { it.copy(printCheckoutEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.printDetailEnabled
            .onEach { v -> _uiState.update { it.copy(printDetailEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.pdfPrinterEnabled
            .onEach { v -> _uiState.update { it.copy(pdfPrinterEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.pdfPrinterTreeUri
            .onEach { v -> _uiState.update { it.copy(pdfPrinterTreeUri = v) } }
            .launchIn(viewModelScope)
        settingsRepository.selectedPrinterType
            .onEach { v -> _uiState.update { it.copy(selectedPrinterType = v) } }
            .launchIn(viewModelScope)
        settingsRepository.selectedPrinterId
            .onEach { v -> _uiState.update { it.copy(selectedPrinterId = v) } }
            .launchIn(viewModelScope)
    }

    private fun loadAutoBackupFiles(): List<BackupEntry> = autoBackupManager.listBackups()

    fun refreshAutoBackupFiles() {
        val files = loadAutoBackupFiles()
        _uiState.update {
            it.copy(
                autoBackupFiles = files,
                autoBackupLastAt = files.firstOrNull()?.lastModified ?: it.autoBackupLastAt,
                autoBackupStorageDesc = autoBackupManager.storageDescription()
            )
        }
    }

    fun setAutoBackupEnabled(v: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoBackupEnabled(v) }
    }

    fun setAutoBackupIdleMinutes(v: Int) {
        viewModelScope.launch { settingsRepository.setAutoBackupIdleMinutes(v.coerceAtLeast(1)) }
    }

    fun setAutoBackupRetentionDays(v: Int) {
        viewModelScope.launch { settingsRepository.setAutoBackupRetentionDays(v.coerceAtLeast(1)) }
    }

    /** 使用者透過 SAF 選到新的資料夾 URI，需已呼叫 takePersistableUriPermission。 */
    fun setAutoBackupExternalTreeUri(uri: String) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupExternalTreeUri(uri)
            _uiState.update { it.copy(message = "已切換備份資料夾") }
        }
    }

    fun clearAutoBackupExternalTreeUri() {
        viewModelScope.launch {
            settingsRepository.setAutoBackupExternalTreeUri("")
            _uiState.update { it.copy(message = "已改回預設下載目錄") }
        }
    }

    fun setCloudBackupEnabled(v: Boolean) {
        viewModelScope.launch { settingsRepository.setCloudBackupEnabled(v) }
    }

    fun setCloudBackupTreeUri(uri: String) {
        viewModelScope.launch {
            settingsRepository.setCloudBackupTreeUri(uri)
            _uiState.update { it.copy(message = "已設定雲端備份資料夾") }
        }
    }

    fun clearCloudBackupTreeUri() {
        viewModelScope.launch {
            settingsRepository.setCloudBackupTreeUri("")
            _uiState.update { it.copy(message = "已移除雲端備份資料夾") }
        }
    }

    fun backupNow() {
        viewModelScope.launch(Dispatchers.IO) {
            autoBackupManager.backupNow()
                .onSuccess { entry ->
                    _uiState.update {
                        it.copy(
                            message = "已建立備份：${entry.name}",
                            autoBackupFiles = loadAutoBackupFiles()
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(message = "自動備份失敗：${e.message}") } }
        }
    }

    fun restoreFromAutoBackup(context: Context, entry: BackupEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            BackupManager.importZip(context, entry.uri, appDatabase)
                .onSuccess { android.os.Process.killProcess(android.os.Process.myPid()) }
                .onFailure { e -> _uiState.update { it.copy(message = "還原失敗：${e.message}") } }
        }
    }

    fun deleteAutoBackup(entry: BackupEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            autoBackupManager.deleteBackup(entry)
            _uiState.update { it.copy(autoBackupFiles = loadAutoBackupFiles()) }
        }
    }

    fun setTabMenuEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTabMenuEnabled(enabled) }
    }

    fun setTabTableEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTabTableEnabled(enabled) }
    }

    fun setTabReportEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTabReportEnabled(enabled) }
    }

    fun setTabReservationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTabReservationEnabled(enabled) }
    }

    fun setBizStart(v: String)     { viewModelScope.launch { settingsRepository.setBizStart(v) } }
    fun setBizEnd(v: String)       { viewModelScope.launch { settingsRepository.setBizEnd(v) } }
    fun setBreakStart(v: String)   { viewModelScope.launch { settingsRepository.setBreakStart(v) } }
    fun setBreakEnd(v: String)     { viewModelScope.launch { settingsRepository.setBreakEnd(v) } }
    fun setDefaultDuration(v: Int) { viewModelScope.launch { settingsRepository.setDefaultDuration(v) } }
    fun setCalendarChipsPerRow(v: Int) { viewModelScope.launch { settingsRepository.setCalendarChipsPerRow(v) } }

    fun setQtyRepeatIntervalMs(v: Int) {
        viewModelScope.launch { settingsRepository.setQtyRepeatIntervalMs(v.coerceIn(30, 500)) }
    }

    fun setQtyRepeatInitialDelayMs(v: Int) {
        viewModelScope.launch { settingsRepository.setQtyRepeatInitialDelayMs(v.coerceIn(300, 2000)) }
    }

    fun setHapticEnabled(v: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticEnabled(v) }
    }

    fun setPrinterTestPassed(v: Boolean) {
        viewModelScope.launch { settingsRepository.setPrinterTestPassed(v) }
    }

    fun setPrintCheckoutEnabled(v: Boolean) {
        viewModelScope.launch { settingsRepository.setPrintCheckoutEnabled(v) }
    }

    fun setPrintDetailEnabled(v: Boolean) {
        viewModelScope.launch { settingsRepository.setPrintDetailEnabled(v) }
    }

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

    // ── 印表機偵測 ──────────────────────────────────────────────────────────

    /** 掃描可用的 USB + 已配對藍芽裝置。 */
    fun scanPrinters(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scannedDevices = emptyList()) }
            val devices = withContext(Dispatchers.IO) {
                PrinterManager.scanPrinters(context)
            }
            _uiState.update { it.copy(isScanning = false, scannedDevices = devices) }
        }
    }

    /** 使用者選定一台印表機，儲存至 DataStore 並重置測試狀態。 */
    fun selectPrinter(printer: PrinterDevice) {
        viewModelScope.launch {
            settingsRepository.setSelectedPrinter(printer.typeKey, printer.identifier)
            settingsRepository.setPrinterTestPassed(false)
            _uiState.update {
                it.copy(
                    selectedPrinterType  = printer.typeKey,
                    selectedPrinterId    = printer.identifier,
                    selectedPrinterName  = printer.displayName,
                    selectedPrinterError = null,
                    printerTestPassed    = false,
                    scannedDevices       = emptyList(),
                )
            }
        }
    }

    /** 清除已選定的印表機。 */
    fun clearSelectedPrinter() {
        viewModelScope.launch {
            settingsRepository.clearSelectedPrinter()
            settingsRepository.setPrinterTestPassed(false)
            _uiState.update {
                it.copy(
                    selectedPrinterType  = "",
                    selectedPrinterId    = "",
                    selectedPrinterName  = "",
                    selectedPrinterError = null,
                    printerTestPassed    = false,
                )
            }
        }
    }

    /**
     * 嘗試解析已儲存的印表機；若裝置已消失則設定錯誤訊息。
     * 在 PrinterSection 首次載入時由 UI 呼叫。
     */
    fun validateSavedPrinter(context: Context) {
        val type = _uiState.value.selectedPrinterType
        val id   = _uiState.value.selectedPrinterId
        if (type.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val device = PrinterManager.resolveDevice(context, type, id)
            if (device == null) {
                _uiState.update {
                    it.copy(selectedPrinterError = "上次選擇的印表機已無法使用，請重新偵測")
                }
            } else {
                _uiState.update {
                    it.copy(selectedPrinterName = device.displayName, selectedPrinterError = null)
                }
            }
        }
    }

    fun changePin(currentPin: String, newPin: String, confirmPin: String, onResult: (Boolean, String) -> Unit) {
        val storedHash = pinHash.value
        when {
            !settingsRepository.verifyPin(currentPin, storedHash) ->
                onResult(false, "目前 PIN 碼錯誤")
            newPin.length != 4 ->
                onResult(false, "新 PIN 碼需為 4 位數字")
            newPin != confirmPin ->
                onResult(false, "兩次輸入不一致")
            else -> viewModelScope.launch {
                settingsRepository.setPin(newPin)
                onResult(true, "PIN 碼已更新")
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun backupDb(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            BackupManager.exportZip(context, uri, appDatabase)
                .onSuccess { _uiState.update { it.copy(message = "備份成功") } }
                .onFailure { e -> _uiState.update { it.copy(message = "備份失敗: ${e.message}") } }
        }
    }

    fun restoreDb(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            // Step 6：匯入前自動在私有目錄建立安全備份
            BackupManager.autoBackupBeforeImport(context, appDatabase)
            BackupManager.importZip(context, uri, appDatabase)
                .onSuccess { android.os.Process.killProcess(android.os.Process.myPid()) }
                .onFailure { e -> _uiState.update { it.copy(message = "還原失敗: ${e.message}（安全備份已保留於裝置私有目錄）") } }
        }
    }

    fun resetDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appDatabase.withTransaction {
                    appDatabase.orderItemDao().deleteAll()
                    appDatabase.orderDao().deleteAll()
                    appDatabase.menuItemDao().deleteAll()
                    appDatabase.menuGroupDao().deleteAll()
                    appDatabase.tableDao().deleteAll()
                }
                AppDatabase.seedDefaults(appDatabase)
                _uiState.update { it.copy(message = "資料庫已初始化完成") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "初始化失敗: ${e.message}") }
            }
        }
    }
}
