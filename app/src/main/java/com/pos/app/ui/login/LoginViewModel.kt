package com.pos.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val pin: String = "",
    val isError: Boolean = false,
    val failCount: Int = 0,
    val isLockedOut: Boolean = false,
    val lockoutSecondsLeft: Int = 0
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val pinHash: StateFlow<String> = settingsRepository.pinHash
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val isDefaultPin: StateFlow<Boolean> = settingsRepository.isDefaultPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var lockoutJob: Job? = null

    fun onDigitEntered(digit: String) {
        if (_uiState.value.isLockedOut) return
        val current = _uiState.value.pin
        if (current.length >= 4) return
        _uiState.update { it.copy(pin = current + digit, isError = false) }
    }

    fun onBackspace() {
        if (_uiState.value.isLockedOut) return
        val current = _uiState.value.pin
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(pin = current.dropLast(1), isError = false) }
        }
    }

    fun onClear() {
        _uiState.update { it.copy(pin = "", isError = false) }
    }

    fun verifyPin(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isLockedOut || state.pin.length < 4) return

        if (settingsRepository.verifyPin(state.pin, pinHash.value)) {
            _uiState.update { it.copy(pin = "", isError = false, failCount = 0) }
            onSuccess()
        } else {
            val newFailCount = state.failCount + 1
            if (newFailCount >= 3) {
                startLockout()
            } else {
                _uiState.update { it.copy(pin = "", isError = true, failCount = newFailCount) }
            }
        }
    }

    private fun startLockout() {
        _uiState.update { it.copy(pin = "", isError = false, isLockedOut = true, lockoutSecondsLeft = 30, failCount = 0) }
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            for (elapsed in 0 until 30) {
                delay(1000L)
                _uiState.update { it.copy(lockoutSecondsLeft = 30 - elapsed - 1) }
            }
            _uiState.update { it.copy(isLockedOut = false, lockoutSecondsLeft = 0) }
        }
    }
}
