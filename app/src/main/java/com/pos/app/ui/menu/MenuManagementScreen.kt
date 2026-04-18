package com.pos.app.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import com.pos.app.ui.theme.Red700

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagementScreen(
    viewModel: MenuManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showGroupManagementDialog) {
        var confirmDeleteGroup by remember { mutableStateOf<MenuGroupEntity?>(null) }

        if (confirmDeleteGroup != null) {
            val group = confirmDeleteGroup!!
            val itemCount = uiState.allItems.count { it.category == group.code }
            AlertDialog(
                onDismissRequest = { confirmDeleteGroup = null },
                title = { Text("刪除群組") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("確定刪除「${group.name}」群組？此操作無法復原。")
                        if (itemCount > 0) {
                            Text(
                                "⚠️ 此群組下有 $itemCount 個品項，刪除群組後這些品項將無法在點餐畫面中被選取（資料仍保留在資料庫中）。",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "此群組目前沒有品項，可安全刪除。",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.deleteGroup(group); confirmDeleteGroup = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("刪除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteGroup = null }) { Text("取消") }
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
            onDelete = { confirmDeleteGroup = it }
        )
    }

    if (uiState.showGroupEditDialog) {
        AddEditGroupDialog(
            editingGroup = uiState.editingGroup,
            onSave = { code, name, onResult -> viewModel.saveGroup(code, name, onResult) },
            onDismiss = { viewModel.dismissGroupEditDialog() }
        )
    }

    if (uiState.showAddDialog) {
        AddEditItemDialog(
            editingItem = uiState.editingItem,
            groups = uiState.groups,
            defaultCategory = uiState.selectedCategory,
            onSave = { name, price, category -> viewModel.saveItem(name, price, category) },
            onDismiss = { viewModel.dismissDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("菜單管理", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { viewModel.showGroupManagementDialog() }) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "群組管理",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("群組", color = Color.White)
                    }
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "新增", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Red700,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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

            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val items = uiState.allItems.filter { it.category == uiState.selectedCategory }
                items(items, key = { it.id }) { item ->
                    MenuManagementItemRow(
                        item = item,
                        onEdit = { viewModel.showEditDialog(item) },
                        onDelete = { viewModel.deleteItem(item) },
                        onToggle = { viewModel.toggleAvailability(item) }
                    )
                }
                if (items.isEmpty()) {
                    item {
                        Text(
                            "此分類尚無品項",
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
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
    onDelete: (MenuGroupEntity) -> Unit
) {
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.8f

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("群組管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onAdd) { Text("新增群組", color = Red700) }
                }

                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("尚無群組，請先新增", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(groups, key = { _, group -> group.code }) { index, group ->
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(group.name, fontWeight = FontWeight.Medium)
                                        Text(group.code, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        IconButton(onClick = { onMoveUp(group) }, enabled = index > 0) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "上移", tint = Red700)
                                        }
                                        IconButton(onClick = { onMoveDown(group) }, enabled = index < groups.lastIndex) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "下移", tint = Red700)
                                        }
                                    }
                                    IconButton(onClick = { onEdit(group) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "編輯群組", tint = Red700)
                                    }
                                    IconButton(onClick = { onDelete(group) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "刪除群組", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("關閉") }
                }
            }
        }
    }
}

@Composable
private fun AddEditGroupDialog(
    editingGroup: MenuGroupEntity?,
    onSave: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember(editingGroup?.code) { mutableStateOf(editingGroup?.code ?: "") }
    var name by remember(editingGroup?.name) { mutableStateOf(editingGroup?.name ?: "") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingGroup == null) "新增群組" else "編輯群組") },
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
                    label = { Text("群組代碼") },
                    supportingText = {
                        Text(codeError ?: "建議使用英文大寫、數字或底線")
                    },
                    isError = codeError != null,
                    enabled = editingGroup == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("群組名稱") },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                colors = ButtonDefaults.buttonColors(containerColor = Red700)
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MenuManagementItemRow(
    item: MenuItemEntity,
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
                    item.name,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isAvailable) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline
                )
                Text(
                    "NT$ %.0f".format(item.price),
                    fontSize = 13.sp,
                    color = if (item.isAvailable) Red700 else MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = item.isAvailable,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = Red700, checkedTrackColor = MaterialTheme.colorScheme.primaryContainer)
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
private fun AddEditItemDialog(
    editingItem: MenuItemEntity?,
    groups: List<MenuGroupEntity>,
    defaultCategory: String,
    onSave: (String, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(editingItem?.name ?: "") }
    var price by remember { mutableStateOf(editingItem?.price?.let { "%.0f".format(it) } ?: "") }
    var category by remember { mutableStateOf(editingItem?.category ?: defaultCategory) }
    var nameError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingItem != null) "編輯品項" else "新增品項") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("品項名稱") },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it; priceError = false },
                    label = { Text("價格 (NT$)") },
                    isError = priceError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("分類", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.code }) { group ->
                        FilterChip(
                            selected = category == group.code,
                            onClick = { category = group.code },
                            label = { Text(group.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Red700,
                                selectedLabelColor = Color.White
                            )
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
                colors = ButtonDefaults.buttonColors(containerColor = Red700)
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
