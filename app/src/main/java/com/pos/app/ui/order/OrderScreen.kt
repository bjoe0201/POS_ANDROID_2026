package com.pos.app.ui.order

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.R
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.ui.theme.Red100
import com.pos.app.ui.theme.Red700

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    onGoSettings: () -> Unit,
    appVersion: String,
    viewModel: OrderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCheckout by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }

    if (showCheckout) {
        CheckoutDialog(
            tableName = uiState.selectedTable?.tableName ?: "",
            orderItems = uiState.orderItems,
            total = uiState.total,
            remark = uiState.remark,
            onRemarkChange = { viewModel.updateRemark(it) },
            onConfirm = {
                viewModel.payOrder { showCheckout = false }
            },
            onDismiss = { showCheckout = false }
        )
    }
    if (showCancel && uiState.order != null) {
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("取消訂單") },
            text = { Text("確定取消 ${uiState.selectedTable?.tableName} 的所有點餐？") },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelOrder(); showCancel = false }) {
                    Text("確定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("返回") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = Red700, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "記帳",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stringResource(R.string.app_version, appVersion),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 4.dp, bottom = 8.dp)
                    )
                    IconButton(onClick = onGoSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定", tint = Color.White)
                    }
                }
            }
        }

        // Table selector row
        TableSelectorRow(
            tables = uiState.tables,
            selected = uiState.selectedTable,
            openTableIds = emptySet(),
            onSelect = { viewModel.selectTable(it) }
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // Left: categories + menu grid
            Column(modifier = Modifier.weight(1.6f).fillMaxHeight()) {
                // Category tabs
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.groups, key = { it.code }) { group ->
                        FilterChip(
                            selected = uiState.selectedCategory == group.code,
                            onClick = { viewModel.selectCategory(group.code) },
                            label = { Text(group.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Red700,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Menu items grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.menuItems, key = { it.id }) { item ->
                        MenuItemButton(
                            item = item,
                            quantity = viewModel.getQuantityInOrder(item.id),
                            onAdd = { viewModel.addItem(item) },
                            onRemove = { viewModel.removeItem(item) }
                        )
                    }
                }
            }

            VerticalDivider()

            // Right: order summary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("訂單明細", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (uiState.order != null) {
                        TextButton(onClick = { showCancel = true }) {
                            Text("取消", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.orderItems, key = { it.id }) { item ->
                        OrderItemRow(item = item, onDelete = { viewModel.deleteOrderItem(item) })
                    }
                    if (uiState.orderItems.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("尚未點餐", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("合計", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "NT$ %.0f".format(uiState.total),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Red700
                    )
                }
                Button(
                    onClick = { showCheckout = true },
                    enabled = uiState.orderItems.isNotEmpty() && uiState.selectedTable != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Red700),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("送出結帳", fontSize = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun TableSelectorRow(
    tables: List<TableEntity>,
    selected: TableEntity?,
    openTableIds: Set<Long>,
    onSelect: (TableEntity) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        items(tables, key = { it.id }) { table ->
            val isSelected = table.id == selected?.id
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isSelected -> Red700
                    openTableIds.contains(table.id) -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                },
                tonalElevation = if (isSelected) 0.dp else 2.dp,
                modifier = Modifier.clickable { onSelect(table) }
            ) {
                Text(
                    text = table.tableName,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun MenuItemButton(
    item: MenuItemEntity,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quantity > 0) Red100 else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { onAdd() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                item.name,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 2
            )
            Text(
                "NT$ %.0f".format(item.price),
                fontSize = 13.sp,
                color = Red700,
                fontWeight = FontWeight.Bold
            )
            if (quantity > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "減", tint = Red700, modifier = Modifier.size(16.dp))
                    }
                    Text("$quantity", fontWeight = FontWeight.Bold, color = Red700)
                    IconButton(
                        onClick = onAdd,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "加", tint = Red700, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderItemRow(item: OrderItemEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${item.name} ×${item.quantity}", fontSize = 14.sp)
            Text("NT$ %.0f".format(item.price * item.quantity), fontSize = 12.sp, color = Red700)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutDialog(
    tableName: String,
    orderItems: List<OrderItemEntity>,
    total: Double,
    remark: String,
    onRemarkChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("確認結帳", fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Red700
                ) {
                    Text(
                        tableName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 訂單明細
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    orderItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${item.name} × ${item.quantity}",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "NT$ %.0f".format(item.price * item.quantity),
                                fontSize = 14.sp,
                                color = Red700
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 合計
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("合計", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "NT$ %.0f".format(total),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Red700
                    )
                }

                OutlinedTextField(
                    value = remark,
                    onValueChange = onRemarkChange,
                    label = { Text("備註（選填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Red700)) {
                Text("確認收款")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } }
    )
}
