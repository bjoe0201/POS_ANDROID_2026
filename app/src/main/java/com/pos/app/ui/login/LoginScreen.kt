package com.pos.app.ui.login

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.ui.theme.Red700

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDefaultPin by viewModel.isDefaultPin.collectAsState()

    LaunchedEffect(uiState.pin) {
        if (uiState.pin.length == 4) {
            viewModel.verifyPin(onLoginSuccess)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "POS 餐飲系統",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Red700
            )

            Text(
                text = "請輸入 PIN 碼",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { index ->
                    val filled = index < uiState.pin.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    uiState.isError -> MaterialTheme.colorScheme.error
                                    filled -> Red700
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                }
            }

            when {
                uiState.isLockedOut -> Text(
                    "輸入錯誤次數過多，請等待 ${uiState.lockoutSecondsLeft} 秒",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
                uiState.isError -> Text(
                    "PIN 碼錯誤，還剩 ${3 - uiState.failCount} 次機會",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            if (isDefaultPin && !uiState.isError && !uiState.isLockedOut) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "目前使用預設密碼 1234，請至設定修改",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // PIN pad
            PinPad(
                onDigit = { viewModel.onDigitEntered(it) },
                onBackspace = { viewModel.onBackspace() },
                onClear = { viewModel.onClear() }
            )
        }
    }
}

@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    PinButton(
                        label = key,
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "C"  -> onClear()
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PinButton(label: String, onClick: () -> Unit) {
    val isSpecial = label == "C" || label == "⌫"
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSpecial)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        if (label == "⌫") {
            Icon(Icons.Default.Backspace, contentDescription = "退格", tint = MaterialTheme.colorScheme.error)
        } else {
            Text(
                text = label,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSpecial) MaterialTheme.colorScheme.error else Red700
            )
        }
    }
}
