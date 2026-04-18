package com.pos.app.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.ui.order.CATEGORIES
import com.pos.app.ui.theme.Red700

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagementScreen(
    viewModel: MenuManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showAddDialog) {
        AddEditItemDialog(
            editingItem = uiState.editingItem,
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
                items(CATEGORIES) { (code, label) ->
                    FilterChip(
                        selected = uiState.selectedCategory == code,
                        onClick = { viewModel.selectCategory(code) },
                        label = { Text(label) },
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
                    items(CATEGORIES) { (code, label) ->
                        FilterChip(
                            selected = category == code,
                            onClick = { category = code },
                            label = { Text(label) },
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
                    if (!nameError && !priceError) {
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
