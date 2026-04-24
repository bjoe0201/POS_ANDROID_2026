package com.pos.app.ui.login

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.ui.theme.LocalPosColors
import com.pos.app.ui.theme.PosColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDefaultPin by viewModel.isDefaultPin.collectAsState()
    val t = LocalPosColors.current
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull() ?: "N/A"
    }

    LaunchedEffect(uiState.pin) {
        if (uiState.pin.length == 4) viewModel.verifyPin(onLoginSuccess)
    }

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(uiState.failCount) {
        if (uiState.failCount > 0) {
            shakeAnim.animateTo(0f, keyframes {
                durationMillis = 500; 0f at 0; (-8f) at 80; 8f at 160; (-6f) at 240; 6f at 320; 0f at 500
            })
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg)
            .drawBehind {
                val c = t.accentDim.copy(alpha = 0.6f)
                drawCircle(brush = Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.2f), radius = size.minDimension * 0.65f))
                drawCircle(brush = Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(size.width * 0.72f, size.height * 0.82f), radius = size.minDimension * 0.55f))
            }
    ) {
        val compactHeight = maxHeight < 820.dp || maxWidth > maxHeight
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .padding(start = 24.dp, top = if (compactHeight) 12.dp else 24.dp, end = 24.dp, bottom = if (compactHeight) 56.dp else 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 18.dp else 32.dp)
        ) {
            // Logo
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 16.dp)) {
                Box(
                    modifier = Modifier
                        .size(if (compactHeight) 62.dp else 72.dp)
                        .clip(RoundedCornerShape(if (compactHeight) 16.dp else 20.dp))
                        .background(t.accentDim2)
                        .border(2.dp, t.accent, RoundedCornerShape(if (compactHeight) 16.dp else 20.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("🍲", fontSize = if (compactHeight) 28.sp else 32.sp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("火鍋店 POS 系統", color = t.text, fontSize = if (compactHeight) 21.sp else 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("餐飲管理系統", color = t.textMuted, fontSize = if (compactHeight) 12.sp else 13.sp, modifier = Modifier.padding(top = if (compactHeight) 4.dp else 6.dp))
                }
            }

            // PIN card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(t.card)
                    .border(1.dp, t.border, RoundedCornerShape(24.dp))
                    .padding(if (compactHeight) 16.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compactHeight) 12.dp else 24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 20.dp)) {
                    Text("請輸入 PIN 碼", color = t.textSub, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compactHeight) 12.dp else 16.dp), modifier = Modifier.graphicsLayer { translationX = shakeAnim.value }) {
                        repeat(4) { i ->
                            val filled = i < uiState.pin.length
                            Box(modifier = Modifier.size(if (compactHeight) 14.dp else 16.dp).clip(CircleShape).background(when { uiState.isError && filled -> t.error; filled -> t.accent; else -> t.border }))
                        }
                    }
                    Box(modifier = Modifier.heightIn(min = if (compactHeight) 24.dp else 32.dp), contentAlignment = Alignment.Center) {
                        when {
                            uiState.isLockedOut -> Box(Modifier.clip(RoundedCornerShape(8.dp)).background(t.error.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Text("🔒 鎖定中，請等待 ${uiState.lockoutSecondsLeft} 秒", color = t.error, fontSize = 13.sp)
                            }
                            uiState.isError -> Text("PIN 碼錯誤，還剩 ${3 - uiState.failCount} 次機會", color = t.error, fontSize = 13.sp)
                            isDefaultPin -> Box(Modifier.clip(RoundedCornerShape(8.dp)).background(t.warning.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 5.dp)) {
                                Text("目前使用預設密碼 1234，請至設定修改", color = t.warning, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                PinPad(
                    locked = uiState.isLockedOut,
                    compact = compactHeight,
                    maxPadHeight = if (compactHeight) 200.dp else 260.dp,
                    onDigit = { viewModel.onDigitEntered(it) },
                    onBackspace = { viewModel.onBackspace() },
                    onClear = { viewModel.onClear() },
                    t = t
                )
            }
        }

        Text(
            text = "v$versionName",
            color = t.textMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun PinPad(
    locked: Boolean,
    compact: Boolean,
    maxPadHeight: Dp,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    t: PosColors
) {
    val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("C","0","←"))
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxPadHeight),
        contentAlignment = Alignment.Center
    ) {
        val keypadWidth = when {
            compact && maxWidth > 280.dp -> 280.dp
            !compact && maxWidth > 340.dp -> 340.dp
            else -> maxWidth
        }
        val spacing = (keypadWidth * 0.025f).coerceIn(4.dp, if (compact) 7.dp else 9.dp)
        val keyWidth = ((keypadWidth - spacing * 2) / 3).coerceIn(if (compact) 40.dp else 50.dp, if (compact) 78.dp else 88.dp)
        val keyHeightByWidth = (keyWidth * 0.60f).coerceIn(if (compact) 30.dp else 36.dp, 50.dp)
        val keyHeightByArea = ((maxPadHeight - spacing * 3) / 4).coerceIn(if (compact) 30.dp else 36.dp, 50.dp)
        val keyHeight = minOf(keyHeightByWidth, keyHeightByArea)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { key ->
                        PinKey(
                            label = key,
                            special = key == "C" || key == "←",
                            disabled = locked,
                            width = keyWidth,
                            height = keyHeight,
                            t = t,
                            onClick = { when(key) { "←" -> onBackspace(); "C" -> onClear(); else -> onDigit(key) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    special: Boolean,
    disabled: Boolean,
    width: Dp,
    height: Dp,
    t: PosColors,
    onClick: () -> Unit
) {
    val pressAnim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val textSize = if (height < 40.dp) 16.sp else if (height < 46.dp) 18.sp else 22.sp
    val arrowSize = if (height < 40.dp) 14.sp else if (height < 46.dp) 16.sp else 18.sp
    Box(
        modifier = Modifier.size(width = width, height = height)
            .graphicsLayer { scaleX = pressAnim.value; scaleY = pressAnim.value }
            .clip(RoundedCornerShape(14.dp))
            .background(when { disabled -> if (special) t.error.copy(0.08f) else t.surface.copy(0.5f); special -> t.error.copy(alpha = 0.15f); else -> t.surface })
            .border(1.dp, if (special) t.error.copy(0.4f) else t.border, RoundedCornerShape(14.dp))
            .then(if (!disabled) Modifier.clickable { scope.launch { pressAnim.animateTo(0.94f, tween(60)); pressAnim.animateTo(1f, tween(80)) }; onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = if (label == "←") arrowSize else textSize, fontWeight = FontWeight.Bold,
            color = if (disabled) t.textMuted else if (special) t.error else t.text)
    }
}
