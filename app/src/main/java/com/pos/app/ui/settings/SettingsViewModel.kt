package com.pos.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            BackupManager.importZip(context, uri, appDatabase)
                .onSuccess { android.os.Process.killProcess(android.os.Process.myPid()) }
                .onFailure { e -> _uiState.update { it.copy(message = "還原失敗: ${e.message}") } }
        }
    }
}
