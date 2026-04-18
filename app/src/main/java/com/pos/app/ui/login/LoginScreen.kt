package com.pos.app.ui.login

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.R
import com.pos.app.ui.theme.Red700

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDefaultPin by viewModel.isDefaultPin.collectAsState()
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull() ?: "N/A"
    }

    LaunchedEffect(uiState.pin) {
        if (uiState.pin.length == 4) {
            viewModel.verifyPin(onLoginSuccess)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val pinPadAreaHeight = (maxHeight * 0.44f).coerceIn(240.dp, 360.dp)
        val pinPadSpacing = (pinPadAreaHeight * 0.03f).coerceIn(6.dp, 10.dp)
        val pinButtonSize = ((pinPadAreaHeight - (pinPadSpacing * 3)) / 4).coerceIn(52.dp, 72.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "POS 餐飲系統",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Red700
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "請輸入 PIN 碼",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

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
                    Spacer(modifier = Modifier.height(16.dp))
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

                Spacer(modifier = Modifier.height(20.dp))

                // PIN pad scales with screen height.
                PinPad(
                    buttonSize = pinButtonSize,
                    spacing = pinPadSpacing,
                    onDigit = { viewModel.onDigitEntered(it) },
                    onBackspace = { viewModel.onBackspace() },
                    onClear = { viewModel.onClear() }
                )
            }

            Text(
                text = stringResource(R.string.app_version, versionName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun PinPad(
    buttonSize: Dp,
    spacing: Dp,
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

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { key ->
                    PinButton(
                        buttonSize = buttonSize,
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
private fun PinButton(buttonSize: Dp, label: String, onClick: () -> Unit) {
    val isSpecial = label == "C" || label == "⌫"
    val fontSize = when {
        buttonSize <= 58.dp -> 18.sp
        buttonSize <= 66.dp -> 20.sp
        else -> 22.sp
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
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
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = if (isSpecial) MaterialTheme.colorScheme.error else Red700
            )
        }
    }
}
