package com.pos.app.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.ui.theme.LocalPosColors
import com.pos.app.ui.theme.PosColors

@Composable
fun TableSettingScreen(viewModel: TableSettingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val t = LocalPosColors.current

    if (uiState.showDialog) {
        TableDialog(
            editingTable = uiState.editingTable,
            onSave = { name, seats, remark -> viewModel.saveTable(name, seats, remark) },
            onDismiss = { viewModel.dismissDialog() },
            t = t
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(t.bg)) {
        // TopBar
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(t.topbar).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(t.accent))
                Column {
                    Text("桌號設定", color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("桌位管理", color = t.textMuted, fontSize = 11.sp)
                }
            }
            IconButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "新增桌號", tint = t.accent)
            }
        }

        // Summary bar
        val activeCount = uiState.tables.count { it.isActive }
        val inactiveCount = uiState.tables.count { !it.isActive }
        Row(
            modifier = Modifier.fillMaxWidth().background(t.surface).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(t.success))
                Text("啟用 $activeCount 桌", color = t.success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(t.border))
                Text("停用 $inactiveCount 桌", color = t.textMuted, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("共 ${uiState.tables.size} 個桌號", color = t.textMuted, fontSize = 12.sp)
        }
        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))

        // Table list
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.tables.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("尚無桌號，點擊右上角 + 新增", color = t.textMuted)
                    }
                }
            } else {
                itemsIndexed(uiState.tables, key = { _, table -> table.id }) { index, table ->
                    TableRow(
                        table = table,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.tables.lastIndex,
                        onMoveUp = { viewModel.moveTableUp(table) },
                        onMoveDown = { viewModel.moveTableDown(table) },
                        onEdit = { viewModel.showEditDialog(table) },
                        onDelete = { viewModel.deleteTable(table) },
                        onToggle = { viewModel.toggleActive(table) },
                        t = t
                    )
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
    onToggle: () -> Unit,
    t: PosColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(t.card)
            .border(1.dp, if (table.isActive) t.border else t.border.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (table.isActive) t.success else t.border)
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Table info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    table.tableName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (table.isActive) t.text else t.textMuted
                )
                if (table.seats != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(t.accentDim2)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("${table.seats} 人", fontSize = 11.sp, color = t.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!table.isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(t.border.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("停用", fontSize = 11.sp, color = t.textMuted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            val remark = table.remark
            if (!remark.isNullOrBlank()) {
                Text(remark, fontSize = 12.sp, color = t.textMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }

        // Move arrows
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上移",
                    tint = if (canMoveUp) t.accent else t.border, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "下移",
                    tint = if (canMoveDown) t.accent else t.border, modifier = Modifier.size(16.dp))
            }
        }

        Switch(
            checked = table.isActive,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = t.accent,
                uncheckedThumbColor = t.textMuted,
                uncheckedTrackColor = t.border
            )
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "編輯", tint = t.accent)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "刪除", tint = t.error)
        }
    }
}

@Composable
private fun TableDialog(
    editingTable: TableEntity?,
    onSave: (String, Int?, String?) -> Unit,
    onDismiss: () -> Unit,
    t: PosColors
) {
    var name by remember { mutableStateOf(editingTable?.tableName ?: "") }
    var seats by remember { mutableStateOf(editingTable?.seats?.toString() ?: "") }
    var remark by remember { mutableStateOf(editingTable?.remark ?: "") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text(if (editingTable != null) "編輯桌號" else "新增桌號", color = t.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) { name = it; nameError = false } },
                    label = { Text("桌號名稱（最多 20 字）", color = t.textMuted) },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
                        focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent
                    )
                )
                OutlinedTextField(
                    value = seats,
                    onValueChange = { if (it.length <= 3 && (it.isEmpty() || it.all { c -> c.isDigit() })) seats = it },
                    label = { Text("座位數（選填）", color = t.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
                        focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent
                    )
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("備註（選填）", color = t.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
                        focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    nameError = name.isBlank()
                    if (!nameError) onSave(name.trim(), seats.toIntOrNull(), remark.ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = t.accent)
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = t.textSub) } }
    )
}
