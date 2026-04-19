package com.pos.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.ui.theme.Red700
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val timestamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) }

    // 還原確認對話框
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("確認備份匯入") },
            text = { Text("還原後 App 會自動關閉，重新開啟後生效。\n目前的資料將被備份檔覆蓋，確定繼續？") },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri!!
                        pendingRestoreUri = null
                        viewModel.restoreDb(context, uri)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red700)
                ) { Text("確定還原") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") }
            }
        )
    }

    // 資料庫初始化：兩步確認
    var showResetStep1 by remember { mutableStateOf(false) }
    var showResetStep2 by remember { mutableStateOf(false) }
    if (showResetStep1) {
        AlertDialog(
            onDismissRequest = { showResetStep1 = false },
            title = { Text("初始化前請先備份") },
            text = { Text("初始化將清除所有訂單、菜單與桌號資料，並恢復為預設內容。\n\n⚠️ 請先執行「備份匯出」以保留目前資料，再繼續初始化。\n\n已備份，要繼續？") },
            confirmButton = {
                Button(
                    onClick = { showResetStep1 = false; showResetStep2 = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Red700)
                ) { Text("已備份，繼續") }
            },
            dismissButton = {
                TextButton(onClick = { showResetStep1 = false }) { Text("取消") }
            }
        )
    }

    // 資料庫初始化：第二步確認（最終確認）
    if (showResetStep2) {
        AlertDialog(
            onDismissRequest = { showResetStep2 = false },
            title = { Text("確認初始化資料庫") },
            text = { Text("此操作無法復原！\n\n所有訂單、菜單與桌號資料將被清除，並恢復為系統預設值。\n\n確定要初始化資料庫嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetStep2 = false
                        viewModel.resetDatabase()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red700)
                ) { Text("確定初始化") }
            },
            dismissButton = {
                TextButton(onClick = { showResetStep2 = false }) { Text("取消") }
            }
        )
    }

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
        topBar = {
            TopAppBar(
                title = { Text("設定", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Red700,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("修改 PIN 碼", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Red700)

            if (uiState.isDefaultPin) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "目前使用預設密碼 1234，請盡快修改",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }
            }

            ChangePinCard(viewModel = viewModel, snackbarHostState = snackbarHostState)

            HorizontalDivider()

            Text("資料備份", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Red700)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { backupLauncher.launch("火鍋店POS備份_$timestamp.zip") },
                    modifier = Modifier.weight(1f)
                ) { Text("備份匯出") }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.weight(1f)
                ) { Text("備份匯入") }
            }

            HorizontalDivider()

            Text("資料庫管理", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Red700)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "初始化將清除全部訂單、菜單與桌號，並恢復系統預設資料。\n建議先執行備份匯出再操作。",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp
                )
            }
            Button(
                onClick = { showResetStep1 = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Red700)
            ) { Text("初始化資料庫") }
        }
    }
}

@Composable
private fun ChangePinCard(viewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                Triple("目前 PIN 碼", currentPin, { v: String -> currentPin = v }),
                Triple("新 PIN 碼", newPin, { v: String -> newPin = v }),
                Triple("確認新 PIN 碼", confirmPin, { v: String -> confirmPin = v })
            ).forEach { (label, value, onChange) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { onChange(it); errorMsg = "" } },
                    label = { Text(label) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = Red700),
                enabled = currentPin.length == 4 && newPin.length == 4 && confirmPin.length == 4
            ) {
                Text("確認修改")
            }
        }
    }
}
