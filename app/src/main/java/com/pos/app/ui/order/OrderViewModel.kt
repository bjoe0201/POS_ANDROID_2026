package com.pos.app.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.db.entity.TableEntity
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
    private val menuRepository: MenuRepository,
    private val tableRepository: TableRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private var orderObserveJob: Job? = null

    init {
        tableRepository.getActiveTables()
            .onEach { tables ->
                val current = _uiState.value.selectedTable
                val stillActive = tables.find { it.id == current?.id }
                _uiState.update { it.copy(tables = tables, selectedTable = stillActive ?: tables.firstOrNull()) }
                loadOrderForSelected()
            }
            .launchIn(viewModelScope)

        menuRepository.getItemsByCategory(_uiState.value.selectedCategory)
            .onEach { items -> _uiState.update { it.copy(menuItems = items) } }
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
        menuRepository.getItemsByCategory(category)
            .onEach { items -> _uiState.update { it.copy(menuItems = items) } }
            .launchIn(viewModelScope)
    }

    fun addItem(menuItem: MenuItemEntity) {
        val table = _uiState.value.selectedTable ?: return
        viewModelScope.launch {
            val orderId = _uiState.value.order?.id
                ?: orderRepository.createOrder(table.id, table.tableName)
            orderRepository.addOrUpdateItem(orderId, menuItem.id, menuItem.name, menuItem.price, 1)
        }
    }

    fun removeItem(menuItem: MenuItemEntity) {
        viewModelScope.launch {
            val orderId = _uiState.value.order?.id ?: return@launch
            orderRepository.addOrUpdateItem(orderId, menuItem.id, menuItem.name, menuItem.price, -1)
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
}
