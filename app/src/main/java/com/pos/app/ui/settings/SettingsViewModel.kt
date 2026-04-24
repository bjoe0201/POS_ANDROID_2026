package com.pos.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.pos.app.data.db.AppDatabase
import com.pos.app.data.repository.SettingsRepository
import com.pos.app.util.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appDatabase: AppDatabase
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
            BackupManager.importZip(context, uri, appDatabase)
                .onSuccess { android.os.Process.killProcess(android.os.Process.myPid()) }
                .onFailure { e -> _uiState.update { it.copy(message = "還原失敗: ${e.message}") } }
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
