package com.pos.app.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.entity.MenuGroupEntity
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.data.repository.MenuGroupRepository
import com.pos.app.data.repository.MenuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MenuManagementUiState(
    val groups: List<MenuGroupEntity> = emptyList(),
    val allItems: List<MenuItemEntity> = emptyList(),
    val selectedCategory: String = "MEAT",
    val showGroupManagementDialog: Boolean = false,
    val showGroupEditDialog: Boolean = false,
    val editingGroup: MenuGroupEntity? = null,
    val showAddDialog: Boolean = false,
    val editingItem: MenuItemEntity? = null
)

@HiltViewModel
class MenuManagementViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val menuGroupRepository: MenuGroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuManagementUiState())
    val uiState: StateFlow<MenuManagementUiState> = _uiState.asStateFlow()

    init {
        menuGroupRepository.getAllGroups()
            .onEach { groups ->
                val selectedCategory = _uiState.value.selectedCategory
                    .takeIf { code -> groups.any { it.code == code } }
                    ?: groups.firstOrNull()?.code.orEmpty()
                _uiState.update { it.copy(groups = groups, selectedCategory = selectedCategory) }
            }
            .launchIn(viewModelScope)

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

    fun showGroupManagementDialog() {
        _uiState.update { it.copy(showGroupManagementDialog = true) }
    }

    fun dismissGroupManagementDialog() {
        _uiState.update {
            it.copy(
                showGroupManagementDialog = false,
                showGroupEditDialog = false,
                editingGroup = null
            )
        }
    }

    fun showAddGroupDialog() {
        _uiState.update { it.copy(showGroupEditDialog = true, editingGroup = null) }
    }

    fun showEditGroupDialog(group: MenuGroupEntity) {
        _uiState.update { it.copy(showGroupEditDialog = true, editingGroup = group) }
    }

    fun dismissGroupEditDialog() {
        _uiState.update { it.copy(showGroupEditDialog = false, editingGroup = null) }
    }

    fun showEditDialog(item: MenuItemEntity) {
        _uiState.update { it.copy(showAddDialog = true, editingItem = item) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingItem = null) }
    }

    fun saveGroup(code: String, name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val editing = _uiState.value.editingGroup
            val normalizedCode = code.trim().uppercase()
            val normalizedName = name.trim()
            val duplicate = _uiState.value.groups.any {
                it.code == normalizedCode && it.code != editing?.code
            }

            when {
                normalizedName.isBlank() -> onResult(false, "群組名稱不可空白")
                editing == null && normalizedCode.isBlank() -> onResult(false, "群組代碼不可空白")
                editing == null && duplicate -> onResult(false, "群組代碼不可重複")
                else -> {
                    if (editing != null) {
                        menuGroupRepository.update(editing.copy(name = normalizedName))
                    } else {
                        val maxOrder = _uiState.value.groups.maxOfOrNull { it.sortOrder } ?: 0
                        menuGroupRepository.insert(
                            MenuGroupEntity(
                                code = normalizedCode,
                                name = normalizedName,
                                sortOrder = maxOrder + 1,
                                isActive = true
                            )
                        )
                        if (_uiState.value.selectedCategory.isBlank()) {
                            _uiState.update { it.copy(selectedCategory = normalizedCode) }
                        }
                    }
                    dismissGroupEditDialog()
                    onResult(true, null)
                }
            }
        }
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

    fun deleteGroup(group: MenuGroupEntity) {
        viewModelScope.launch { menuGroupRepository.delete(group) }
    }

    fun moveGroupUp(group: MenuGroupEntity) {
        val groups = _uiState.value.groups
        val index = groups.indexOfFirst { it.code == group.code }
        if (index <= 0) return
        reorderAndPersist(groups, index, index - 1)
    }

    fun moveGroupDown(group: MenuGroupEntity) {
        val groups = _uiState.value.groups
        val index = groups.indexOfFirst { it.code == group.code }
        if (index == -1 || index >= groups.lastIndex) return
        reorderAndPersist(groups, index, index + 1)
    }

    private fun reorderAndPersist(groups: List<MenuGroupEntity>, fromIndex: Int, toIndex: Int) {
        val reordered = groups.toMutableList().apply {
            val moving = removeAt(fromIndex)
            add(toIndex, moving)
        }

        viewModelScope.launch {
            reordered.forEachIndexed { index, group ->
                val newSortOrder = index + 1
                if (group.sortOrder != newSortOrder) {
                    menuGroupRepository.update(group.copy(sortOrder = newSortOrder))
                }
            }
        }
    }
}
