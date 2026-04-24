package com.pos.app.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.data.db.entity.MenuGroupEntity
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.ui.theme.LocalPosColors
import com.pos.app.ui.theme.PosColors

@Composable
fun MenuManagementScreen(
    viewModel: MenuManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val t = LocalPosColors.current

    if (uiState.showGroupManagementDialog) {
        var confirmDeleteGroup by remember { mutableStateOf<MenuGroupEntity?>(null) }

        if (confirmDeleteGroup != null) {
            val group = confirmDeleteGroup!!
            val itemCount = uiState.allItems.count { it.category == group.code }
            AlertDialog(
                onDismissRequest = { confirmDeleteGroup = null },
                containerColor = t.surface,
                title = { Text("刪除群組", color = t.text, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("確定刪除「${group.name}」群組？此操作無法復原。", color = t.textSub)
                        if (itemCount > 0) {
                            Text(
                                "⚠️ 此群組下有 $itemCount 個品項，刪除群組後這些品項將無法在點餐畫面中被選取（資料仍保留在資料庫中）。",
                                color = t.error,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                "此群組目前沒有品項，可安全刪除。",
                                color = t.textMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.deleteGroup(group); confirmDeleteGroup = null },
                        colors = ButtonDefaults.buttonColors(containerColor = t.error)
                    ) { Text("刪除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteGroup = null }) { Text("取消", color = t.textSub) }
                }
            )
        }

        GroupManagementDialog(
            groups = uiState.groups,
            onDismiss = { viewModel.dismissGroupManagementDialog() },
            onAdd = { viewModel.showAddGroupDialog() },
            onEdit = { viewModel.showEditGroupDialog(it) },
            onMoveUp = { viewModel.moveGroupUp(it) },
            onMoveDown = { viewModel.moveGroupDown(it) },
            onDelete = { confirmDeleteGroup = it },
            t = t
        )
    }

    if (uiState.showGroupEditDialog) {
        AddEditGroupDialog(
            editingGroup = uiState.editingGroup,
            onSave = { code, name, onResult -> viewModel.saveGroup(code, name, onResult) },
            onDismiss = { viewModel.dismissGroupEditDialog() },
            t = t
        )
    }

    if (uiState.showAddDialog) {
        AddEditItemDialog(
            editingItem = uiState.editingItem,
            groups = uiState.groups,
            defaultCategory = uiState.selectedCategory,
            onSave = { name, price, category -> viewModel.saveItem(name, price, category) },
            onDismiss = { viewModel.dismissDialog() },
            t = t
        )
    }

    var confirmDeleteItem by remember { mutableStateOf<MenuItemEntity?>(null) }
    if (confirmDeleteItem != null) {
        val item = confirmDeleteItem!!
        AlertDialog(
            onDismissRequest = { confirmDeleteItem = null },
            containerColor = t.surface,
            title = { Text("刪除品項", color = t.text, fontWeight = FontWeight.Bold) },
            text = { Text("確定刪除「${item.name}」？此操作無法復原。", color = t.textSub) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteItem(item); confirmDeleteItem = null },
                    colors = ButtonDefaults.buttonColors(containerColor = t.error)
                ) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteItem = null }) { Text("取消", color = t.textSub) }
            }
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
                    Text("菜單管理", color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("品項與群組設定", color = t.textMuted, fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.showGroupManagementDialog() }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "群組管理", tint = t.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("群組", color = t.accent, fontSize = 13.sp)
                }
                IconButton(onClick = { viewModel.showAddDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "新增", tint = t.accent)
                }
            }
        }

        // Category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.background(t.surface)
        ) {
            items(uiState.groups, key = { it.code }) { group ->
                PosChip(
                    label = group.name,
                    active = uiState.selectedCategory == group.code,
                    onClick = { viewModel.selectCategory(group.code) },
                    t = t
                )
            }
        }
        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))

        // Item list
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            val filteredItems = uiState.allItems.filter { it.category == uiState.selectedCategory }
            if (filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("此分類尚無品項", color = t.textMuted, fontSize = 14.sp)
                    }
                }
            } else {
                itemsIndexed(filteredItems, key = { _, it -> it.id }) { index, item ->
                    MenuManagementItemRow(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < filteredItems.lastIndex,
                        onMoveUp = { viewModel.moveItemUp(item, filteredItems) },
                        onMoveDown = { viewModel.moveItemDown(item, filteredItems) },
                        onEdit = { viewModel.showEditDialog(item) },
                        onDelete = { confirmDeleteItem = item },
                        onToggle = { viewModel.toggleAvailability(item) },
                        t = t
                    )
                }
            }
        }
    }
}

