package com.pos.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class DateRange { TODAY, WEEK, MONTH, YEAR, ALL, CUSTOM }

data class OrderWithItems(
    val order: OrderEntity,
    val items: List<OrderItemEntity>
)

data class GroupSalesStat(
    val groupName: String,
    val quantity: Int,
    val revenue: Double
)

data class ReportUiState(
    val dateRange: DateRange = DateRange.TODAY,
    val customStartDate: Long? = null,
    val customEndDate: Long? = null,
    val showDeleted: Boolean = false,
    val orders: List<OrderWithItems> = emptyList(),
    val totalRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val avgOrderValue: Double = 0.0,
    val itemRanking: List<Pair<String, Int>> = emptyList(),
    val groupRanking: List<GroupSalesStat> = emptyList(),
    val message: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        // 持續監聽訂單資料庫，任何新增/更新都會自動重新計算報表
        viewModelScope.launch {
            orderRepository.getAllOrders().collect { allOrders ->
                recompute(allOrders, _uiState.value.dateRange, _uiState.value.showDeleted)
            }
        }
    }

    fun setDateRange(range: DateRange) {
        _uiState.update { it.copy(dateRange = range) }
        viewModelScope.launch {
            recompute(orderRepository.getAllOrders().first(), range, _uiState.value.showDeleted)
        }
    }

    fun setCustomStartDate(millis: Long) {
        _uiState.update { it.copy(customStartDate = millis, dateRange = DateRange.CUSTOM) }
    }

    fun setCustomEndDate(millis: Long) {
        _uiState.update { it.copy(customEndDate = millis, dateRange = DateRange.CUSTOM) }
    }

    fun applyCustomDateRange() {
        val start = _uiState.value.customStartDate ?: return
        val end = _uiState.value.customEndDate ?: return
        val (from, to) = if (start <= end) start to end else end to start

        _uiState.update {
            it.copy(
                dateRange = DateRange.CUSTOM,
                customStartDate = from,
                customEndDate = to
            )
        }

        viewModelScope.launch {
            recompute(orderRepository.getAllOrders().first(), DateRange.CUSTOM, _uiState.value.showDeleted)
        }
    }

    fun toggleShowDeleted() {
        val newShow = !_uiState.value.showDeleted
        _uiState.update { it.copy(showDeleted = newShow) }
        viewModelScope.launch {
            recompute(orderRepository.getAllOrders().first(), _uiState.value.dateRange, newShow)
        }
    }

    fun softDeleteOrder(orderId: Long) {
        viewModelScope.launch { orderRepository.softDeleteOrder(orderId) }
    }

    private suspend fun recompute(allOrders: List<OrderEntity>, range: DateRange, showDeleted: Boolean) {
        _uiState.update { it.copy(isLoading = true) }
        val (start, end) = resolveDateBounds(
            range = range,
            customStart = _uiState.value.customStartDate,
            customEnd = _uiState.value.customEndDate
        )

        // 只計算 PAID 的訂單；showDeleted 決定是否納入已刪除
        val paidOrders = allOrders
            .filter { it.status == "PAID" && it.createdAt in start..end }
            .filter { if (showDeleted) true else !it.isDeleted }

        val allItems = orderRepository.getAllOrderItems()

        val orderWithItems = paidOrders.map { order ->
            OrderWithItems(order, allItems.filter { it.orderId == order.id })
        }

        val totalRevenue = orderWithItems.sumOf { owi -> owi.items.sumOf { it.price * it.quantity } }
        val itemMap = mutableMapOf<String, Int>()
        val groupMap = mutableMapOf<String, Pair<Int, Double>>()
        orderWithItems.forEach { owi -> owi.items.forEach { item ->
            itemMap[item.name] = (itemMap[item.name] ?: 0) + item.quantity
            val groupName = item.menuGroupName.ifBlank { "未分組" }
            val currentGroup = groupMap[groupName] ?: (0 to 0.0)
            groupMap[groupName] = (currentGroup.first + item.quantity) to (currentGroup.second + item.price * item.quantity)
        }}
        val ranking = itemMap.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value }
        val groupRanking = groupMap.entries
            .sortedByDescending { it.value.second }
            .take(10)
            .map { (name, summary) -> GroupSalesStat(name, summary.first, summary.second) }

        _uiState.update {
            it.copy(
                orders = orderWithItems,
                totalRevenue = totalRevenue,
                totalOrders = orderWithItems.size,
                avgOrderValue = if (orderWithItems.isEmpty()) 0.0 else totalRevenue / orderWithItems.size,
                itemRanking = ranking,
                groupRanking = groupRanking,
                isLoading = false
            )
        }
    }

    private fun resolveDateBounds(range: DateRange, customStart: Long?, customEnd: Long?): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (range) {
            DateRange.TODAY -> {
                startOfDay(cal.timeInMillis) to endOfDay(cal.timeInMillis)
            }
            DateRange.WEEK -> {
                val end = endOfDay(cal.timeInMillis)
                cal.add(Calendar.DAY_OF_YEAR, -6)
                startOfDay(cal.timeInMillis) to end
            }
            DateRange.MONTH -> {
                val end = endOfDay(cal.timeInMillis)
                cal.add(Calendar.DAY_OF_YEAR, -29)
                startOfDay(cal.timeInMillis) to end
            }
            DateRange.YEAR -> {
                val end = endOfDay(cal.timeInMillis)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                startOfDay(cal.timeInMillis) to end
            }
            DateRange.ALL -> 0L to Long.MAX_VALUE
            DateRange.CUSTOM -> {
                val from = customStart ?: 0L
                val to = customEnd ?: Long.MAX_VALUE
                val (start, end) = if (from <= to) from to to else to to from
                startOfDay(start) to endOfDay(end)
            }
        }
    }

    private fun startOfDay(timeMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun endOfDay(timeMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
