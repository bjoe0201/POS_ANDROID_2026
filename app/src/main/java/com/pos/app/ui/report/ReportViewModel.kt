package com.pos.app.ui.report

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.repository.OrderRepository
import com.pos.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

enum class DateRange { TODAY, YESTERDAY, WEEK, MONTH, YEAR, ALL, CUSTOM }

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
    val isLoading: Boolean = true,
    val printDetailEnabled: Boolean = false
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository
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
        settingsRepository.printDetailEnabled
            .onEach { v -> _uiState.update { it.copy(printDetailEnabled = v) } }
            .launchIn(viewModelScope)
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
            DateRange.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
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

    /**
     * 將目前報表畫面上的資料匯出為 CSV（依畫面版面輸出多區段）：
     *   1. 總覽（總營業額、總筆數、平均客單）
     *   2. 品項銷售排行
     *   3. 群組銷售排行
     *   4. 訂單明細（每筆訂單展開成多列品項）
     */
    fun exportCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.orders.isEmpty()) {
                _uiState.update { it.copy(message = "此期間無資料可匯出") }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = buildReportCsv(state)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        // UTF-8 BOM 讓 Excel 開啟中文不亂碼
                        os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        os.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("無法開啟輸出串流")
                }
            }
            _uiState.update {
                it.copy(
                    message = if (result.isSuccess) "已匯出 ${state.orders.size} 筆訂單" else "匯出失敗：${result.exceptionOrNull()?.message ?: "未知錯誤"}"
                )
            }
        }
    }

    private fun buildReportCsv(state: ReportUiState): String {
        val dateTimeFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val rangeLabel = when (state.dateRange) {
            DateRange.TODAY -> "今日"
            DateRange.YESTERDAY -> "昨天"
            DateRange.WEEK -> "本週"
            DateRange.MONTH -> "本月"
            DateRange.YEAR -> "今年"
            DateRange.ALL -> "全部"
            DateRange.CUSTOM -> "自訂"
        }
        val (start, end) = resolveDateBounds(state.dateRange, state.customStartDate, state.customEndDate)
        val rangeStr = if (state.dateRange == DateRange.ALL) "全部" else "${dateFmt.format(java.util.Date(start))} ~ ${dateFmt.format(java.util.Date(end))}"

        val sb = StringBuilder()
        fun line(vararg cols: Any?) {
            sb.appendLine(cols.joinToString(",") { csvEscape(it?.toString().orEmpty()) })
        }

        // 檔頭
        line("報表匯出")
        line("日期區間", "$rangeLabel（$rangeStr）")
        line("含已刪除", if (state.showDeleted) "是" else "否")
        line("產生時間", dateTimeFmt.format(java.util.Date()))
        sb.appendLine()

        // 1. 總覽
        line("===== 總覽 =====")
        line("項目", "數值")
        line("總營業額", "NT\$%.0f".format(state.totalRevenue))
        line("總筆數", "${state.totalOrders} 筆")
        line("平均客單", "NT\$%.0f".format(state.avgOrderValue))
        sb.appendLine()

        // 2. 品項銷售排行
        line("===== 品項銷售排行 =====")
        line("排名", "品項", "數量")
        state.itemRanking.forEachIndexed { idx, (name, qty) ->
            line(idx + 1, name, qty)
        }
        if (state.itemRanking.isEmpty()) line("（無資料）")
        sb.appendLine()

        // 3. 群組銷售排行
        line("===== 群組銷售排行 =====")
        line("排名", "群組", "數量", "營業額")
        state.groupRanking.forEachIndexed { idx, g ->
            line(idx + 1, g.groupName, g.quantity, "NT\$%.0f".format(g.revenue))
        }
        if (state.groupRanking.isEmpty()) line("（無資料）")
        sb.appendLine()

        // 4. 訂單明細
        line("===== 訂單明細 =====")
        line("訂單ID", "桌號", "建立時間", "狀態", "已刪除", "品項", "群組", "數量", "單價", "小計")
        state.orders.forEach { owi ->
            val o = owi.order
            val createdStr = dateTimeFmt.format(java.util.Date(o.createdAt))
            val deletedFlag = if (o.isDeleted) "是" else ""
            if (owi.items.isEmpty()) {
                line(o.id, o.tableName, createdStr, o.status, deletedFlag, "", "", "", "", "")
            } else {
                owi.items.forEach { item ->
                    line(
                        o.id,
                        o.tableName,
                        createdStr,
                        o.status,
                        deletedFlag,
                        item.name,
                        item.menuGroupName,
                        item.quantity,
                        "%.0f".format(item.price),
                        "%.0f".format(item.price * item.quantity)
                    )
                }
            }
        }

        return sb.toString()
    }

    /** CSV 欄位跳脫：含逗號 / 引號 / 換行時用雙引號包起來，內部引號加倍。 */
    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return ""
        val needQuote = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        val escaped = s.replace("\"", "\"\"")
        return if (needQuote) "\"$escaped\"" else escaped
    }
}
