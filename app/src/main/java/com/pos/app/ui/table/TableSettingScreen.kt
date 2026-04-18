package com.pos.app.ui.table

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.ui.theme.Red700

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableSettingScreen(viewModel: TableSettingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showDialog) {
        TableDialog(
            editingTable = uiState.editingTable,
            onSave = { name, seats, remark -> viewModel.saveTable(name, seats, remark) },
            onDismiss = { viewModel.dismissDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("桌號設定", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "新增桌號", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Red700,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            itemsIndexed(uiState.tables, key = { _, table -> table.id }) { index, table ->
                TableRow(
                    table = table,
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.tables.lastIndex,
                    onMoveUp = { viewModel.moveTableUp(table) },
                    onMoveDown = { viewModel.moveTableDown(table) },
                    onEdit = { viewModel.showEditDialog(table) },
                    onDelete = { viewModel.deleteTable(table) },
                    onToggle = { viewModel.toggleActive(table) }
                )
            }
            if (uiState.tables.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("尚無桌號，點擊右上角 + 新增", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    table: TableEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    table.tableName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (table.isActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline
                )
                val detail = buildString {
                    table.seats?.let { append("$it 人桌  ") }
                    table.remark?.let { append(it) }
                }
                if (detail.isNotBlank()) {
                    Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "上移", tint = Red700)
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "下移", tint = Red700)
                }
            }
            Switch(
                checked = table.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Red700,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "編輯", tint = Red700)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TableDialog(
    editingTable: TableEntity?,
    onSave: (String, Int?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(editingTable?.tableName ?: "") }
    var seats by remember { mutableStateOf(editingTable?.seats?.toString() ?: "") }
    var remark by remember { mutableStateOf(editingTable?.remark ?: "") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingTable != null) "編輯桌號" else "新增桌號") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) { name = it; nameError = false } },
                    label = { Text("桌號名稱（最多 20 字）") },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = seats,
                    onValueChange = { if (it.length <= 3 && (it.isEmpty() || it.all { c -> c.isDigit() })) seats = it },
                    label = { Text("座位數（選填）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("備註（選填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    nameError = name.isBlank()
                    if (!nameError) onSave(name.trim(), seats.toIntOrNull(), remark.ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = Red700)
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
