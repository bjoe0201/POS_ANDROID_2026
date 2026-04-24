package com.pos.app.ui.settings

import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.ui.theme.LocalPosColors
import com.pos.app.ui.theme.PosColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val t = LocalPosColors.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val timestamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) }

    // Restore confirmation dialog
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            containerColor = t.surface,
            title = { Text("確認備份匯入", color = t.text, fontWeight = FontWeight.Bold) },
            text = { Text("還原後 App 會自動關閉，重新開啟後生效。\n目前的資料將被備份檔覆蓋，確定繼續？", color = t.textSub) },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri!!
                        pendingRestoreUri = null
                        viewModel.restoreDb(context, uri)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = t.accent)
                ) { Text("確定還原") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("取消", color = t.textSub) }
            }
        )
    }

    // DB reset two-step confirmation - inline boxes instead of dialogs
    var resetStep by remember { mutableStateOf(0) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.backupDb(context, it) } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingRestoreUri = it } }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = t.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // TopBar
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).background(t.topbar).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(t.accent))
                    Column {
                        Text("設定", color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("系統設定", color = t.textMuted, fontSize = 11.sp)
                    }
                }
                if (onBack != null) {
                    TextButton(onClick = onBack) {
                        Text("← 返回", color = t.textSub, fontSize = 13.sp)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User manual button
                var showManual by remember { mutableStateOf(false) }
                if (showManual) { UserManualDialog(t = t, onDismiss = { showManual = false }) }
                Button(
                    onClick = { showManual = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = t.accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📖  使用說明", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                // App info card
                AppInfoCard(t = t)

                // PIN change section
                SectionCard(title = "修改 PIN 碼", t = t) {
                    if (uiState.isDefaultPin) {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(t.warning.copy(0.12f)).border(1.dp, t.warning.copy(0.3f), RoundedCornerShape(8.dp)).padding(12.dp)
                        ) {
                            Text("目前使用預設密碼 1234，請盡快修改", color = t.warning, fontSize = 13.sp)
                        }
                    }
                    ChangePinContent(viewModel = viewModel, snackbarHostState = snackbarHostState, t = t)
                }

                // Tab visibility section
                SectionCard(title = "功能頁面", t = t) {
                    Text("「記帳」為必要功能，其餘頁面可依需求開啟或關閉。", color = t.textMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    TabToggleRow(label = "記帳",   emoji = "🛒", enabled = true, locked = true, onToggle = {}, t = t)
                    TabToggleRow(label = "訂位",   emoji = "📅", enabled = uiState.tabReservationEnabled, locked = false, onToggle = { viewModel.setTabReservationEnabled(it) }, t = t)
                    TabToggleRow(label = "菜單管理", emoji = "🥩", enabled = uiState.tabMenuEnabled, locked = false, onToggle = { viewModel.setTabMenuEnabled(it) }, t = t)
                    TabToggleRow(label = "桌號設定", emoji = "🪑", enabled = uiState.tabTableEnabled, locked = false, onToggle = { viewModel.setTabTableEnabled(it) }, t = t)
                    TabToggleRow(label = "報表",   emoji = "📊", enabled = uiState.tabReportEnabled, locked = false, onToggle = { viewModel.setTabReportEnabled(it) }, t = t)
                }

                // Reservation settings section
                if (uiState.tabReservationEnabled) {
                    var showDurationMenu by remember { mutableStateOf(false) }
                    var showBreakToggle by remember { mutableStateOf(uiState.breakStart.isNotEmpty()) }
                    var showBizStartPicker by remember { mutableStateOf(false) }
                    var showBizEndPicker by remember { mutableStateOf(false) }
                    var showBreakStartPicker by remember { mutableStateOf(false) }
                    var showBreakEndPicker by remember { mutableStateOf(false) }

                    if (showBizStartPicker) {
                        TimePickerAlertDialog(currentTime = uiState.bizStart, onConfirm = { viewModel.setBizStart(it); showBizStartPicker = false }, onDismiss = { showBizStartPicker = false }, t = t)
                    }
                    if (showBizEndPicker) {
                        TimePickerAlertDialog(currentTime = uiState.bizEnd, onConfirm = { viewModel.setBizEnd(it); showBizEndPicker = false }, onDismiss = { showBizEndPicker = false }, t = t)
                    }
                    if (showBreakStartPicker) {
                        TimePickerAlertDialog(currentTime = uiState.breakStart.ifEmpty { "14:00" }, onConfirm = { viewModel.setBreakStart(it); showBreakStartPicker = false }, onDismiss = { showBreakStartPicker = false }, t = t)
                    }
                    if (showBreakEndPicker) {
                        TimePickerAlertDialog(currentTime = uiState.breakEnd.ifEmpty { "17:00" }, onConfirm = { viewModel.setBreakEnd(it); showBreakEndPicker = false }, onDismiss = { showBreakEndPicker = false }, t = t)
                    }

                    SectionCard(title = "訂位設定", t = t) {
                        // Business hours
                        Text("營業時間", color = t.textMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showBizStartPicker = true }, modifier = Modifier.weight(1f), border = androidx.compose.foundation.BorderStroke(1.dp, t.border), shape = RoundedCornerShape(8.dp)) {
                                Text("開始 ${uiState.bizStart}", color = t.text, fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { showBizEndPicker = true }, modifier = Modifier.weight(1f), border = androidx.compose.foundation.BorderStroke(1.dp, t.border), shape = RoundedCornerShape(8.dp)) {
                                Text("結束 ${uiState.bizEnd}", color = t.text, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Break time toggle
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("中間休息時間", color = t.text, fontSize = 14.sp)
                            Switch(
                                checked = showBreakToggle,
                                onCheckedChange = {
                                    showBreakToggle = it
                                    if (!it) { viewModel.setBreakStart(""); viewModel.setBreakEnd("") }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = t.accent, checkedTrackColor = t.accentDim2, uncheckedThumbColor = t.textMuted, uncheckedTrackColor = t.border)
                            )
                        }
                        if (showBreakToggle) {
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showBreakStartPicker = true }, modifier = Modifier.weight(1f), border = androidx.compose.foundation.BorderStroke(1.dp, t.border), shape = RoundedCornerShape(8.dp)) {
                                    Text("休息開始 ${uiState.breakStart.ifEmpty { "--:--" }}", color = t.text, fontSize = 13.sp)
                                }
                                OutlinedButton(onClick = { showBreakEndPicker = true }, modifier = Modifier.weight(1f), border = androidx.compose.foundation.BorderStroke(1.dp, t.border), shape = RoundedCornerShape(8.dp)) {
                                    Text("休息結束 ${uiState.breakEnd.ifEmpty { "--:--" }}", color = t.text, fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Default duration
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("預設用餐時間", color = t.text, fontSize = 14.sp)
                            Box {
                                OutlinedButton(onClick = { showDurationMenu = true }, border = androidx.compose.foundation.BorderStroke(1.dp, t.border), shape = RoundedCornerShape(8.dp)) {
                                    Text("${uiState.defaultDuration} 分鐘", color = t.accent, fontSize = 13.sp)
                                }
                                DropdownMenu(expanded = showDurationMenu, onDismissRequest = { showDurationMenu = false }, containerColor = t.surface) {
                                    listOf(30, 45, 60, 90, 120).forEach { min ->
                                        DropdownMenuItem(
                                            text = { Text("$min 分鐘", color = if (uiState.defaultDuration == min) t.accent else t.text) },
                                            onClick = { viewModel.setDefaultDuration(min); showDurationMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Calendar chips per row
                        var showChipsPerRowMenu by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("月曆每行時段數", color = t.text, fontSize = 14.sp)
                            Box {
                                OutlinedButton(onClick = { showChipsPerRowMenu = true }, border = androidx.compose.foundation.BorderStroke(1.dp, t.border), shape = RoundedCornerShape(8.dp)) {
                                    Text("每行 ${uiState.calendarChipsPerRow} 個", color = t.accent, fontSize = 13.sp)
                                }
                                DropdownMenu(expanded = showChipsPerRowMenu, onDismissRequest = { showChipsPerRowMenu = false }, containerColor = t.surface) {
                                    listOf(1, 2, 3, 4).forEach { n ->
                                        DropdownMenuItem(
                                            text = { Text("每行 $n 個", color = if (uiState.calendarChipsPerRow == n) t.accent else t.text) },
                                            onClick = { viewModel.setCalendarChipsPerRow(n); showChipsPerRowMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Backup section
                SectionCard(title = "資料備份", t = t) {
                    Text("使用備份匯出保存目前資料，或透過備份匯入還原先前備份。", color = t.textMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { backupLauncher.launch("火鍋店POS備份_$timestamp.zip") },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, t.accent),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("備份匯出", color = t.accent) }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("備份匯入", color = t.textSub) }
                    }
                }

                // DB reset section
                SectionCard(title = "資料庫管理", t = t) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(t.error.copy(0.08f)).border(1.dp, t.error.copy(0.25f), RoundedCornerShape(8.dp)).padding(12.dp)
                    ) {
                        Text(
                            "初始化將清除全部訂單、菜單與桌號，並恢復系統預設資料。\n建議先執行備份匯出再操作。",
                            color = t.error.copy(0.9f),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    when (resetStep) {
                        0 -> {
                            Button(
                                onClick = { resetStep = 1 },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = t.error.copy(0.7f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("初始化資料庫", fontWeight = FontWeight.SemiBold) }
                        }
                        1 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.error.copy(0.1f)).border(1.dp, t.error.copy(0.3f), RoundedCornerShape(10.dp)).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("初始化前請先備份", color = t.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("此操作將清除所有資料並恢復預設內容。\n請先執行「備份匯出」以保留目前資料。", color = t.textSub, fontSize = 13.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { resetStep = 0 },
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("取消", color = t.textSub) }
                                    Button(
                                        onClick = { resetStep = 2 },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = t.error),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("已備份，繼續", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        2 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.error.copy(0.15f)).border(2.dp, t.error.copy(0.5f), RoundedCornerShape(10.dp)).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("⚠️ 最終確認", color = t.error, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                Text("此操作無法復原！所有訂單、菜單與桌號資料將被清除，並恢復為系統預設值。確定要初始化資料庫嗎？", color = t.text, fontSize = 13.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { resetStep = 0 },
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("取消", color = t.textSub) }
                                    Button(
                                        onClick = { resetStep = 0; viewModel.resetDatabase() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = t.error),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("確定初始化", fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppInfoCard(t: PosColors) {
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

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.card).border(1.dp, t.border, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(t.accentDim2).border(1.dp, t.accent, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("🍲", fontSize = 26.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("火鍋店 POS 系統", color = t.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("v$versionName", color = t.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = t.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionCard(title: String, t: PosColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, t.border, RoundedCornerShape(12.dp))
    ) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth().background(t.surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(t.accent))
            Text(title, color = t.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))
        // Section body
        Column(
            modifier = Modifier.fillMaxWidth().background(t.card).padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun ChangePinContent(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState, t: PosColors) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PinTextField(
            value = currentPin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { currentPin = it; errorMsg = "" } },
            label = "目前 PIN 碼",
            showPin = showCurrent,
            onToggleShow = { showCurrent = !showCurrent },
            t = t
        )
        PinTextField(
            value = newPin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { newPin = it; errorMsg = "" } },
            label = "新 PIN 碼",
            showPin = showNew,
            onToggleShow = { showNew = !showNew },
            t = t
        )
        PinTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { confirmPin = it; errorMsg = "" } },
            label = "確認新 PIN 碼",
            showPin = showConfirm,
            onToggleShow = { showConfirm = !showConfirm },
            t = t
        )
        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = t.error, fontSize = 13.sp)
        }
        Button(
            onClick = {
                viewModel.changePin(currentPin, newPin, confirmPin) { success, msg ->
                    if (success) {
                        currentPin = ""; newPin = ""; confirmPin = ""; errorMsg = ""
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    } else {
                        errorMsg = msg
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = t.accent, disabledContainerColor = t.border),
            shape = RoundedCornerShape(8.dp),
            enabled = currentPin.length == 4 && newPin.length == 4 && confirmPin.length == 4
        ) {
            Text("確認修改", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun TimePickerAlertDialog(
    currentTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    t: PosColors
) {
    val parts = currentTime.split(":")
    var hour by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 11) }
    var minute by remember { mutableStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("選擇時間", color = t.text, fontWeight = FontWeight.Bold) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour picker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { hour = (hour + 1) % 24 }) {
                        Text("▲", color = t.accent, fontSize = 18.sp)
                    }
                    Text("%02d".format(hour), color = t.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { hour = (hour - 1 + 24) % 24 }) {
                        Text("▼", color = t.accent, fontSize = 18.sp)
                    }
                }
                Text(" : ", color = t.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                // Minute picker (5-min steps)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { minute = (minute + 5) % 60 }) {
                        Text("▲", color = t.accent, fontSize = 18.sp)
                    }
                    Text("%02d".format(minute), color = t.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { minute = (minute - 5 + 60) % 60 }) {
                        Text("▼", color = t.accent, fontSize = 18.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm("%02d:%02d".format(hour, minute)) },
                colors = ButtonDefaults.buttonColors(containerColor = t.accent),
                shape = RoundedCornerShape(8.dp)
            ) { Text("確認") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = t.textSub) }
        }
    )
}

@Composable
private fun TabToggleRow(
    label: String,
    emoji: String,
    enabled: Boolean,
    locked: Boolean,
    onToggle: (Boolean) -> Unit,
    t: PosColors
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 18.sp)
            Text(label, color = if (locked) t.textMuted else t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (locked) {
                Text("（必要）", color = t.textMuted, fontSize = 12.sp)
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = { if (!locked) onToggle(it) },
            enabled = !locked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = t.accent,
                checkedTrackColor = t.accentDim2,
                uncheckedThumbColor = t.textMuted,
                uncheckedTrackColor = t.border,
                disabledCheckedThumbColor = t.accent.copy(alpha = 0.6f),
                disabledCheckedTrackColor = t.accentDim2.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun PinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showPin: Boolean,
    onToggleShow: () -> Unit,
    t: PosColors
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = t.textMuted) },
        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onToggleShow) {
                Icon(
                    if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPin) "隱藏" else "顯示",
                    tint = t.textMuted
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
            focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent
        ),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
    )
}

// ── 使用說明 Dialog ────────────────────────────────────────────────────────────

@Composable
private fun UserManualDialog(t: PosColors, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(t.bg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.topbar)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(t.accent)
                        )
                        Text("📖 使用說明", color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("✕ 關閉", color = t.textMuted, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = t.border, thickness = 1.dp)
                // WebView content
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            loadDataWithBaseURL(null, MANUAL_HTML, "text/html", "UTF-8", null)
                        }
                    }
                )
            }
        }
    }
}

private val MANUAL_HTML = """
<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: 'Noto Sans TC', sans-serif;
    background: #0f1117;
    color: #e0e0e0;
    font-size: 14px;
    line-height: 1.7;
    padding: 16px;
  }
  h1 { color: #e05252; font-size: 20px; margin-bottom: 6px; }
  h2 {
    color: #e05252;
    font-size: 15px;
    margin: 20px 0 8px;
    padding-bottom: 4px;
    border-bottom: 1px solid #333;
  }
  h3 { color: #ff9a9a; font-size: 13px; margin: 14px 0 4px; }
  p { margin: 4px 0 8px; color: #c0c0c0; }
  ul, ol { margin: 4px 0 8px 18px; color: #c0c0c0; }
  li { margin-bottom: 4px; }
  .badge {
    display: inline-block;
    background: #2a2d3e;
    border: 1px solid #444;
    border-radius: 6px;
    padding: 2px 8px;
    font-size: 12px;
    color: #e05252;
    margin-right: 4px;
  }
  .tip {
    background: #1c2a1c;
    border-left: 3px solid #4caf50;
    border-radius: 4px;
    padding: 8px 12px;
    margin: 8px 0;
    color: #a8d5a2;
    font-size: 13px;
  }
  .warn {
    background: #2a1c1c;
    border-left: 3px solid #e05252;
    border-radius: 4px;
    padding: 8px 12px;
    margin: 8px 0;
    color: #e08080;
    font-size: 13px;
  }
  .note {
    background: #1c1c2a;
    border-left: 3px solid #5588cc;
    border-radius: 4px;
    padding: 8px 12px;
    margin: 8px 0;
    color: #88aadd;
    font-size: 13px;
  }
  table { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 13px; }
  th { background: #1e2030; color: #e05252; padding: 6px 8px; text-align: left; }
  td { padding: 5px 8px; border-bottom: 1px solid #2a2d3e; }
  hr { border: none; border-top: 1px solid #2a2d3e; margin: 16px 0; }
  .section-icon { font-size: 18px; margin-right: 6px; }
</style>
</head>
<body>

<h1>🍲 火鍋店 POS 使用說明</h1>
<p>版本 v1.1.4 &nbsp;·&nbsp; 2026-04-22</p>

<hr>

<h2><span class="section-icon">🔐</span>一、登入</h2>
<p>啟動後進入 PIN 登入畫面，輸入 4 位數字密碼即可進入主畫面。</p>
<ul>
  <li>預設密碼：<strong>1234</strong>，首次使用請於設定頁修改。</li>
  <li>連續輸入錯誤 <strong>3 次</strong>，系統鎖定 <strong>30 秒</strong>。</li>
</ul>
<div class="warn">⚠️ 請務必修改預設密碼，避免資料外洩。</div>

<hr>

<h2><span class="section-icon">🛒</span>二、記帳（點餐）</h2>
<h3>基本流程</h3>
<ol>
  <li>畫面上方橫列選擇桌號。</li>
  <li>中段分類 Tab 篩選菜單群組（鍋底 / 肉類 / 海鮮…）。</li>
  <li>點擊品項按鈕加入訂單，再次點擊可遞增數量。</li>
  <li>右側訂單摘要確認後，點擊「結帳」完成收款。</li>
</ol>
<h3>補登功能</h3>
<p>點擊日期可切換至歷史日期補登訂單。</p>
<div class="tip">💡 3 分鐘無操作，自動切回當天日期。</div>
<h3>注意事項</h3>
<ul>
  <li>同一桌號可同時存在多筆「開啟中」訂單。</li>
  <li>已結帳（PAID）或取消（CANCELLED）的訂單不可再修改。</li>
  <li>品項名稱與價格為下單時的快照，後續修改菜單不影響舊訂單。</li>
</ul>

<hr>

<h2><span class="section-icon">📅</span>三、訂位管理</h2>
<h3>月曆檢視</h3>
<ul>
  <li>每天格子顯示當日各時段的桌數，例如：<code>12:00</code>＋<code>3</code> 表示 12:00 有 3 桌訂位。</li>
  <li>左側為時間色塊，右側為桌數色塊，顏色深淺代表重要度。</li>
  <li>時段超過可見行數時，可在格子內<strong>上下滾動</strong>查看。</li>
  <li>點擊左右箭頭切換月份。</li>
</ul>
<h3>當日訂位列表</h3>
<ol>
  <li>點擊月曆上任一日期，進入該日的訂位列表。</li>
  <li>點擊右下角「＋」新增訂位。</li>
  <li>點擊訂位項目可編輯或刪除。</li>
</ol>
<h3>新增 / 編輯訂位欄位</h3>
<table>
  <tr><th>欄位</th><th>說明</th></tr>
  <tr><td>桌號</td><td>從現有有效桌號選擇</td></tr>
  <tr><td>客人姓名</td><td>訂位人姓名</td></tr>
  <tr><td>聯絡電話</td><td>選填</td></tr>
  <tr><td>人數</td><td>用餐人數</td></tr>
  <tr><td>日期</td><td>訂位日期</td></tr>
  <tr><td>開始 / 結束時間</td><td>用餐時段（依預設時長自動帶入結束時間）</td></tr>
  <tr><td>重要度</td><td>一般 / 重要 / 非常重要，對應綠 / 黃 / 紅色塊</td></tr>
  <tr><td>備註</td><td>其他注意事項，選填</td></tr>
</table>
<div class="note">📌 月曆每行可顯示的時段數量可於「設定 → 訂位設定 → 月曆每行時段數」調整（1～4 個）。</div>

<hr>

<h2><span class="section-icon">🥩</span>四、菜單管理</h2>
<h3>群組管理</h3>
<ul>
  <li>可新增、修改、排序、刪除菜單群組。</li>
  <li>刪除群組時，若該群組下有品項，系統會顯示品項數量警告並需再次確認。</li>
</ul>
<h3>品項管理</h3>
<ul>
  <li>可新增、編輯、刪除品項，設定名稱、價格、所屬群組。</li>
  <li>切換「啟用 / 停用」可讓品項在記帳頁顯示或隱藏，不影響歷史訂單。</li>
</ul>
<div class="tip">💡 品項啟停用即時生效，無需重啟 App。</div>

<hr>

<h2><span class="section-icon">🪑</span>五、桌號設定</h2>
<ul>
  <li>可新增、編輯、排序（上移 / 下移）、刪除桌號。</li>
  <li>桌號名稱最多 20 個字，可加入座位數與備註。</li>
  <li>停用的桌號不出現在記帳畫面，但歷史訂單仍保留桌號名稱快照。</li>
</ul>
<div class="warn">⚠️ 刪除桌號不會刪除該桌的歷史訂單，但新訂單無法再選取該桌號。</div>

<hr>

<h2><span class="section-icon">📊</span>六、報表</h2>
<h3>篩選範圍</h3>
<table>
  <tr><th>選項</th><th>說明</th></tr>
  <tr><td>今日</td><td>今天的訂單</td></tr>
  <tr><td>昨天</td><td>昨日的訂單</td></tr>
  <tr><td>本週</td><td>本週一到今天</td></tr>
  <tr><td>本月</td><td>本月 1 號到今天</td></tr>
  <tr><td>全部</td><td>所有訂單（含已刪除，依勾選決定）</td></tr>
</table>
<h3>統計內容</h3>
<ul>
  <li>總收入、訂單數、平均客單價。</li>
  <li>品項銷售排行（依數量）。</li>
  <li>群組銷售排行（依金額）。</li>
  <li>逐筆訂單明細（可展開查看品項）。</li>
</ul>
<h3>軟刪除</h3>
<ul>
  <li>在訂單明細點擊「刪除」僅標記為已刪除，資料不會消失。</li>
  <li>勾選「顯示已刪除」可讓已刪除訂單納入統計計算。</li>
</ul>
<div class="tip">💡 誤刪時可開啟「顯示已刪除」找回訂單，並可取消刪除標記（如功能已實作）。</div>

<hr>

<h2><span class="section-icon">⚙️</span>七、設定</h2>
<h3>修改 PIN 碼</h3>
<ol>
  <li>輸入目前 PIN 碼。</li>
  <li>輸入新 PIN 碼（4 位數字）。</li>
  <li>再次確認新 PIN 碼，點擊「確認修改」。</li>
</ol>
<h3>功能頁面</h3>
<p>可個別開關「訂位」「菜單管理」「桌號設定」「報表」分頁；「記帳」與「設定」為必要分頁，無法關閉。</p>
<div class="note">📌 停用某分頁時，若使用者正停留在該頁，系統自動跳回記帳頁。</div>
<h3>訂位設定</h3>
<table>
  <tr><th>項目</th><th>說明</th></tr>
  <tr><td>營業開始 / 結束時間</td><td>訂位可選的時段範圍</td></tr>
  <tr><td>中間休息時間</td><td>啟用後可設定不接受訂位的休息時段</td></tr>
  <tr><td>預設用餐時間</td><td>新增訂位時自動帶入的用餐時長（分鐘）</td></tr>
  <tr><td>月曆每行時段數</td><td>月曆每天格子中，每列顯示幾個時段（1～4 個，預設 2）</td></tr>
</table>
<h3>資料備份</h3>
<ul>
  <li><strong>備份匯出</strong>：將整個資料庫打包為 .zip 檔儲存至指定位置。</li>
  <li><strong>備份匯入</strong>：選取 .zip 備份檔還原資料庫，還原完成後 App 自動關閉，重開後生效。</li>
</ul>
<div class="warn">⚠️ 備份匯入會<strong>完整覆蓋</strong>現有資料庫，操作前請先執行備份匯出。</div>
<h3>資料庫初始化</h3>
<p>清除所有訂單、菜單與桌號，恢復系統預設資料（需兩步驟確認）。</p>
<div class="warn">⚠️ 初始化操作<strong>無法復原</strong>，請務必先執行備份匯出。</div>

<hr>

<h2>⚡ 常見問題</h2>
<table>
  <tr><th>問題</th><th>解決方式</th></tr>
  <tr><td>忘記 PIN 碼</td><td>目前無法透過 App 重設，需備份還原或重裝（資料會遺失）</td></tr>
  <tr><td>品項在記帳頁不見了</td><td>至菜單管理確認品項是否已停用</td></tr>
  <tr><td>桌號不見了</td><td>至桌號設定確認桌號是否已停用或刪除</td></tr>
  <tr><td>報表數字不對</td><td>確認篩選範圍與「已刪除」勾選狀態</td></tr>
  <tr><td>備份匯入後資料不見</td><td>備份會覆蓋所有資料，請確認備份檔是否正確</td></tr>
</table>

<hr>
<p style="text-align:center; color:#555; font-size:12px; padding: 8px 0 16px;">🍲 火鍋店 POS v1.1.4 &nbsp;·&nbsp; Android 10+</p>

</body>
</html>
""".trimIndent()

