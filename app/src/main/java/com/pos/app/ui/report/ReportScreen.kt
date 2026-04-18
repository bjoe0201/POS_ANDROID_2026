package com.pos.app.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.ui.theme.Red700
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onGoSettings: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // 刪除確認對話框
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    if (confirmDeleteId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("確認刪除") },
            text = { Text("此訂單將標記為「已刪除」，不計入統計。確定繼續？") },
            confirmButton = {
                Button(
                    onClick = { viewModel.softDeleteOrder(confirmDeleteId!!); confirmDeleteId = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("取消") } }
        )
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("報表", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onGoSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Red700, titleContentColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date range selector + 已刪除勾選
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateRange.entries.forEach { range ->
                        val label = when (range) {
                            DateRange.TODAY -> "今日"
                            DateRange.WEEK -> "本週"
                            DateRange.MONTH -> "本月"
                            DateRange.ALL -> "全部"
                        }
                        FilterChip(
                            selected = uiState.dateRange == range,
                            onClick = { viewModel.setDateRange(range) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Red700,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.showDeleted,
                            onCheckedChange = { viewModel.toggleShowDeleted() },
                            colors = CheckboxDefaults.colors(checkedColor = Red700)
                        )
                        Text("已刪除", fontSize = 13.sp)
                    }
                }
            }

            // Summary cards
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("總營業額", "NT$ %.0f".format(uiState.totalRevenue), modifier = Modifier.weight(1f))
                    SummaryCard("總筆數", "${uiState.totalOrders} 筆", modifier = Modifier.weight(1f))
                    SummaryCard("平均客單", "NT$ %.0f".format(uiState.avgOrderValue), modifier = Modifier.weight(1f))
                }
            }

            // Item ranking
            if (uiState.itemRanking.isNotEmpty()) {
                item { Text("品項銷售排行", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Red700) }
                items(uiState.itemRanking.take(10)) { (name, qty) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontSize = 14.sp)
                        Text("$qty 份", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Red700)
                    }
                }
                item { HorizontalDivider() }
            }

            // Order list
            item { Text("訂單明細（${uiState.totalOrders} 筆）", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Red700) }
            if (uiState.isLoading) {
                item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
            } else if (uiState.orders.isEmpty()) {
                item { Text("此期間無訂單", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(8.dp)) }
            } else {
                items(uiState.orders, key = { it.order.id }) { owi ->
                    OrderSummaryRow(
                        owi = owi,
                        sdf = sdf,
                        onDelete = { confirmDeleteId = owi.order.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Red700)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderSummaryRow(owi: OrderWithItems, sdf: SimpleDateFormat, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val isDeleted = owi.order.isDeleted
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (isDeleted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                            color = if (isDeleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                        )
                        if (isDeleted) {
                            Text("已刪除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(sdf.format(Date(owi.order.createdAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Text(
                    "NT$ %.0f".format(owi.items.sumOf { it.price * it.quantity }),
                    fontWeight = FontWeight.Bold,
                    color = if (isDeleted) MaterialTheme.colorScheme.outline else Red700
                )
                if (!isDeleted) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "刪除",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                owi.items.forEach { item ->
                    Text(
                        "  ${item.name} × ${item.quantity}  =  NT$ %.0f".format(item.price * item.quantity),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
