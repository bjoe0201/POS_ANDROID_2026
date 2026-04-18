package com.pos.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.entity.MenuGroupEntity
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.data.repository.MenuGroupRepository
import com.pos.app.data.repository.MenuRepository
import com.pos.app.data.repository.OrderRepository
import com.pos.app.data.repository.TableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

val CATEGORIES = listOf(
    "HOTPOT_BASE" to "鍋底",
    "MEAT" to "肉類",
    "SEAFOOD" to "海鮮",
    "VEGETABLE" to "蔬菜",
    "BEVERAGE" to "飲料",
    "OTHER" to "其他"
)

data class OrderUiState(
    val groups: List<MenuGroupEntity> = emptyList(),
    val tables: List<TableEntity> = emptyList(),
    val selectedTable: TableEntity? = null,
    val order: OrderEntity? = null,
    val orderItems: List<OrderItemEntity> = emptyList(),
    val menuItems: List<MenuItemEntity> = emptyList(),
    val selectedCategory: String = "HOTPOT_BASE",
    val remark: String = ""
) {
    val total: Double get() = orderItems.sumOf { it.price * it.quantity }
    val itemCount: Int get() = orderItems.sumOf { it.quantity }
}

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val menuGroupRepository: MenuGroupRepository,
    private val menuRepository: MenuRepository,
    private val tableRepository: TableRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private var orderObserveJob: Job? = null
    private var menuObserveJob: Job? = null

    init {
        menuGroupRepository.getActiveGroups()
            .onEach { groups ->
                val selectedCategory = _uiState.value.selectedCategory
                    .takeIf { code -> groups.any { it.code == code } }
                    ?: groups.firstOrNull()?.code.orEmpty()

                _uiState.update { it.copy(groups = groups, selectedCategory = selectedCategory) }
                observeMenuForCategory(selectedCategory)
            }
            .launchIn(viewModelScope)

        tableRepository.getActiveTables()
            .onEach { tables ->
                val current = _uiState.value.selectedTable
                val stillActive = tables.find { it.id == current?.id }
                _uiState.update { it.copy(tables = tables, selectedTable = stillActive ?: tables.firstOrNull()) }
                loadOrderForSelected()
            }
            .launchIn(viewModelScope)
    }

    fun selectTable(table: TableEntity) {
        _uiState.update { it.copy(selectedTable = table, order = null, orderItems = emptyList()) }
        loadOrderForSelected()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadOrderForSelected() {
        val table = _uiState.value.selectedTable ?: return
        orderObserveJob?.cancel()
        orderObserveJob = viewModelScope.launch {
            orderRepository.getOpenOrderForTable(table.id)
                .onEach { order -> _uiState.update { it.copy(order = order) } }
                .flatMapLatest { order ->
                    if (order != null) orderRepository.getItemsForOrder(order.id)
                    else flowOf(emptyList())
                }
                .collect { items -> _uiState.update { it.copy(orderItems = items) } }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        observeMenuForCategory(category)
    }

    fun addItem(menuItem: MenuItemEntity) {
        val table = _uiState.value.selectedTable ?: return
        viewModelScope.launch {
            val orderId = _uiState.value.order?.id
                ?: orderRepository.createOrder(table.id, table.tableName)
            orderRepository.addOrUpdateItem(
                orderId = orderId,
                menuItemId = menuItem.id,
                name = menuItem.name,
                price = menuItem.price,
                menuGroupCode = menuItem.category,
                menuGroupName = resolveGroupName(menuItem.category),
                delta = 1
            )
        }
    }

    fun removeItem(menuItem: MenuItemEntity) {
        viewModelScope.launch {
            val orderId = _uiState.value.order?.id ?: return@launch
            orderRepository.addOrUpdateItem(
                orderId = orderId,
                menuItemId = menuItem.id,
                name = menuItem.name,
                price = menuItem.price,
                menuGroupCode = menuItem.category,
                menuGroupName = resolveGroupName(menuItem.category),
                delta = -1
            )
        }
    }

    fun deleteOrderItem(item: OrderItemEntity) {
        viewModelScope.launch { orderRepository.removeItem(item) }
    }

    fun updateRemark(text: String) {
        _uiState.update { it.copy(remark = text) }
    }

    fun payOrder(onDone: () -> Unit) {
        viewModelScope.launch {
            val orderId = _uiState.value.order?.id ?: return@launch
            orderRepository.payOrder(orderId, _uiState.value.remark)
            _uiState.update { it.copy(remark = "") }
            onDone()
        }
    }

    fun cancelOrder() {
        viewModelScope.launch {
            val orderId = _uiState.value.order?.id ?: return@launch
            orderRepository.cancelOrder(orderId)
        }
    }

    fun getQuantityInOrder(menuItemId: Long): Int =
        _uiState.value.orderItems.find { it.menuItemId == menuItemId }?.quantity ?: 0

    private fun observeMenuForCategory(category: String) {
        menuObserveJob?.cancel()
        if (category.isBlank()) {
            _uiState.update { it.copy(menuItems = emptyList()) }
            return
        }

        menuObserveJob = menuRepository.getItemsByCategory(category)
            .onEach { items -> _uiState.update { it.copy(menuItems = items) } }
            .launchIn(viewModelScope)
    }

    private fun resolveGroupName(code: String): String =
        _uiState.value.groups.find { it.code == code }?.name ?: code
}
