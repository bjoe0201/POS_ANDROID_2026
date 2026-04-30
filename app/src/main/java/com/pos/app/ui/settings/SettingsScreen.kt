package com.pos.app.ui.settings

import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.pos.app.util.UsbPrinterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
            title = { Text("⚠️ 確認匯入備份", color = t.error, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(t.error.copy(alpha = 0.1f))
                            .border(1.dp, t.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "匯入備份將完整覆蓋目前所有資料！\n還原後 App 會自動關閉，重新開啟後生效。",
                            color = t.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "系統將在覆蓋前自動建立安全備份，萬一匯入失敗仍可找回。",
                        color = t.textSub,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri!!
                        pendingRestoreUri = null
                        viewModel.restoreDb(context, uri)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = t.error)
                ) { Text("確定匯入", fontWeight = FontWeight.Bold) }
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

                // 點餐操作（長按連續加減）
                SectionCard(title = "點餐操作", t = t) {
                    // 觸覺回饋開關
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("觸覺回饋（震動）", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("點選與長按連續觸發時提供輕微震動", color = t.textMuted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = uiState.hapticEnabled,
                            onCheckedChange = { viewModel.setHapticEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = t.accent,
                                checkedTrackColor = t.accentDim2,
                                uncheckedThumbColor = t.textMuted,
                                uncheckedTrackColor = t.border
                            )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))
                    Spacer(Modifier.height(12.dp))

                    Text("在記帳頁長按 +/− 可連續加減數量；放開停止。", color = t.textMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))

                    // 連續計數速度
                    var intervalSlider by remember(uiState.qtyRepeatIntervalMs) {
                        mutableStateOf(uiState.qtyRepeatIntervalMs.toFloat())
                    }
                    val perSecond = (1000f / intervalSlider).toInt().coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("連續計數速度", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("間隔 ${intervalSlider.toInt()}ms（每秒約 $perSecond 次）", color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = intervalSlider,
                        onValueChange = { intervalSlider = it },
                        onValueChangeFinished = { viewModel.setQtyRepeatIntervalMs(intervalSlider.toInt()) },
                        valueRange = 30f..500f,
                        steps = ((500 - 30) / 10) - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = t.accent,
                            activeTrackColor = t.accent,
                            inactiveTrackColor = t.border
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("快 (30ms)", color = t.textMuted, fontSize = 11.sp)
                        Text("慢 (500ms)", color = t.textMuted, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    // 長按啟動延遲
                    var initialDelaySlider by remember(uiState.qtyRepeatInitialDelayMs) {
                        mutableStateOf(uiState.qtyRepeatInitialDelayMs.toFloat())
                    }
                    val seconds = "%.1f".format(initialDelaySlider / 1000f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("長按啟動延遲", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${seconds} 秒後開始連續", color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = initialDelaySlider,
                        onValueChange = { initialDelaySlider = it },
                        onValueChangeFinished = { viewModel.setQtyRepeatInitialDelayMs(initialDelaySlider.toInt()) },
                        valueRange = 300f..2000f,
                        steps = ((2000 - 300) / 100) - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = t.accent,
                            activeTrackColor = t.accent,
                            inactiveTrackColor = t.border
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("靈敏 (0.3s)", color = t.textMuted, fontSize = 11.sp)
                        Text("保守 (2.0s)", color = t.textMuted, fontSize = 11.sp)
                    }
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

                // Printer section
                PrinterSection(
                    t = t,
                    snackbarHostState = snackbarHostState,
                    viewModel = viewModel,
                    printerTestPassed = uiState.printerTestPassed,
                    printCheckoutEnabled = uiState.printCheckoutEnabled,
                    printDetailEnabled = uiState.printDetailEnabled
                )

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

                // Auto backup section
                AutoBackupSection(
                    viewModel = viewModel,
                    uiState = uiState,
                    t = t
                )

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
private fun PrinterSection(
    t: PosColors,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel,
    printerTestPassed: Boolean,
    printCheckoutEnabled: Boolean,
    printDetailEnabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMsg by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    fun runTest() {
        isTesting = true
        statusMsg = "正在搜尋 USB 裝置…"
        scope.launch {
            val device = withContext(Dispatchers.IO) { UsbPrinterManager.findPrinterDevice(context) }
            if (device == null) {
                statusMsg = "未偵測到 USB 裝置，請確認連接與 OTG 設定。"
                isTesting = false
                return@launch
            }
            statusMsg = "找到裝置：${device.productName ?: device.deviceName}" +
                    "\nVendorID=0x${"%04X".format(device.vendorId)}  ProductID=0x${"%04X".format(device.productId)}"

            suspend fun doTest() {
                statusMsg += "\n已有權限，開始列印…"
                val result = UsbPrinterManager.printTestPage(context, device)
                if (result.isSuccess) {
                    statusMsg += "\n列印成功！"
                    viewModel.setPrinterTestPassed(true)
                } else {
                    statusMsg += "\n列印失敗：${result.exceptionOrNull()?.message}"
                }
                isTesting = false
            }

            if (!UsbPrinterManager.hasPermission(context, device)) {
                statusMsg += "\n正在請求 USB 權限…"
                UsbPrinterManager.requestPermission(context, device) { granted ->
                    scope.launch {
                        if (!granted) { statusMsg += "\n權限被拒絕。"; isTesting = false; return@launch }
                        doTest()
                    }
                }
            } else {
                doTest()
            }
        }
    }

    SectionCard(title = "印表機", t = t) {
        Text("USB 熱感印表機（ESC/POS），請先以 USB 連接 EPSON TM-T70 後點「測試列印」。", color = t.textMuted, fontSize = 13.sp)
        if (statusMsg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(t.surface)
                    .border(1.dp, t.border, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(statusMsg, color = t.textSub, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { runTest() },
            enabled = !isTesting,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = t.accent),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isTesting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = t.text, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isTesting) "測試中…" else "測試列印", fontWeight = FontWeight.SemiBold)
        }

        // 列印功能開關（僅在測試通過後顯示）
        if (printerTestPassed) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.height(1.dp).fillMaxWidth().background(t.border))
            Spacer(Modifier.height(14.dp))

            // 收款結帳列印
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("收款結帳列印", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("確認收款後自動列印收據", color = t.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = printCheckoutEnabled,
                    onCheckedChange = { viewModel.setPrintCheckoutEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = t.accent, checkedTrackColor = t.accentDim2,
                        uncheckedThumbColor = t.textMuted, uncheckedTrackColor = t.border
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            // 明細列印
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("明細列印", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("報表訂單明細新增逐筆列印按鈕", color = t.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = printDetailEnabled,
                    onCheckedChange = { viewModel.setPrintDetailEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = t.accent, checkedTrackColor = t.accentDim2,
                        uncheckedThumbColor = t.textMuted, uncheckedTrackColor = t.border
                    )
                )
            }
        }
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

@Composable
private fun AutoBackupSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    t: PosColors
) {
    val context = LocalContext.current
    var showIdleMenu by remember { mutableStateOf(false) }
    var showRetentionMenu by remember { mutableStateOf(false) }
    var pendingRestoreEntry by remember { mutableStateOf<com.pos.app.util.BackupEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<com.pos.app.util.BackupEntry?>(null) }

    // 進入畫面時立即拉一次最新狀態（涵蓋背景備份、外部檔案變動等情況）
    LaunchedEffect(Unit) { viewModel.refreshAutoBackupFiles() }

    // SAF 資料夾選擇器
    val pickFolderLauncher = rememberLauncherForActivityResult(
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
            viewModel.setAutoBackupExternalTreeUri(uri.toString())
        }
    }

    val lastText = remember(uiState.autoBackupLastAt) {
        uiState.autoBackupLastAt?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: "尚無備份"
    }

    if (pendingRestoreEntry != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreEntry = null },
            containerColor = t.surface,
            title = { Text("還原自動備份", color = t.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "將使用「${pendingRestoreEntry!!.name}」覆蓋目前資料，還原後 App 會自動關閉，重新開啟後生效。確定繼續？",
                    color = t.textSub
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val e = pendingRestoreEntry!!
                        pendingRestoreEntry = null
                        viewModel.restoreFromAutoBackup(context, e)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = t.accent)
                ) { Text("確定還原") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreEntry = null }) { Text("取消", color = t.textSub) }
            }
        )
    }

    if (pendingDeleteEntry != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            containerColor = t.surface,
            title = { Text("刪除備份", color = t.text, fontWeight = FontWeight.Bold) },
            text = { Text("確定刪除「${pendingDeleteEntry!!.name}」？此操作無法復原。", color = t.textSub) },
            confirmButton = {
                Button(
                    onClick = {
                        val e = pendingDeleteEntry!!
                        pendingDeleteEntry = null
                        viewModel.deleteAutoBackup(e)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = t.error)
                ) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) { Text("取消", color = t.textSub) }
            }
        )
    }

    SectionCard(title = "自動儲存", t = t) {
        Text(
            "閒置指定時間後自動備份到「下載／火鍋店POS備份」；也可指定其他資料夾。App 解除安裝後備份檔仍會保留。",
            color = t.textMuted, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))

        // Enable toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("啟用自動儲存", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = uiState.autoBackupEnabled,
                onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = t.accent,
                    checkedTrackColor = t.accentDim2,
                    uncheckedThumbColor = t.textMuted,
                    uncheckedTrackColor = t.border
                )
            )
        }

        // Idle minutes
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("閒置觸發時間", color = t.text, fontSize = 14.sp)
            Box {
                OutlinedButton(
                    onClick = { showIdleMenu = true },
                    enabled = uiState.autoBackupEnabled,
                    border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("${uiState.autoBackupIdleMinutes} 分鐘", color = t.accent, fontSize = 13.sp) }
                DropdownMenu(expanded = showIdleMenu, onDismissRequest = { showIdleMenu = false }, containerColor = t.surface) {
                    listOf(1, 3, 5, 10, 15, 30, 60).forEach { m ->
                        DropdownMenuItem(
                            text = { Text("$m 分鐘", color = if (uiState.autoBackupIdleMinutes == m) t.accent else t.text) },
                            onClick = { viewModel.setAutoBackupIdleMinutes(m); showIdleMenu = false }
                        )
                    }
                }
            }
        }

        // Retention days
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("保留天數", color = t.text, fontSize = 14.sp)
            Box {
                OutlinedButton(
                    onClick = { showRetentionMenu = true },
                    enabled = uiState.autoBackupEnabled,
                    border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("${uiState.autoBackupRetentionDays} 天", color = t.accent, fontSize = 13.sp) }
                DropdownMenu(expanded = showRetentionMenu, onDismissRequest = { showRetentionMenu = false }, containerColor = t.surface) {
                    listOf(1, 3, 5, 7, 14, 30).forEach { d ->
                        DropdownMenuItem(
                            text = { Text("$d 天", color = if (uiState.autoBackupRetentionDays == d) t.accent else t.text) },
                            onClick = { viewModel.setAutoBackupRetentionDays(d); showRetentionMenu = false }
                        )
                    }
                }
            }
        }

        // Storage folder
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(t.surface)
                .border(1.dp, t.border, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("備份資料夾", color = t.textMuted, fontSize = 12.sp)
            Text(
                uiState.autoBackupStorageDesc,
                color = t.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (!uiState.autoBackupUsingCustom) {
                Text("（預設：系統「下載 / 火鍋店POS備份」）", color = t.textMuted, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pickFolderLauncher.launch(null) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, t.accent),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("選擇其他資料夾", color = t.accent, fontSize = 12.sp) }
                if (uiState.autoBackupUsingCustom) {
                    OutlinedButton(
                        onClick = { viewModel.clearAutoBackupExternalTreeUri() },
                        border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("改回預設", color = t.textSub, fontSize = 12.sp) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("最近自動備份", color = t.textMuted, fontSize = 12.sp)
                Text(lastText, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = { viewModel.backupNow() },
                colors = ButtonDefaults.buttonColors(containerColor = t.accent),
                shape = RoundedCornerShape(8.dp)
            ) { Text("立即備份", fontSize = 13.sp) }
        }

        if (uiState.autoBackupFiles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))
            Spacer(Modifier.height(8.dp))
            Text("備份列表（${uiState.autoBackupFiles.size} 個）", color = t.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
            uiState.autoBackupFiles.forEach { info ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(t.surface)
                        .border(1.dp, t.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(info.name, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${sdf.format(Date(info.lastModified))}  ·  ${"%.1f".format(info.size / 1024.0)} KB",
                            color = t.textMuted, fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = { pendingRestoreEntry = info }) {
                        Text("還原", color = t.accent, fontSize = 13.sp)
                    }
                    TextButton(onClick = { pendingDeleteEntry = info }) {
                        Text("刪除", color = t.error, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
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

  html { scroll-behavior: smooth; }

  body {
    font-family: 'Noto Sans TC', system-ui, sans-serif;
    background: #0d1018;
    color: #dde0e8;
    font-size: 14px;
    line-height: 1.75;
    padding: 0 0 80px;
  }

  /* ── Hero Header ── */
  .hero {
    background: linear-gradient(135deg, #1a0808 0%, #2a0d0d 60%, #1a1228 100%);
    padding: 22px 18px 18px;
    border-bottom: 1px solid #3a1a1a;
    text-align: center;
  }
  .hero h1 {
    font-size: 20px;
    font-weight: 700;
    color: #f0f0f0;
    margin-bottom: 4px;
    letter-spacing: 0.5px;
  }
  .hero .subtitle {
    font-size: 12px;
    color: #888;
  }
  .hero .ver-badge {
    display: inline-block;
    background: #e05252;
    color: #fff;
    font-size: 11px;
    font-weight: 600;
    padding: 2px 10px;
    border-radius: 20px;
    margin-top: 8px;
  }

  /* ── TOC ── */
  .toc {
    background: #13161f;
    border-bottom: 1px solid #252830;
    padding: 12px 16px;
  }
  .toc-title {
    font-size: 11px;
    color: #666;
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-bottom: 8px;
  }
  .toc-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  .toc-item {
    background: #1e2130;
    border: 1px solid #2e3248;
    border-radius: 8px;
    padding: 5px 10px;
    font-size: 12px;
    color: #aab0cc;
    text-decoration: none;
    display: inline-block;
    cursor: pointer;
  }
  a.toc-item:active { background: #2a304a; border-color: #5566aa; color: #ccd4ff; }

  /* ── Back to top ── */
  .back-to-top {
    position: fixed;
    right: 18px;
    bottom: 20px;
    width: 44px;
    height: 44px;
    background: #e05252;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    color: #fff;
    text-decoration: none;
    box-shadow: 0 3px 12px rgba(224,82,82,0.45);
    border: 2px solid #ff8080;
    line-height: 1;
  }
  .back-to-top:active { background: #c03838; }

  /* ── Section ── */
  .section { padding: 0 16px; }

  .section-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 22px 0 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #252830;
  }
  .section-num {
    background: #e05252;
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .section-icon-lbl {
    font-size: 15px;
    font-weight: 700;
    color: #f0f0f0;
  }

  /* ── Sub-headings ── */
  h3 {
    color: #cc8888;
    font-size: 13px;
    font-weight: 600;
    margin: 14px 0 6px;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  h3::before {
    content: '';
    display: inline-block;
    width: 3px;
    height: 13px;
    background: #e05252;
    border-radius: 2px;
    flex-shrink: 0;
  }

  p { margin: 4px 0 8px; color: #b0b8c8; }
  ul, ol { margin: 4px 0 10px 20px; color: #b0b8c8; }
  li { margin-bottom: 5px; }
  strong { color: #e8e0d0; font-weight: 600; }
  code {
    background: #1e2235;
    border: 1px solid #2e3555;
    border-radius: 4px;
    padding: 1px 6px;
    font-size: 12px;
    color: #c8b4f0;
    font-family: monospace;
  }

  /* ── Step flow ── */
  .steps { list-style: none; margin: 6px 0 10px; padding: 0; }
  .steps li {
    display: flex;
    gap: 10px;
    align-items: flex-start;
    margin-bottom: 8px;
    color: #b0b8c8;
  }
  .step-num {
    background: #2a1a1a;
    border: 1px solid #5a2a2a;
    color: #e05252;
    font-size: 11px;
    font-weight: 700;
    min-width: 20px;
    height: 20px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 2px;
    flex-shrink: 0;
  }

  /* ── Cards ── */
  .tip, .warn, .note, .info {
    border-radius: 8px;
    padding: 10px 12px;
    margin: 8px 0;
    font-size: 13px;
    line-height: 1.6;
  }
  .tip {
    background: #0f1f14;
    border: 1px solid #1e4a26;
    border-left: 3px solid #4caf50;
    color: #90c89a;
  }
  .warn {
    background: #1f0f0f;
    border: 1px solid #4a1e1e;
    border-left: 3px solid #e05252;
    color: #d08888;
  }
  .note {
    background: #0f1322;
    border: 1px solid #1e2a55;
    border-left: 3px solid #5588ee;
    color: #8899cc;
  }
  .info {
    background: #191520;
    border: 1px solid #3a2a55;
    border-left: 3px solid #aa77ee;
    color: #bb99ee;
  }

  /* ── Feature pill tags ── */
  .tag {
    display: inline-block;
    background: #2a1a30;
    border: 1px solid #6644aa;
    border-radius: 12px;
    padding: 1px 9px;
    font-size: 11px;
    color: #bb99ee;
    margin: 0 2px 2px 0;
    vertical-align: middle;
  }
  .tag-new {
    background: #1a2a15;
    border-color: #4a8a44;
    color: #88cc88;
  }

  /* ── Tables ── */
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 8px 0 12px;
    font-size: 13px;
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid #252830;
  }
  th {
    background: #1a1d2a;
    color: #e05252;
    padding: 8px 10px;
    text-align: left;
    font-weight: 600;
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  td {
    padding: 7px 10px;
    border-bottom: 1px solid #1e2130;
    color: #b0b8c8;
    vertical-align: top;
  }
  tr:last-child td { border-bottom: none; }
  tr:nth-child(even) td { background: #11141e; }

  /* ── Divider ── */
  .divider {
    height: 1px;
    background: linear-gradient(to right, transparent, #2e3248 30%, #2e3248 70%, transparent);
    margin: 4px 0;
  }

  /* ── FAQ ── */
  .faq-item {
    background: #13161f;
    border: 1px solid #252830;
    border-radius: 8px;
    padding: 10px 12px;
    margin-bottom: 8px;
  }
  .faq-q {
    color: #e8d0a0;
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 4px;
  }
  .faq-a {
    color: #9099b0;
    font-size: 13px;
  }

  /* ── Footer ── */
  .footer {
    text-align: center;
    color: #444;
    font-size: 11px;
    padding: 20px 0 8px;
  }
</style>
</head>
<body>

<!-- Hero -->
<div id="top" class="hero">
  <div style="font-size:32px;margin-bottom:6px;">🍲</div>
  <h1>火鍋店 POS 使用說明</h1>
  <div class="subtitle">Android 平板記帳系統操作指引</div>
  <div class="ver-badge">v1.2.8 &nbsp;·&nbsp; 2026-04-30</div>
</div>

<!-- TOC -->
<div class="toc">
  <div class="toc-title">目錄</div>
  <div class="toc-grid">
    <a href="#sec-login"       class="toc-item">🔐 登入</a>
    <a href="#sec-order"       class="toc-item">🛒 記帳點餐</a>
    <a href="#sec-reservation" class="toc-item">📅 訂位管理</a>
    <a href="#sec-menu"        class="toc-item">🥩 菜單管理</a>
    <a href="#sec-table"       class="toc-item">🪑 桌號設定</a>
    <a href="#sec-report"      class="toc-item">📊 報表</a>
    <a href="#sec-backup"      class="toc-item">💾 備份</a>
    <a href="#sec-settings"    class="toc-item">⚙️ 設定</a>
    <a href="#sec-faq"         class="toc-item">❓ 常見問題</a>
  </div>
</div>

<div class="section">

<!-- ①  登入 -->
<div id="sec-login" class="section-header">
  <div class="section-num">1</div>
  <div class="section-icon-lbl">🔐 登入</div>
</div>

<p>啟動後進入 PIN 碼登入畫面，輸入 <strong>4 位數字密碼</strong>即可進入主畫面。</p>
<ul>
  <li>首次使用預設密碼：<strong>1234</strong>，請立即至設定修改。</li>
  <li>連續輸入錯誤 <strong>3 次</strong>，系統鎖定 <strong>30 秒</strong>後自動解鎖。</li>
</ul>
<div class="warn">⚠️ 請務必修改預設密碼，避免他人隨意存取資料。</div>

<div class="divider"></div>

<!-- ②  記帳點餐 -->
<div id="sec-order" class="section-header">
  <div class="section-num">2</div>
  <div class="section-icon-lbl">🛒 記帳點餐</div>
</div>

<h3>基本點餐流程</h3>
<ol class="steps">
  <li><span class="step-num">1</span><span>畫面<strong>上方橫列</strong>點選桌號（可左右滑動查看更多桌）。</span></li>
  <li><span class="step-num">2</span><span>中段<strong>分類 Tab</strong> 切換菜單群組（鍋底 / 肉類 / 海鮮 / 蔬菜 / 飲料 / 其他）。</span></li>
  <li><span class="step-num">3</span><span><strong>點擊品項卡片</strong>加入訂單；右側即時顯示品項清單與合計金額。</span></li>
  <li><span class="step-num">4</span><span>確認金額後點「<strong>送出結帳 →</strong>」，跳出明細確認視窗。</span></li>
  <li><span class="step-num">5</span><span>點「<strong>✓ 確認收款</strong>」完成結帳，系統播放歡快音效。</span></li>
</ol>

<h3>快速調整數量 <span class="tag tag-new">v1.2.5+</span></h3>
<ul>
  <li><strong>單擊</strong>品項卡片上的 <code>＋</code> / <code>－</code>：數量加減 1。</li>
  <li><strong>長按</strong> <code>＋</code> / <code>－</code> 超過 1 秒：進入<strong>連續計數模式</strong>，放開即停止。</li>
  <li>按住或單擊時，卡片上方會浮現<strong>數字氣泡</strong>（加入亮黃色 / 減少亮綠色）即時反饋。</li>
  <li>連續計數速度與長按啟動延遲可於「設定 → 點餐操作」調整。</li>
</ul>
<div class="tip">💡 觸覺回饋（震動）預設開啟，可於「設定 → 點餐操作 → 觸覺回饋」關閉。</div>

<h3>補登歷史日期 <span class="tag tag-new">v1.2.7+</span></h3>
<p>點擊頁面右上角日期可切換至過去日期補登訂單。</p>
<div class="note">📌 切換至歷史日期時，桌號列上方會出現紅色橫條「⚠️ 補登模式：MM/dd」提醒。3 分鐘無操作自動恢復今天；第一次加入品項也會跳出確認提示。</div>

<h3>注意事項</h3>
<ul>
  <li>同一桌號可同時存在多筆「進行中」訂單。</li>
  <li>已結帳或已取消的訂單不可再修改。</li>
  <li>品項名稱與價格為<strong>下單時的快照</strong>，後續修改菜單不影響舊訂單。</li>
  <li>收款音效音量跟隨系統「通知」音量；靜音模式下不會發聲。</li>
</ul>
<div class="tip">🛡️ 每加一道菜或結帳都會即時寫入儲存，即使系統當機或斷電，已記帳的資料不會遺失。</div>

<div class="divider"></div>

<!-- ③  訂位管理 -->
<div id="sec-reservation" class="section-header">
  <div class="section-num">3</div>
  <div class="section-icon-lbl">📅 訂位管理</div>
</div>

<h3>月曆總覽</h3>
<ul>
  <li>每天格子顯示當日各時段的訂位桌數（例如：<code>18:00</code> <code>3</code> 表示 18:00 有 3 桌）。</li>
  <li><strong>左右滑動</strong>月曆可切換月份；頂部「<strong>今天</strong>」按鈕可快速跳回當日（非當月時呈灰色）。</li>
  <li>時段超過可見行數時可在格子內<strong>上下滑動</strong>查看。</li>
</ul>

<h3>當日時段格線（點擊日期進入）</h3>
<ul>
  <li>橫軸為桌號、縱軸為時段，一目瞭然掌握空桌狀況。</li>
  <li>點擊空白格子可<strong>新增訂位</strong>。</li>
  <li>點擊訂位方塊可<strong>編輯或刪除</strong>。</li>
  <li><strong>長按</strong>訂位方塊可拖曳至其他桌或調整時段。</li>
</ul>

<h3>訂位欄位說明</h3>
<table>
  <tr><th>欄位</th><th>說明</th></tr>
  <tr><td>桌號</td><td>從現有啟用中桌號選擇</td></tr>
  <tr><td>客人姓名</td><td>訂位人姓名（必填）</td></tr>
  <tr><td>聯絡電話</td><td>選填</td></tr>
  <tr><td>用餐人數</td><td>選填</td></tr>
  <tr><td>日期</td><td>訂位日期</td></tr>
  <tr><td>開始 / 結束時間</td><td>用餐時段（依設定時長自動帶入結束時間）</td></tr>
  <tr><td>重要度</td><td>一般（綠）/ 重要（黃）/ 非常重要（紅）</td></tr>
  <tr><td>備註</td><td>其他注意事項，選填</td></tr>
</table>
<div class="note">📌 月曆每行時段數可於「設定 → 訂位設定 → 月曆每行時段數」調整（1～4 個，預設 2）。</div>

<div class="divider"></div>

<!-- ④  菜單管理 -->
<div id="sec-menu" class="section-header">
  <div class="section-num">4</div>
  <div class="section-icon-lbl">🥩 菜單管理</div>
</div>

<h3>群組管理（右上角「群組」按鈕）</h3>
<ul>
  <li>可<strong>新增、修改名稱、手動排序、刪除</strong>菜單群組。</li>
  <li>刪除群組時若有品項，系統會顯示品項數量警告並需二次確認。</li>
</ul>

<h3>品項管理</h3>
<ul>
  <li>可<strong>新增、編輯（名稱 / 價格 / 群組）、刪除</strong>品項。</li>
  <li>右側開關切換<strong>啟用 / 停用</strong>——停用品項不在記帳頁顯示，但歷史訂單完整保留。</li>
  <li>上下箭頭調整品項顯示順序。</li>
</ul>
<div class="tip">💡 品項啟停用即時生效，無需重啟 App。</div>

<div class="divider"></div>

<!-- ⑤  桌號設定 -->
<div id="sec-table" class="section-header">
  <div class="section-num">5</div>
  <div class="section-icon-lbl">🪑 桌號設定</div>
</div>

<ul>
  <li>可<strong>新增、編輯（名稱 / 座位數 / 備註）、上下排序、刪除</strong>桌號。</li>
  <li>桌號名稱最多 <strong>20 個字</strong>。</li>
  <li>開關停用的桌號不出現在記帳頁，但歷史訂單仍保留桌號名稱快照。</li>
</ul>
<div class="warn">⚠️ 刪除桌號不會刪除歷史訂單，但新訂單將無法選取該桌號。</div>

<div class="divider"></div>

<!-- ⑥  報表 -->
<div id="sec-report" class="section-header">
  <div class="section-num">6</div>
  <div class="section-icon-lbl">📊 報表</div>
</div>

<h3>日期篩選</h3>
<table>
  <tr><th>選項</th><th>說明</th></tr>
  <tr><td>今日</td><td>今天的訂單</td></tr>
  <tr><td>昨天</td><td>昨日的訂單</td></tr>
  <tr><td>本週</td><td>本週一到今天</td></tr>
  <tr><td>本月</td><td>本月 1 號到今天</td></tr>
  <tr><td>今年</td><td>今年 1 月 1 日到今天</td></tr>
  <tr><td>全部</td><td>所有訂單</td></tr>
  <tr><td>自訂</td><td>手動選擇開始日期與結束日期，點「套用」生效</td></tr>
</table>

<h3>統計內容</h3>
<ul>
  <li>三張統計卡片：<strong>總營業額 / 總筆數 / 平均客單</strong>。</li>
  <li><strong>品項銷售排行</strong>（依數量排序）+ 圓餅圖。</li>
  <li><strong>群組銷售排行</strong>（依金額排序）+ 圓餅圖。</li>
  <li><strong>逐筆訂單明細</strong>（展開查看品項 / 單筆刪除）。</li>
</ul>
<div class="tip">💡 橫式螢幕時，排行清單在左、圓餅圖在右，空間更充裕。</div>

<h3>軟刪除</h3>
<ul>
  <li>訂單明細右側垃圾桶圖示：僅標記「已刪除」，資料不會消失。</li>
  <li>勾選右上角「<strong>已刪除</strong>」後，已刪除訂單才納入統計計算。</li>
</ul>

<h3>匯出 CSV <span class="tag tag-new">v1.2.6+</span></h3>
<ol class="steps">
  <li><span class="step-num">1</span><span>設定好日期篩選後，點右上角「<strong>匯出報表</strong>」。</span></li>
  <li><span class="step-num">2</span><span>選擇儲存位置，確認後即匯出。</span></li>
</ol>
<p>CSV 包含：檔頭 → 總覽 → 品項排行 → 群組排行 → 訂單明細，採 <strong>UTF-8 + BOM</strong> 格式，Excel / Google Sheets 直接開啟中文不亂碼。</p>

<h3>USB 報表列印 <span class="tag tag-new">v1.2.8+</span></h3>
<ul>
  <li>連接 USB 熱感印表機後，點「<strong>報表列印</strong>」按鈕送印目前篩選結果。</li>
  <li>若日期範圍超過 1 天且訂單超過 10 筆，系統會詢問是否列印訂單明細，可選擇「<strong>只印總覽</strong>」避免誤印大量紙張。</li>
  <li>印表機設定請至「設定 → 印表機 → 測試列印」確認連線正常。</li>
</ul>
<div class="note">📌 報表頂部若出現「🔔 今日仍有 N 桌訂單尚未結帳」提示卡，提醒您確認是否有遺漏的結帳。</div>

<div class="divider"></div>

<!-- ⑦  備份 -->
<div id="sec-backup" class="section-header">
  <div class="section-num">7</div>
  <div class="section-icon-lbl">💾 資料備份</div>
</div>

<h3>手動備份匯出 / 匯入</h3>
<table>
  <tr><th>動作</th><th>說明</th></tr>
  <tr><td><strong>備份匯出</strong></td><td>將全部訂單、菜單、桌號打包為 <code>.zip</code>，儲存至您指定位置</td></tr>
  <tr><td><strong>備份匯入</strong></td><td>選擇 <code>.zip</code> 還原資料；還原完成後 App 自動關閉，重新開啟後生效</td></tr>
</table>
<div class="warn">⚠️ 備份匯入會<strong>完整覆蓋</strong>現有資料庫，操作前請確認已匯出最新備份。<br>匯入前系統會自動建立一份安全備份（保留最新 5 份），萬一匯入結果不如預期仍可找回。</div>

<h3>自動儲存（閒置備份）<span class="tag tag-new">v1.2.5+</span></h3>
<table>
  <tr><th>項目</th><th>預設</th><th>可調範圍</th></tr>
  <tr><td>閒置觸發時間</td><td>5 分鐘</td><td>1 / 3 / 5 / 10 / 15 / 30 / 60 分鐘</td></tr>
  <tr><td>保留天數</td><td>3 天</td><td>1 / 3 / 5 / 7 / 14 / 30 天</td></tr>
  <tr><td>備份位置</td><td>系統「下載／火鍋店POS備份」</td><td>可指定任何資料夾</td></tr>
</table>
<ul>
  <li>每天最多 1 份，同日觸發會覆寫；切入背景時會立即執行一次備份。</li>
  <li><strong>App 解除安裝後，預設目錄的備份檔仍會保留</strong>，可重裝後還原。</li>
  <li>設定頁備份列表可直接點「還原」或「刪除」。</li>
</ul>
<div class="tip">💡 若想讓備份自動同步到雲端，可將備份資料夾設為 Google Drive 或 OneDrive 的本地同步資料夾。</div>

<div class="divider"></div>

<!-- ⑧  設定 -->
<div id="sec-settings" class="section-header">
  <div class="section-num">8</div>
  <div class="section-icon-lbl">⚙️ 設定</div>
</div>

<h3>修改 PIN 碼</h3>
<ol class="steps">
  <li><span class="step-num">1</span><span>輸入目前 PIN 碼。</span></li>
  <li><span class="step-num">2</span><span>輸入新 PIN 碼（4 位數字）。</span></li>
  <li><span class="step-num">3</span><span>再次輸入確認，點「確認修改」完成。</span></li>
</ol>

<h3>功能頁面開關</h3>
<p>可個別啟用 / 停用「訂位」「菜單管理」「桌號設定」「報表」分頁。「<strong>記帳</strong>」與「<strong>設定</strong>」為必要分頁，無法關閉。</p>

<h3>點餐操作</h3>
<table>
  <tr><th>項目</th><th>說明</th></tr>
  <tr><td>觸覺回饋（震動）</td><td>點選品項與長按連續加減時提供震動，預設開啟</td></tr>
  <tr><td>連續計數速度</td><td>長按 +/- 時的觸發間隔（預設 100ms，可調 30～500ms）</td></tr>
  <tr><td>長按啟動延遲</td><td>長按多久後開始連續計數（預設 1.0 秒）</td></tr>
</table>

<h3>訂位設定</h3>
<table>
  <tr><th>項目</th><th>說明</th></tr>
  <tr><td>營業時間</td><td>訂位可選擇的時段範圍</td></tr>
  <tr><td>中間休息時間</td><td>啟用後可設定不接受訂位的休息時段</td></tr>
  <tr><td>預設用餐時間</td><td>新增訂位時自動帶入的用餐時長</td></tr>
  <tr><td>月曆每行時段數</td><td>月曆格子每列顯示幾個時段（1～4 個）</td></tr>
</table>

<h3>印表機</h3>
<p>透過 USB 連接 ESC/POS 熱感印表機（如 EPSON TM-T70），點「<strong>測試列印</strong>」確認連線正常後即可使用收據列印與報表列印功能。</p>

<h3>資料庫初始化</h3>
<p>清除全部訂單、菜單與桌號，恢復系統預設資料（需<strong>兩步驟確認</strong>）。</p>
<div class="warn">⚠️ 初始化操作<strong>無法復原</strong>，請務必先執行備份匯出或確認自動儲存已有最新備份。</div>

<div class="divider"></div>

<!-- ❓  常見問題 -->
<div id="sec-faq" class="section-header">
  <div class="section-num">？</div>
  <div class="section-icon-lbl">❓ 常見問題</div>
</div>

<div class="faq-item">
  <div class="faq-q">忘記 PIN 碼怎麼辦？</div>
  <div class="faq-a">目前無法透過 App 重設密碼。若有備份檔，可重裝 App 後匯入備份；若無備份，重裝後資料將遺失。</div>
</div>

<div class="faq-item">
  <div class="faq-q">品項在記帳頁消失了？</div>
  <div class="faq-a">前往「菜單管理」確認該品項的開關是否已被停用，啟用後即可恢復。</div>
</div>

<div class="faq-item">
  <div class="faq-q">桌號在記帳頁不見了？</div>
  <div class="faq-a">前往「桌號設定」確認桌號是否已被停用或刪除，停用狀態下重新開啟即可。</div>
</div>

<div class="faq-item">
  <div class="faq-q">報表數字看起來不對？</div>
  <div class="faq-a">請確認：① 日期篩選範圍是否正確；② 右上角「已刪除」的勾選狀態是否符合預期。</div>
</div>

<div class="faq-item">
  <div class="faq-q">備份匯入後資料消失？</div>
  <div class="faq-a">備份匯入會完整覆蓋現有資料。系統在匯入前已自動建立安全備份（保留最新 5 份），可至「設定 → 資料備份 → 備份匯入」找回匯入前的備份。</div>
</div>

<div class="faq-item">
  <div class="faq-q">當機或斷電後當天資料不見？</div>
  <div class="faq-a">v1.2.2 起採用 crash-safe 資料庫設計，每筆操作即時寫入。若有遺失，請至「設定 → 自動儲存」從備份清單還原，或至手機「下載 → 火鍋店POS備份」資料夾取得備份檔。</div>
</div>

<div class="faq-item">
  <div class="faq-q">找不到自動備份檔？</div>
  <div class="faq-a">開啟手機檔案管理員，進入「內部儲存 → Download → 火鍋店POS備份」資料夾，或於 App「設定 → 自動儲存」中的備份列表直接操作。</div>
</div>

<div class="faq-item">
  <div class="faq-q">USB 印表機無法列印？</div>
  <div class="faq-a">確認 USB 線連接正常，並於「設定 → 印表機 → 測試列印」重新測試連線，授權彈窗請點允許。</div>
</div>

</div><!-- /section -->

<div class="footer">🍲 火鍋店 POS v1.2.8 &nbsp;·&nbsp; Android 10+<br>如需協助請聯繫系統管理員</div>

<!-- Back to top -->
<a href="#top" class="back-to-top">↑</a>

</body>
</html>
""".trimIndent()

