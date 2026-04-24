package com.pos.app.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.util.SoundEffects
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.ui.theme.LocalPosColors
import com.pos.app.ui.theme.PosColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    onGoSettings: () -> Unit,
    appVersion: String,
    viewModel: OrderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val t = LocalPosColors.current
    var showCheckout by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var lastCheckout by remember { mutableStateOf<Pair<String, Double>?>(null) }
    val dateFormatter = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Row {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { viewModel.updateSelectedDate(it) }
                        showDatePicker = false
                    }) { Text("確定") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updateSelectedDate(System.currentTimeMillis())
                    showDatePicker = false
                }) { Text("今天") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showCheckout) {
        CheckoutDialog(
            tableName = uiState.selectedTable?.tableName ?: "",
            orderItems = uiState.orderItems,
            total = uiState.total,
            remark = uiState.remark,
            selectedDate = uiState.selectedDate,
            onRemarkChange = { viewModel.updateRemark(it) },
            onConfirm = {
                val tName = uiState.selectedTable?.tableName
                val tTotal = uiState.total
                viewModel.payOrder {
                    showCheckout = false
                    lastCheckout = tName?.let { n -> n to tTotal }
                    // DB 寫入完成後發出完成音效
                    SoundEffects.playPaymentSuccess()
                }
            },
            onDismiss = { showCheckout = false },
            t = t
        )
    }
    if (showCancel && uiState.order != null) {
        AlertDialog(
            onDismissRequest = { showCancel = false },
            containerColor = t.surface,
            title = { Text("取消訂單", color = t.text, fontWeight = FontWeight.Bold) },
            text = { Text("確定取消 ${uiState.selectedTable?.tableName} 的所有點餐？", color = t.textSub) },
            confirmButton = { TextButton(onClick = { viewModel.cancelOrder(); showCancel = false }) { Text("確定", color = t.error) } },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("返回", color = t.textSub) } }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(t.bg)) {
        val compact = maxHeight < 900.dp || maxWidth > maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
        // TopBar
            PosTopBar(title = "記帳點餐", subtitle = "火鍋店 POS", compact = compact, t = t, rightContent = {
            lastCheckout?.let { (name, amt) ->
                Text("✓ $name NT${"$"}${amt.toLong()}", color = t.success, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            }
            val todayStart = remember { startOfDay(System.currentTimeMillis()) }
            val isToday = uiState.selectedDate == todayStart
            val dateLabel = if (isToday) "今天" else dateFormatter.format(Date(uiState.selectedDate))
            val dateTint = if (isToday) t.textSub else t.error
            TextButton(onClick = { showDatePicker = true }) {
                Text(dateLabel, color = dateTint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        })

        // Table selector
            TableSelectorRow(tables = uiState.tables, selected = uiState.selectedTable, totals = uiState.openOrderTotals, compact = compact, onSelect = { viewModel.selectTable(it) }, t = t)

        // Main area
            Row(modifier = Modifier.fillMaxSize()) {
            // Left: categories + menu
                Column(modifier = Modifier.weight(1.62f).fillMaxHeight()) {
                // Category chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.background(t.bg)
                ) {
                    items(uiState.groups, key = { it.code }) { group ->
                        PosChip(label = group.name, active = uiState.selectedCategory == group.code, compact = compact, onClick = { viewModel.selectCategory(group.code) }, t = t)
                    }
                }
                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))
                // Menu grid
                if (uiState.menuItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("此分類目前沒有可用品項", color = t.textMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (compact) 118.dp else 130.dp),
                        contentPadding = PaddingValues(if (compact) 8.dp else 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.menuItems, key = { it.id }) { item ->
                            MenuCard(item = item, qty = viewModel.getQuantityInOrder(item.id), compact = compact, onAdd = { viewModel.addItem(item) }, onRemove = { viewModel.removeItem(item) }, t = t)
                        }
                    }
                }
            }
            // Vertical divider
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(t.border))
            // Right: order panel
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                OrderPanel(
                    table = uiState.selectedTable, order = uiState.orderItems, total = uiState.total,
                    remark = uiState.remark, onRemarkChange = { viewModel.updateRemark(it) },
                    onDelete = { viewModel.deleteOrderItem(it) }, onCancel = { showCancel = true },
                    onCheckout = { showCheckout = true }, compact = compact, t = t
                )
            }
        }
        }
    }
}

