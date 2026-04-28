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
import com.pos.app.data.repository.SettingsRepository
import com.pos.app.data.repository.TableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

fun startOfDay(millis: Long): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

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
    val remark: String = "",
    val openOrderTotals: Map<Long, Double> = emptyMap(),
    val selectedDate: Long = startOfDay(System.currentTimeMillis()),
    val qtyRepeatIntervalMs: Int = 100,
    val qtyRepeatInitialDelayMs: Int = 1000,
    val hapticEnabled: Boolean = true,
    val printCheckoutEnabled: Boolean = false,
    val errorMessage: String? = null,
    /** 補登模式：selectedDate 不是今日 */
    val isBackfillMode: Boolean = false
) {
    val total: Double get() = orderItems.sumOf { it.price * it.quantity }
    val itemCount: Int get() = orderItems.sumOf { it.quantity }
}

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val menuGroupRepository: MenuGroupRepository,
    private val menuRepository: MenuRepository,
    private val tableRepository: TableRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private var orderObserveJob: Job? = null
    private var menuObserveJob: Job? = null
    private var resetDateJob: Job? = null

    /** 補登確認事件：發出補登日期文字，UI 訂閱後顯示確認對話框 */
    private val _backfillConfirmChannel = Channel<String>(Channel.CONFLATED)
    val backfillConfirmEvent: kotlinx.coroutines.flow.Flow<String> =
        _backfillConfirmChannel.receiveAsFlow()

    /** 是否已對目前 selectedDate 的補登進行過確認 */
    private var backfillConfirmed = false
    /** 暫存等待確認的品項 */
    private var pendingAddItem: MenuItemEntity? = null

    companion object {
        private const val DATE_RESET_DELAY_MS = 3 * 60 * 1000L // 3 分鐘
    }

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

        // observe all open order item totals
        @OptIn(ExperimentalCoroutinesApi::class)
        orderRepository.getAllOpenOrders()
            .flatMapLatest { openOrders ->
                if (openOrders.isEmpty()) flowOf(emptyMap())
                else combine(openOrders.map { order ->
                    orderRepository.getItemsForOrder(order.id).map { items -> order.tableId to items.sumOf { it.price * it.quantity } }
                }) { pairs -> pairs.toMap() }
            }
            .onEach { totals -> _uiState.update { it.copy(openOrderTotals = totals) } }
            .launchIn(viewModelScope)

        settingsRepository.qtyRepeatIntervalMs
            .onEach { v -> _uiState.update { it.copy(qtyRepeatIntervalMs = v) } }
            .launchIn(viewModelScope)
        settingsRepository.qtyRepeatInitialDelayMs
            .onEach { v -> _uiState.update { it.copy(qtyRepeatInitialDelayMs = v) } }
            .launchIn(viewModelScope)
        settingsRepository.hapticEnabled
            .onEach { v -> _uiState.update { it.copy(hapticEnabled = v) } }
            .launchIn(viewModelScope)
        settingsRepository.printCheckoutEnabled
            .onEach { v -> _uiState.update { it.copy(printCheckoutEnabled = v) } }
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

    fun updateSelectedDate(dateMillis: Long) {
        val newDate = startOfDay(dateMillis)
        val todayStart = startOfDay(System.currentTimeMillis())
        _uiState.update { it.copy(selectedDate = newDate, isBackfillMode = newDate != todayStart) }
        if (newDate != todayStart) {
            backfillConfirmed = false   // 換日期後需重新確認
            startResetDateTimer()
        } else {
            cancelResetDateTimer()
        }
    }

    private fun startResetDateTimer() {
        resetDateJob?.cancel()
        resetDateJob = viewModelScope.launch {
            delay(DATE_RESET_DELAY_MS)
            val todayStart = startOfDay(System.currentTimeMillis())
            _uiState.update { it.copy(selectedDate = todayStart, isBackfillMode = false) }
            backfillConfirmed = false
            resetDateJob = null
        }
    }

    private fun cancelResetDateTimer() {
        resetDateJob?.cancel()
        resetDateJob = null
    }

    private fun touchResetDateTimer() {
        if (resetDateJob?.isActive == true) startResetDateTimer()
    }

    fun addItem(menuItem: MenuItemEntity) {
        val table = _uiState.value.selectedTable ?: return
        touchResetDateTimer()

        val todayStart = startOfDay(System.currentTimeMillis())
        val selectedDate = _uiState.value.selectedDate

        // 補登模式：第一次新增品項前需要使用者確認
        if (selectedDate != todayStart && !backfillConfirmed) {
            pendingAddItem = menuItem
            val dateFmt = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
            viewModelScope.launch {
                _backfillConfirmChannel.send(dateFmt.format(java.util.Date(selectedDate)))
            }
            return
        }

        viewModelScope.launch {
            val createdAt = if (selectedDate == todayStart) System.currentTimeMillis() else selectedDate
            val orderId = _uiState.value.order?.id
                ?: orderRepository.createOrder(table.id, table.tableName, createdAt)
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

    /** 使用者在補登確認對話框點「確認繼續」 */
    fun confirmBackfill() {
        backfillConfirmed = true
        val item = pendingAddItem ?: return
        pendingAddItem = null
        addItem(item)
    }

    /** 使用者在補登確認對話框點「取消」 */
    fun cancelBackfill() {
        pendingAddItem = null
    }

    /** 回到今日（供補登橫條「回到今天」按鈕使用） */
    fun resetToToday() {
        cancelResetDateTimer()
        backfillConfirmed = false
        pendingAddItem = null
        val todayStart = startOfDay(System.currentTimeMillis())
        _uiState.update { it.copy(selectedDate = todayStart, isBackfillMode = false) }
    }

    fun removeItem(menuItem: MenuItemEntity) {
        touchResetDateTimer()
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
        touchResetDateTimer()
        viewModelScope.launch { orderRepository.removeItem(item) }
    }

    fun updateRemark(text: String) {
        touchResetDateTimer()
        _uiState.update { it.copy(remark = text) }
    }

    fun payOrder(onDone: () -> Unit) {
        viewModelScope.launch {
            val orderId = _uiState.value.order?.id
            if (orderId == null) {
                _uiState.update { it.copy(errorMessage = "無可結帳訂單，請重新選桌後再試") }
                return@launch
            }
            runCatching {
                orderRepository.payOrder(orderId, _uiState.value.remark)
            }.onSuccess {
                _uiState.update { it.copy(remark = "", errorMessage = null) }
                onDone()
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = "結帳寫入失敗：${e.message ?: "未知錯誤"}") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
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
