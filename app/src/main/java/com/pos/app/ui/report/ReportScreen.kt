package com.pos.app.ui.report

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.pos.app.ui.theme.LocalPosColors
import com.pos.app.ui.theme.PosColors
import com.pos.app.util.UsbPrinterManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onGoSettings: () -> Unit,
    appVersion: String,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val t = LocalPosColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val dateSdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val fileNameSdf = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showReportDetailPrintDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val onReportPrintClick: () -> Unit = {
        if (viewModel.shouldConfirmReportDetailPrint()) {
            showReportDetailPrintDialog = true
        } else {
            viewModel.printCurrentReport(context, includeOrderDetails = true)
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.exportCsv(context, it) }
    }

    val suggestedFileName: () -> String = {
        val now = System.currentTimeMillis()
        val start = uiState.customStartDate ?: now
        val end = uiState.customEndDate ?: now
        val label = when (uiState.dateRange) {
            DateRange.TODAY -> "today_${fileNameSdf.format(Date(now))}"
            DateRange.YESTERDAY -> "yesterday_${fileNameSdf.format(Date(now - 86_400_000L))}"
            DateRange.WEEK -> "week_${fileNameSdf.format(Date(now))}"
            DateRange.MONTH -> "month_${fileNameSdf.format(Date(now))}"
            DateRange.YEAR -> "year_${fileNameSdf.format(Date(now))}"
            DateRange.ALL -> "all_${fileNameSdf.format(Date(now))}"
            DateRange.CUSTOM -> "${fileNameSdf.format(Date(start))}_${fileNameSdf.format(Date(end))}"
        }
        "report_$label.csv"
    }

    if (showStartDatePicker) {
        val startPickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.customStartDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startPickerState.selectedDateMillis?.let { viewModel.setCustomStartDate(it) }
                    showStartDatePicker = false
                }) { Text("確定", color = t.accent) }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("取消", color = t.textSub) } }
        ) {
            DatePicker(state = startPickerState)
        }
    }

    if (showEndDatePicker) {
        val endPickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.customEndDate ?: uiState.customStartDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endPickerState.selectedDateMillis?.let { viewModel.setCustomEndDate(it) }
                    showEndDatePicker = false
                }) { Text("確定", color = t.accent) }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("取消", color = t.textSub) } }
        ) {
            DatePicker(state = endPickerState)
        }
    }

    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    if (confirmDeleteId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            containerColor = t.surface,
            title = { Text("確認刪除", color = t.text, fontWeight = FontWeight.Bold) },
            text = { Text("此訂單將標記為「已刪除」，不計入統計。確定繼續？", color = t.textSub) },
            confirmButton = {
                Button(
                    onClick = { viewModel.softDeleteOrder(confirmDeleteId!!); confirmDeleteId = null },
                    colors = ButtonDefaults.buttonColors(containerColor = t.error)
                ) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("取消", color = t.textSub) } }
        )
    }

    if (showReportDetailPrintDialog) {
        AlertDialog(
            onDismissRequest = { showReportDetailPrintDialog = false },
            containerColor = t.surface,
            title = { Text("列印明細確認", color = t.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "目前範圍超過 1 天，且共有 ${uiState.totalOrders} 筆訂單。\n\n列印訂單明細可能會印出很多紙，是否要包含「訂單明細」？",
                    color = t.textSub
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDetailPrintDialog = false
                        viewModel.printCurrentReport(context, includeOrderDetails = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = t.accent)
                ) { Text("列印明細") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showReportDetailPrintDialog = false }) {
                        Text("取消", color = t.textSub)
                    }
                    TextButton(
                        onClick = {
                            showReportDetailPrintDialog = false
                            viewModel.printCurrentReport(context, includeOrderDetails = false)
                        }
                    ) {
                        Text("只印總覽", color = t.accent)
                    }
                }
            }
        )
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
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
                        Text("報表", color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("v$appVersion · 銷售統計", color = t.textMuted, fontSize = 11.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date range selector
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DateRange.entries.forEach { range ->
                                val label = when (range) {
                                    DateRange.TODAY -> "今日"
                                    DateRange.YESTERDAY -> "昨天"
                                    DateRange.WEEK -> "本週"
                                    DateRange.MONTH -> "本月"
                                    DateRange.YEAR -> "今年"
                                    DateRange.ALL -> "全部"
                                    DateRange.CUSTOM -> "自訂"
                                }
                                ReportChip(
                                    label = label,
                                    active = uiState.dateRange == range,
                                    onClick = { viewModel.setDateRange(range) },
                                    t = t
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.showDeleted,
                                    onCheckedChange = { viewModel.toggleShowDeleted() },
                                    colors = CheckboxDefaults.colors(checkedColor = t.accent)
                                )
                                Text("已刪除", fontSize = 12.sp, color = t.textMuted)
                            }
                        }

                        if (uiState.dateRange == DateRange.CUSTOM) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { showStartDatePicker = true },
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            uiState.customStartDate?.let { dateSdf.format(Date(it)) } ?: "開始日期",
                                            color = t.textSub, fontSize = 13.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { showEndDatePicker = true },
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            uiState.customEndDate?.let { dateSdf.format(Date(it)) } ?: "結束日期",
                                            color = t.textSub, fontSize = 13.sp
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.applyCustomDateRange() },
                                        enabled = uiState.customStartDate != null && uiState.customEndDate != null,
                                        colors = ButtonDefaults.buttonColors(containerColor = t.accent, disabledContainerColor = t.border),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("套用", fontSize = 13.sp)
                                    }
                                }
                                ReportActionButtons(
                                    uiState = uiState,
                                    onPrint = onReportPrintClick,
                                    onExport = { exportCsvLauncher.launch(suggestedFileName()) },
                                    t = t
                                )
                            }
                        } else {
                            // 非自訂模式也提供匯出入口
                            ReportActionButtons(
                                uiState = uiState,
                                onPrint = onReportPrintClick,
                                onExport = { exportCsvLauncher.launch(suggestedFileName()) },
                                t = t
                            )
                        }
                    }
                }

                // Stat cards 3-column grid
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("總營業額", "NT${"$"}%.0f".format(uiState.totalRevenue), modifier = Modifier.weight(1f), t = t)
                        StatCard("總筆數", "${uiState.totalOrders} 筆", modifier = Modifier.weight(1f), t = t)
                        StatCard("平均客單", "NT${"$"}%.0f".format(uiState.avgOrderValue), modifier = Modifier.weight(1f), t = t)
                    }
                }

                // Item ranking
                if (uiState.itemRanking.isNotEmpty()) {
                    item {
                        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                        val topItems = uiState.itemRanking.take(10)
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.card).border(1.dp, t.border, RoundedCornerShape(12.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("品項銷售排行", color = t.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val listContent: @Composable ColumnScope.() -> Unit = {
                                topItems.forEachIndexed { idx, (name, qty) ->
                                    val maxQty = topItems.firstOrNull()?.second?.toFloat() ?: 1f
                                    val fraction = qty / maxQty
                                    val sliceColor = t.chartBars.getOrElse(idx) { t.accent }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(sliceColor)
                                                )
                                                Text("${idx + 1}", color = sliceColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                                Text(name, color = t.text, fontSize = 14.sp)
                                            }
                                            Text("$qty 份", color = t.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.border)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(sliceColor)
                                            )
                                        }
                                    }
                                }
                            }
                            val pieValues = topItems.map { it.second.toDouble() }
                            val pieColors = topItems.indices.map { idx -> t.chartBars.getOrElse(idx) { t.accent } }
                            if (isLandscape) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        content = listContent
                                    )
                                    PieChart(
                                        values = pieValues,
                                        colors = pieColors,
                                        modifier = Modifier.size(200.dp)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = listContent)
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PieChart(
                                        values = pieValues,
                                        colors = pieColors,
                                        modifier = Modifier.size(200.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Group ranking
                if (uiState.groupRanking.isNotEmpty()) {
                    item {
                        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.card).border(1.dp, t.border, RoundedCornerShape(12.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("群組銷售排行", color = t.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val maxRevenue = uiState.groupRanking.firstOrNull()?.revenue?.toFloat() ?: 1f
                            val listContent: @Composable ColumnScope.() -> Unit = {
                                uiState.groupRanking.forEachIndexed { idx, group ->
                                    val fraction = (group.revenue / maxRevenue).toFloat()
                                    val sliceColor = t.chartBars.getOrElse(idx) { t.accent }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(sliceColor)
                                                )
                                                Column {
                                                    Text(group.groupName, color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    Text("${group.quantity} 份", color = t.textMuted, fontSize = 12.sp)
                                                }
                                            }
                                            Text("NT${"$"}%.0f".format(group.revenue), color = t.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.border)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(sliceColor)
                                            )
                                        }
                                    }
                                }
                            }
                            val pieValues = uiState.groupRanking.map { it.revenue }
                            val pieColors = uiState.groupRanking.indices.map { idx -> t.chartBars.getOrElse(idx) { t.accent } }
                            if (isLandscape) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        content = listContent
                                    )
                                    PieChart(
                                        values = pieValues,
                                        colors = pieColors,
                                        modifier = Modifier.size(200.dp)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = listContent)
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PieChart(
                                        values = pieValues,
                                        colors = pieColors,
                                        modifier = Modifier.size(200.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Order list header
                item {
                    Text(
                        "訂單明細（${uiState.totalOrders} 筆）",
                        color = t.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }

                if (uiState.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = t.accent)
                        }
                    }
                } else if (uiState.orders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("此期間無訂單", color = t.textMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(uiState.orders, key = { it.order.id }) { owi ->
                        OrderSummaryRow(
                            owi = owi,
                            sdf = sdf,
                            onDelete = { confirmDeleteId = owi.order.id },
                            printDetailEnabled = uiState.printDetailEnabled,
                            t = t
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportActionButtons(
    uiState: ReportUiState,
    onPrint: () -> Unit,
    onExport: () -> Unit,
    t: PosColors
) {
    val actionEnabled = uiState.orders.isNotEmpty() && !uiState.isLoading && !uiState.isPrintingReport
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrint,
            enabled = actionEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = t.accent, disabledContainerColor = t.border),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(if (uiState.isPrintingReport) "列印中…" else "報表列印", fontSize = 13.sp)
        }
        Button(
            onClick = onExport,
            enabled = actionEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = t.accent, disabledContainerColor = t.border),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("匯出報表", fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReportChip(label: String, active: Boolean, onClick: () -> Unit, t: PosColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) t.accent else t.card)
            .border(1.dp, if (active) t.accent else t.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (active) Color.White else t.textSub, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, t: PosColors) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(t.card)
            .border(1.dp, t.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label.uppercase(), fontSize = 11.sp, color = t.textMuted, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = t.accent)
    }
}

@Composable
private fun OrderSummaryRow(
    owi: OrderWithItems,
    sdf: SimpleDateFormat,
    onDelete: () -> Unit,
    printDetailEnabled: Boolean,
    t: PosColors
) {
    var expanded by remember { mutableStateOf(false) }
    val isDeleted = owi.order.isDeleted
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDeleted) t.error.copy(alpha = 0.06f) else t.card)
            .border(1.dp, if (isDeleted) t.error.copy(0.2f) else t.border, RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "#${owi.order.id}  ${owi.order.tableName}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        textDecoration = if (isDeleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isDeleted) t.textMuted else t.text
                    )
                    if (isDeleted) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(t.error.copy(0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("已刪除", fontSize = 10.sp, color = t.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(sdf.format(Date(owi.order.createdAt)), fontSize = 12.sp, color = t.textMuted)
            }
            Text(
                "NT${"$"}%.0f".format(owi.items.sumOf { it.price * it.quantity }),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isDeleted) t.textMuted else t.accent
            )
            if (!isDeleted) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "刪除",
                        tint = t.error, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))
            Spacer(Modifier.height(8.dp))
            owi.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${item.name} × ${item.quantity}", fontSize = 13.sp, color = t.textSub)
                    Text("NT${"$"}%.0f".format(item.price * item.quantity), fontSize = 13.sp, color = t.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            if (printDetailEnabled && !isDeleted) {
                Spacer(Modifier.height(10.dp))
                val total = owi.items.sumOf { it.price * it.quantity }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            UsbPrinterManager.printOrderDetail(
                                context = context,
                                orderId = owi.order.id,
                                tableName = owi.order.tableName,
                                createdAt = owi.order.createdAt,
                                items = owi.items,
                                total = total
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, t.accent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("列印明細", color = t.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 圓餅圖：依傳入的 values 以對應 colors 繪製扇形，順時針由 12 點鐘方向開始。
 * values 為 0 或負值時畫成灰色整圓佔位。
 */
@Composable
private fun PieChart(
    values: List<Double>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val total = values.sumOf { if (it > 0) it else 0.0 }
    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height)
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)
        if (total <= 0.0) {
            drawArc(
                color = Color.Gray.copy(alpha = 0.3f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )
            return@Canvas
        }
        var startAngle = -90f
        values.forEachIndexed { idx, v ->
            if (v <= 0.0) return@forEachIndexed
            val sweep = (v / total * 360.0).toFloat()
            drawArc(
                color = colors.getOrElse(idx) { colors.lastOrNull() ?: Color.Gray },
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )
            startAngle += sweep
        }
    }
}