@Composable
private fun PosChip(label: String, active: Boolean, onClick: () -> Unit, t: PosColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) t.accent else t.card)
            .border(1.dp, if (active) t.accent else t.border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (active) Color.White else t.textSub, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun MenuManagementItemRow(
    item: MenuItemEntity,
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
            .border(1.dp, t.border, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .then(if (!item.isAvailable) Modifier else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort arrows
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上移",
                    tint = if (canMoveUp) t.accent else t.border, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "下移",
                    tint = if (canMoveDown) t.accent else t.border, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f).then(if (!item.isAvailable) Modifier else Modifier)) {
            Text(
                item.name,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = if (item.isAvailable) t.text else t.textMuted
            )
            Text(
                "NT${"$"} %.0f".format(item.price),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (item.isAvailable) t.accent else t.textMuted
            )
        }
        Switch(
            checked = item.isAvailable,
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
private fun GroupManagementDialog(
    groups: List<MenuGroupEntity>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (MenuGroupEntity) -> Unit,
    onMoveUp: (MenuGroupEntity) -> Unit,
    onMoveDown: (MenuGroupEntity) -> Unit,
    onDelete: (MenuGroupEntity) -> Unit,
    t: PosColors
) {
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.8f

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(t.surface)
                .border(1.dp, t.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("群組管理", color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = onAdd) { Text("新增群組", color = t.accent) }
            }

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    Text("尚無群組，請先新增", color = t.textMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(groups, key = { _, group -> group.code }) { index, group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(t.card)
                                .border(1.dp, t.border, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(group.name, color = t.text, fontWeight = FontWeight.Medium)
                                Text(group.code, fontSize = 12.sp, color = t.textMuted)
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(onClick = { onMoveUp(group) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "上移",
                                        tint = if (index > 0) t.accent else t.border, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onMoveDown(group) }, enabled = index < groups.lastIndex, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "下移",
                                        tint = if (index < groups.lastIndex) t.accent else t.border, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { onEdit(group) }) {
                                Icon(Icons.Default.Edit, contentDescription = "編輯群組", tint = t.accent)
                            }
                            IconButton(onClick = { onDelete(group) }) {
                                Icon(Icons.Default.Delete, contentDescription = "刪除群組", tint = t.error)
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("關閉", color = t.textSub) }
            }
        }
    }
}

@Composable
private fun AddEditGroupDialog(
    editingGroup: MenuGroupEntity?,
    onSave: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    t: PosColors
) {
    var code by remember(editingGroup?.code) { mutableStateOf(editingGroup?.code ?: "") }
    var name by remember(editingGroup?.name) { mutableStateOf(editingGroup?.name ?: "") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text(if (editingGroup == null) "新增群組" else "編輯群組", color = t.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        if (editingGroup == null) {
                            code = it.uppercase().filter { char -> char.isLetterOrDigit() || char == '_' }
                            codeError = null
                        }
                    },
                    label = { Text("群組代碼", color = t.textMuted) },
                    supportingText = { Text(codeError ?: "建議使用英文大寫、數字或底線", color = if (codeError != null) t.error else t.textMuted, fontSize = 12.sp) },
                    isError = codeError != null,
                    enabled = editingGroup == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
                        focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent
                    )
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("群組名稱", color = t.textMuted) },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it, color = t.error, fontSize = 12.sp) } },
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
                    onSave(code, name) { success, message ->
                        if (!success) {
                            if (message?.contains("代碼") == true) codeError = message
                            else nameError = message
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = t.accent)
            ) { Text("儲存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = t.textSub) }
        }
    )
}

@Composable
private fun AddEditItemDialog(
    editingItem: MenuItemEntity?,
    groups: List<MenuGroupEntity>,
    defaultCategory: String,
    onSave: (String, Double, String) -> Unit,
    onDismiss: () -> Unit,
    t: PosColors
) {
    var name by remember { mutableStateOf(editingItem?.name ?: "") }
    var price by remember { mutableStateOf(editingItem?.price?.let { "%.0f".format(it) } ?: "") }
    var category by remember { mutableStateOf(editingItem?.category ?: defaultCategory) }
    var nameError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text(if (editingItem != null) "編輯品項" else "新增品項", color = t.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("品項名稱", color = t.textMuted) },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
                        focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent
                    )
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it; priceError = false },
                    label = { Text("價格 (NT$)", color = t.textMuted) },
                    isError = priceError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
                        focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent
                    )
                )
                Text("分類", color = t.textSub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.code }) { group ->
                        PosChip(
                            label = group.name,
                            active = category == group.code,
                            onClick = { category = group.code },
                            t = t
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    nameError = name.isBlank()
                    val priceVal = price.toDoubleOrNull()
                    priceError = priceVal == null || priceVal <= 0
                    if (!nameError && !priceError && category.isNotBlank()) {
                        onSave(name.trim(), priceVal!!, category)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = t.accent)
            ) { Text("儲存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = t.textSub) }
        }
    )
}