@Composable
private fun PosTopBar(title: String, subtitle: String, compact: Boolean, t: PosColors, rightContent: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 56.dp).background(t.topbar).padding(horizontal = if (compact) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.width(4.dp).height(if (compact) 20.dp else 24.dp).clip(RoundedCornerShape(2.dp)).background(t.accent))
            Column {
                Text(title, color = t.text, fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 16.sp, letterSpacing = 0.02.sp)
                Text(subtitle, color = t.textMuted, fontSize = if (compact) 10.sp else 11.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = rightContent)
    }
}

@Composable
private fun TableSelectorRow(tables: List<TableEntity>, selected: TableEntity?, totals: Map<Long, Double>, compact: Boolean, onSelect: (TableEntity) -> Unit, t: PosColors) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.background(t.surface).fillMaxWidth()
    ) {
        item {
            Text("桌號", color = t.textMuted, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 4.dp).wrapContentHeight(Alignment.CenterVertically))
        }
        items(tables, key = { it.id }) { table ->
            val sel = table.id == selected?.id
            val tableTotal = totals[table.id] ?: 0.0
            val occ = tableTotal > 0.0
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(when { sel -> t.accent; occ -> t.occupiedBg; else -> t.card })
                    .border(1.dp, when { sel -> t.accent; occ -> t.occupied; else -> t.border }, RoundedCornerShape(10.dp))
                    .clickable { onSelect(table) }
                    .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 5.dp else 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(table.tableName, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (compact) 12.sp else 13.sp,
                    color = if (sel) Color.White else if (occ) t.occupied else t.textSub)
                if (occ) {
                    Text("NT${"$"}${tableTotal.toLong()}", fontSize = if (compact) 9.sp else 10.sp, fontWeight = FontWeight.Bold,
                        color = if (sel) Color.White.copy(0.85f) else t.occupied)
                }
            }
        }
    }
}

@Composable
private fun PosChip(label: String, active: Boolean, compact: Boolean, onClick: () -> Unit, t: PosColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) t.accent else t.card)
            .border(1.dp, if (active) t.accent else t.border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = if (compact) 13.dp else 16.dp, vertical = if (compact) 5.dp else 6.dp)
    ) {
        Text(label, color = if (active) Color.White else t.textSub, fontSize = if (compact) 12.sp else 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun MenuCard(item: MenuItemEntity, qty: Int, compact: Boolean, onAdd: () -> Unit, onRemove: () -> Unit, t: PosColors) {
    val active = qty > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) t.accentDim else t.card)
            .border(1.dp, if (active) t.accent else t.border, RoundedCornerShape(12.dp))
            .clickable { onAdd() }
            .padding(if (compact) 8.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Text(item.name, color = if (active) t.accent else t.text, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = if (compact) 13.sp else 14.sp, maxLines = 2)
        Text("NT${"$"}${item.price.toLong()}", color = t.accent, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.ExtraBold)
        if (active) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable(false) {}) {
                QtyBtn(label = "−", onClick = onRemove, t = t)
                Text("$qty", color = t.accent, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 14.sp else 16.sp, modifier = Modifier.widthIn(min = 20.dp))
                QtyBtn(label = "+", onClick = onAdd, t = t)
            }
        } else {
            Text("點選加入", color = t.textMuted, fontSize = if (compact) 10.sp else 11.sp)
        }
    }
}

