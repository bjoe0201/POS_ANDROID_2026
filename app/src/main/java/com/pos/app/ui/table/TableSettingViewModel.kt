package com.pos.app.ui.table

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.data.repository.TableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TableSettingUiState(
    val tables: List<TableEntity> = emptyList(),
    val showDialog: Boolean = false,
    val editingTable: TableEntity? = null
)

@HiltViewModel
class TableSettingViewModel @Inject constructor(
    private val tableRepository: TableRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TableSettingUiState())
    val uiState: StateFlow<TableSettingUiState> = _uiState.asStateFlow()

    init {
        tableRepository.getAllTables()
            .onEach { tables -> _uiState.update { it.copy(tables = tables) } }
            .launchIn(viewModelScope)
    }

    fun showAddDialog() = _uiState.update { it.copy(showDialog = true, editingTable = null) }
    fun showEditDialog(table: TableEntity) = _uiState.update { it.copy(showDialog = true, editingTable = table) }
    fun dismissDialog() = _uiState.update { it.copy(showDialog = false, editingTable = null) }

    fun saveTable(name: String, seats: Int?, remark: String?) {
        viewModelScope.launch {
            val editing = _uiState.value.editingTable
            if (editing != null) {
                tableRepository.update(editing.copy(tableName = name, seats = seats, remark = remark?.ifBlank { null }))
            } else {
                val maxOrder = _uiState.value.tables.maxOfOrNull { it.sortOrder } ?: 0
                tableRepository.insert(TableEntity(tableName = name, seats = seats, remark = remark?.ifBlank { null }, sortOrder = maxOrder + 1))
            }
            dismissDialog()
        }
    }

    fun deleteTable(table: TableEntity) {
        viewModelScope.launch { tableRepository.delete(table) }
    }

    fun toggleActive(table: TableEntity) {
        viewModelScope.launch { tableRepository.setActive(table.id, !table.isActive) }
    }
}
