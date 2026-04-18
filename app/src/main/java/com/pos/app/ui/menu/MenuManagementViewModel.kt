package com.pos.app.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.data.repository.MenuRepository
import com.pos.app.ui.order.CATEGORIES
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MenuManagementUiState(
    val allItems: List<MenuItemEntity> = emptyList(),
    val selectedCategory: String = "MEAT",
    val showAddDialog: Boolean = false,
    val editingItem: MenuItemEntity? = null
)

@HiltViewModel
class MenuManagementViewModel @Inject constructor(
    private val menuRepository: MenuRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuManagementUiState())
    val uiState: StateFlow<MenuManagementUiState> = _uiState.asStateFlow()

    init {
        menuRepository.getAllItems()
            .onEach { items -> _uiState.update { it.copy(allItems = items) } }
            .launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingItem = null) }
    }

    fun showEditDialog(item: MenuItemEntity) {
        _uiState.update { it.copy(showAddDialog = true, editingItem = item) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingItem = null) }
    }

    fun saveItem(name: String, price: Double, category: String) {
        viewModelScope.launch {
            val editing = _uiState.value.editingItem
            if (editing != null) {
                menuRepository.update(editing.copy(name = name, price = price, category = category))
            } else {
                menuRepository.insert(MenuItemEntity(name = name, price = price, category = category))
            }
            dismissDialog()
        }
    }

    fun deleteItem(item: MenuItemEntity) {
        viewModelScope.launch { menuRepository.delete(item) }
    }

    fun toggleAvailability(item: MenuItemEntity) {
        viewModelScope.launch {
            menuRepository.setAvailability(item.id, !item.isAvailable)
        }
    }

    fun getItemsForCurrentCategory(): List<MenuItemEntity> {
        return _uiState.value.allItems.filter {
            it.category == _uiState.value.selectedCategory
        }
    }
}