@Composable
private fun QtyBtn(label: String, onClick: () -> Unit, t: PosColors) {
    Box(
        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(t.accentDim2).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = t.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun OrderPanel(table: TableEntity?, order: List<OrderItemEntity>, total: Double, remark: String, onRemarkChange: (String) -> Unit, onDelete: (OrderItemEntity) -> Unit, onCancel: () -> Unit, onCheckout: () -> Unit, compact: Boolean, t: PosColors) {
    val itemCount = order.size
    val quantityCount = order.sumOf { it.quantity }

    Column(modifier = Modifier.fillMaxHeight()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(table?.tableName ?: "尚未選桌", color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("共 $itemCount 項目 $quantityCount 件", color = t.textMuted, fontSize = if (compact) 11.sp else 12.sp)
                }
                table?.seats?.let { Text("($it 人桌)", color = t.textMuted, fontSize = 12.sp) }
            }
            if (order.isNotEmpty()) {
                TextButton(onClick = onCancel) { Text("取消訂單", color = t.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))

        // Items
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (order.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 24.dp else 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛒", fontSize = 32.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("尚未點餐", color = t.textMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(order, key = { it.id }) { oi ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(oi.name, color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("NT${"$"}${oi.price.toLong()} × ${oi.quantity} = NT${"$"}${(oi.price * oi.quantity).toLong()}",
                                color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onDelete(oi) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "刪除", tint = t.error, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))
                }
            }
        }

        // Bottom: remark + total + button
        Column(
            modifier = Modifier.background(t.surface).padding(if (compact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            OutlinedTextField(
                value = remark, onValueChange = onRemarkChange,
                placeholder = { Text("備註（選填）", color = t.textMuted, fontSize = 13.sp) },
                maxLines = 2, modifier = Modifier.fillMaxWidth().heightIn(max = if (compact) 64.dp else 72.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
                    focusedTextColor = t.text, unfocusedTextColor = t.text,
                    cursorColor = t.accent
                ), shape = RoundedCornerShape(8.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("合計", color = t.textSub, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("NT${"$"}${total.toLong()}", color = t.accent, fontSize = if (compact) 24.sp else 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Button(
                onClick = onCheckout,
                enabled = order.isNotEmpty() && table != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = t.accent, disabledContainerColor = t.border),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("送出結帳 →", fontSize = if (compact) 15.sp else 17.sp, modifier = Modifier.padding(vertical = if (compact) 2.dp else 4.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CheckoutDialog(tableName: String, orderItems: List<OrderItemEntity>, total: Double, remark: String, selectedDate: Long, onRemarkChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, t: PosColors) {
    val quantityCount = orderItems.sumOf { it.quantity }
    val todayStart = remember { startOfDay(System.currentTimeMillis()) }
    val isToday = selectedDate == todayStart
    val dateLabel = if (isToday) "今天" else SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(selectedDate))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("確認結帳", color = t.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (isToday) t.accentDim else t.error.copy(alpha = 0.15f))
                        .border(1.dp, if (isToday) t.accent else t.error, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(dateLabel, color = if (isToday) t.accent else t.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(t.accentDim).border(1.dp, t.accent, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(tableName, color = t.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text("共 ${orderItems.size} 個品項 $quantityCount 件", color = t.textMuted, fontSize = 13.sp)
                }
                Column(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(t.bg).border(1.dp, t.border, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    orderItems.forEach { oi ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${oi.name} × ${oi.quantity}", color = t.text, fontSize = 14.sp)
                            Text("NT${"$"}${(oi.price * oi.quantity).toLong()}", color = t.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("應收金額", color = t.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("NT${"$"}${total.toLong()}", color = t.accent, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedTextField(
                    value = remark, onValueChange = onRemarkChange,
                    placeholder = { Text("備註（選填）", color = t.textMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.accent, unfocusedBorderColor = t.border, focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = t.accent), shape = RoundedCornerShape(10.dp)) { Text("✓ 確認收款", fontWeight = FontWeight.Bold) } },
        dismissButton = { OutlinedButton(onClick = onDismiss, border = androidx.compose.foundation.BorderStroke(1.dp, t.border), shape = RoundedCornerShape(10.dp)) { Text("返回修改", color = t.textSub) } }
    )
}
