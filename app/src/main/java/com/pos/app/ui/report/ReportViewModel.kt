package com.pos.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.repository.MenuRepository
import com.pos.app.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class DateRange { TODAY, WEEK, MONTH, ALL }

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
    private val orderRepository: OrderRepository,
    private val menuRepository: MenuRepository
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
        val cutoff = cutoffTimestamp(range)

        // 只計算 PAID 的訂單；showDeleted 決定是否納入已刪除
        val paidOrders = allOrders
            .filter { it.status == "PAID" && it.createdAt >= cutoff }
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

    private fun cutoffTimestamp(range: DateRange): Long {
        val cal = Calendar.getInstance()
        return when (range) {
            DateRange.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            DateRange.WEEK -> { cal.add(Calendar.DAY_OF_YEAR, -7); cal.timeInMillis }
            DateRange.MONTH -> { cal.add(Calendar.MONTH, -1); cal.timeInMillis }
            DateRange.ALL -> 0L
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
