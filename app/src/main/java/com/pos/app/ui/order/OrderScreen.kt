package com.pos.app.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.util.SoundEffects
import com.pos.app.util.UsbPrinterManager
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.ui.theme.LocalPosColors
import com.pos.app.ui.theme.PosColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    onGoSettings: () -> Unit,
    appVersion: String,
    viewModel: OrderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val t = LocalPosColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCheckout by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var lastCheckout by remember { mutableStateOf<Pair<String, Double>?>(null) }
    val dateFormatter = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

    // 補登確認對話框
    var backfillDateLabel by remember { mutableStateOf("") }
    var showBackfillConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.backfillConfirmEvent.collect { dateLabel ->
            backfillDateLabel = dateLabel
            showBackfillConfirm = true
        }
    }
    if (showBackfillConfirm) {
        AlertDialog(
            onDismissRequest = { showBackfillConfirm = false; viewModel.cancelBackfill() },
            containerColor = t.surface,
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
            title = { Text("⚠️ 補登模式確認", color = t.error, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "您正在補登 $backfillDateLabel 的訂單。\n\n" +
                    "此訂單的 createdAt 將記錄為該日期，今日報表將看不到它。\n\n確認繼續補登？",
                    color = t.textSub
                )
            },
            confirmButton = {
                Button(
                    onClick = { showBackfillConfirm = false; viewModel.confirmBackfill() },
                    colors = ButtonDefaults.buttonColors(containerColor = t.accent),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("確認補登", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBackfillConfirm = false; viewModel.cancelBackfill() },
                    border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("取消", color = t.textSub) }
            }
        )
    }

    // 結帳 errorMessage → Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Row {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { viewModel.updateSelectedDate(it) }
                        showDatePicker = false
                    }) { Text("確定") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updateSelectedDate(System.currentTimeMillis())
                    showDatePicker = false
                }) { Text("今天") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showCheckout) {
        CheckoutDialog(
            tableName = uiState.selectedTable?.tableName ?: "",
            orderItems = uiState.orderItems,
            total = uiState.total,
            remark = uiState.remark,
            selectedDate = uiState.selectedDate,
            errorMessage = uiState.errorMessage,
            onRemarkChange = { viewModel.updateRemark(it) },
            onConfirm = {
                val tName = uiState.selectedTable?.tableName ?: ""
                val tTotal = uiState.total
                val tItems = uiState.orderItems.toList()
                val tRemark = uiState.remark
                val tOrderId = uiState.order?.id ?: 0L
                val tCreatedAt = uiState.order?.createdAt ?: System.currentTimeMillis()
                val shouldPrint = uiState.printCheckoutEnabled
                viewModel.payOrder {
                    showCheckout = false
                    lastCheckout = tName.takeIf { it.isNotEmpty() }?.let { it to tTotal }
                    SoundEffects.playPaymentSuccess()
                    if (shouldPrint) {
                        scope.launch {
                            UsbPrinterManager.printCheckoutReceipt(
                                context, tName, tItems, tTotal, tRemark, tOrderId, tCreatedAt
                            )
                        }
                    }
                }
            },
            onDismiss = { showCheckout = false; viewModel.clearErrorMessage() },
            t = t
        )
    }
    if (showCancel && uiState.order != null) {
        CancelOrderDialog(
            tableName = uiState.selectedTable?.tableName ?: "",
            onConfirm = { viewModel.cancelOrder(); showCancel = false },
            onDismiss = { showCancel = false },
            t = t
        )
    }

    val openCount = uiState.openOrderTotals.count { it.value > 0.0 }

    Scaffold(
        containerColor = t.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues).background(t.bg)) {
            val compact = maxHeight < 900.dp || maxWidth > maxHeight

            Column(modifier = Modifier.fillMaxSize()) {
            // TopBar
                PosTopBar(title = "記帳點餐", subtitle = "火鍋店 POS", compact = compact, openCount = openCount, t = t, rightContent = {
                lastCheckout?.let { (name, amt) ->
                    Text("✓ $name NT${"$"}${amt.toLong()}", color = t.success, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                }
                val todayStart = remember { startOfDay(System.currentTimeMillis()) }
                val isToday = uiState.selectedDate == todayStart
                val dateLabel = if (isToday) "今天" else dateFormatter.format(Date(uiState.selectedDate))
                val dateTint = if (isToday) t.textSub else t.error
                TextButton(onClick = { showDatePicker = true }) {
                    Text(dateLabel, color = dateTint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            })

            // 補登模式警示橫條（Step 2）
            if (uiState.isBackfillMode) {
                val displayDate = dateFormatter.format(Date(uiState.selectedDate))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.error.copy(alpha = 0.13f))
                        .border(
                            width = 1.dp,
                            color = t.error.copy(alpha = 0.35f),
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "⚠️  補登模式：$displayDate　今日報表不計入",
                        color = t.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.resetToToday() }) {
                        Text("回到今天", color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Table selector
                TableSelectorRow(tables = uiState.tables, selected = uiState.selectedTable, totals = uiState.openOrderTotals, compact = compact, onSelect = { viewModel.selectTable(it) }, t = t)

            // Main area
                Row(modifier = Modifier.fillMaxSize()) {
                // Left: categories + menu
                    Column(modifier = Modifier.weight(1.62f).fillMaxHeight()) {
                    // Category chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.background(t.bg)
                    ) {
                        items(uiState.groups, key = { it.code }) { group ->
                            PosChip(label = group.name, active = uiState.selectedCategory == group.code, compact = compact, onClick = { viewModel.selectCategory(group.code) }, t = t)
                        }
                    }
                    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))
                    // Menu grid
                    if (uiState.menuItems.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("此分類目前沒有可用品項", color = t.textMuted, fontSize = 14.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (compact) 118.dp else 130.dp),
                            contentPadding = PaddingValues(if (compact) 8.dp else 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.menuItems, key = { it.id }) { item ->
                                MenuCard(
                                    item = item,
                                    qty = viewModel.getQuantityInOrder(item.id),
                                    compact = compact,
                                    repeatIntervalMs = uiState.qtyRepeatIntervalMs,
                                    repeatInitialDelayMs = uiState.qtyRepeatInitialDelayMs,
                                    hapticEnabled = uiState.hapticEnabled,
                                    onAdd = { viewModel.addItem(item) },
                                    onRemove = { viewModel.removeItem(item) },
                                    t = t
                                )
                            }
                        }
                    }
                }
                // Vertical divider
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(t.border))
                // Right: order panel
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    OrderPanel(
                        table = uiState.selectedTable, order = uiState.orderItems, total = uiState.total,
                        remark = uiState.remark, onRemarkChange = { viewModel.updateRemark(it) },
                        onDelete = { viewModel.deleteOrderItem(it) }, onCancel = { showCancel = true },
                        onCheckout = { showCheckout = true }, compact = compact, t = t
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun PosTopBar(title: String, subtitle: String, compact: Boolean, openCount: Int = 0, t: PosColors, rightContent: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 56.dp).background(t.topbar).padding(horizontal = if (compact) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.width(4.dp).height(if (compact) 20.dp else 24.dp).clip(RoundedCornerShape(2.dp)).background(t.accent))
            Column {
                Text(title, color = t.text, fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 16.sp, letterSpacing = 0.02.sp)
                Text(subtitle, color = t.textMuted, fontSize = if (compact) 10.sp else 11.sp)
            }
            // OPEN 桌數徽章（Step 5）
            if (openCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(t.error.copy(alpha = 0.18f))
                        .border(1.dp, t.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "🔔 $openCount 桌未結帳",
                        color = t.error,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = rightContent)
    }
}

@Composable
private fun TableSelectorRow(tables: List<TableEntity>, selected: TableEntity?, totals: Map<Long, Double>, compact: Boolean, onSelect: (TableEntity) -> Unit, t: PosColors) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.background(t.surface).fillMaxWidth()
    ) {
        item {
            Text("桌號", color = t.textMuted, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 4.dp).wrapContentHeight(Alignment.CenterVertically))
        }
        items(tables, key = { it.id }) { table ->
            val sel = table.id == selected?.id
            val tableTotal = totals[table.id] ?: 0.0
            val occ = tableTotal > 0.0
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(when { sel -> t.accent; occ -> t.occupiedBg; else -> t.card })
                    .border(1.dp, when { sel -> t.accent; occ -> t.occupied; else -> t.border }, RoundedCornerShape(10.dp))
                    .clickable { onSelect(table) }
                    .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 5.dp else 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(table.tableName, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (compact) 12.sp else 13.sp,
                    color = if (sel) Color.White else if (occ) t.occupied else t.textSub)
                if (occ) {
                    Text("NT${"$"}${tableTotal.toLong()}", fontSize = if (compact) 9.sp else 10.sp, fontWeight = FontWeight.Bold,
                        color = if (sel) Color.White.copy(0.85f) else t.occupied)
                }
            }
        }
    }
}

@Composable
private fun PosChip(label: String, active: Boolean, compact: Boolean, onClick: () -> Unit, t: PosColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) t.accent else t.card)
            .border(1.dp, if (active) t.accent else t.border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = if (compact) 13.dp else 16.dp, vertical = if (compact) 5.dp else 6.dp)
    ) {
        Text(label, color = if (active) Color.White else t.textSub, fontSize = if (compact) 12.sp else 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun MenuCard(
    item: MenuItemEntity,
    qty: Int,
    compact: Boolean,
    repeatIntervalMs: Int,
    repeatInitialDelayMs: Int,
    hapticEnabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    t: PosColors
) {
    val active = qty > 0

    // Bubble state
    var bubbleVisible by remember { mutableStateOf(false) }
    var bubbleIsPlus by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var hideJob: Job? by remember { mutableStateOf(null) }
    val haptics = LocalHapticFeedback.current
    val onAddState = rememberUpdatedState(onAdd)

    // qty 歸零時立即隱藏氣泡
    LaunchedEffect(qty) {
        if (qty <= 0) {
            hideJob?.cancel()
            bubbleVisible = false
        }
    }

    // 單擊放開後保留氣泡 600ms 再隱藏；連續模式放開時也走相同收尾
    fun showBubble(isPlus: Boolean) {
        hideJob?.cancel()
        bubbleIsPlus = isPlus
        bubbleVisible = true
    }
    fun scheduleHideBubble() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(600)
            bubbleVisible = false
        }
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) t.accentDim else t.card)
                .border(1.dp, if (active) t.accent else t.border, RoundedCornerShape(12.dp))
                .pointerInput(repeatIntervalMs, repeatInitialDelayMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        showBubble(true)
                        if (hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAddState.value()
                        var repeatJob: Job? = null
                        repeatJob = scope.launch {
                            delay(repeatInitialDelayMs.toLong())
                            if (hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            while (true) {
                                onAddState.value()
                                delay(repeatIntervalMs.toLong())
                            }
                        }
                        waitForUpOrCancellation()
                        repeatJob?.cancel()
                        scheduleHideBubble()
                        down.consume()
                    }
                }
                .padding(if (compact) 8.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Text(
                item.name,
                color = if (active) t.accent else t.text,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (compact) 13.sp else 14.sp,
                maxLines = 2
            )
            Text(
                "NT${"$"}${item.price.toLong()}",
                color = t.accent,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (active) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(false) {}
                ) {
                    RepeatableQtyButton(
                        label = "−",
                        isPlus = false,
                        intervalMs = repeatIntervalMs,
                        initialDelayMs = repeatInitialDelayMs,
                        hapticEnabled = hapticEnabled,
                        onTrigger = onRemove,
                        onPressStart = { showBubble(false) },
                        onPressEnd = { scheduleHideBubble() }
                    )
                    Text(
                        "$qty",
                        color = t.accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 14.sp else 16.sp,
                        modifier = Modifier.widthIn(min = 20.dp)
                    )
                    RepeatableQtyButton(
                        label = "+",
                        isPlus = true,
                        intervalMs = repeatIntervalMs,
                        initialDelayMs = repeatInitialDelayMs,
                        hapticEnabled = hapticEnabled,
                        onTrigger = onAdd,
                        onPressStart = { showBubble(true) },
                        onPressEnd = { scheduleHideBubble() }
                    )
                }
            } else {
                Text("點選加入", color = t.textMuted, fontSize = if (compact) 10.sp else 11.sp)
            }
        }

        // 即時數字氣泡（懸浮在卡片上方）
        if (bubbleVisible) {
            val bubbleBg = if (bubbleIsPlus) Color(0xFFFFC400) else Color(0xFF00C853)
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -180),
                properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(bubbleBg)
                        .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (bubbleIsPlus) "+" else "−",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            "$qty",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 可長按連續觸發的數量按鈕。
 * - 短按：down 立即觸發一次（onTrigger）。
 * - 長按超過 [initialDelayMs]：以 [intervalMs] 間隔反覆觸發直到放開。
 * - 加減使用不同色調：+ 亮黃；− 亮綠。
 */
@Composable
private fun RepeatableQtyButton(
    label: String,
    isPlus: Boolean,
    intervalMs: Int,
    initialDelayMs: Int,
    hapticEnabled: Boolean,
    onTrigger: () -> Unit,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val bg = if (isPlus) Color(0xFFFFD600).copy(alpha = 0.18f) else Color(0xFF00E676).copy(alpha = 0.18f)
    val fg = if (isPlus) Color(0xFFFFC400) else Color(0xFF00C853)
    val border = if (isPlus) Color(0xFFFFC400).copy(alpha = 0.55f) else Color(0xFF00C853).copy(alpha = 0.55f)

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 保留最新的 onTrigger 參考，避免 pointerInput 重啟
    val triggerState = rememberUpdatedState(onTrigger)
    val pressStartState = rememberUpdatedState(onPressStart)
    val pressEndState = rememberUpdatedState(onPressEnd)

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .pointerInput(intervalMs, initialDelayMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume() // 立即消費，避免父層 pointerInput 重複觸發
                    pressStartState.value()
                    // 單擊立即觸發 + 輕震動回饋
                    if (hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    triggerState.value()
                    var repeatJob: Job? = null
                    repeatJob = scope.launch {
                        delay(initialDelayMs.toLong())
                        // 進入連續模式：較強回饋一次
                        if (hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        var count = 0
                        while (true) {
                            triggerState.value()
                            count++
                            if (hapticEnabled && count % 5 == 0) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            delay(intervalMs.toLong())
                        }
                    }
                    // 等到放開或取消
                    waitForUpOrCancellation()
                    repeatJob?.cancel()
                    pressEndState.value()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun OrderPanel(table: TableEntity?, order: List<OrderItemEntity>, total: Double, remark: String, onRemarkChange: (String) -> Unit, onDelete: (OrderItemEntity) -> Unit, onCancel: () -> Unit, onCheckout: () -> Unit, compact: Boolean, t: PosColors) {
    val itemCount = order.size
    val quantityCount = order.sumOf { it.quantity }

    Column(modifier = Modifier.fillMaxHeight()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(table?.tableName ?: "尚未選桌", color = t.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("共 $itemCount 項目 $quantityCount 件", color = t.textMuted, fontSize = if (compact) 11.sp else 12.sp)
                }
                table?.seats?.let { Text("($it 人桌)", color = t.textMuted, fontSize = 12.sp) }
            }
    if (order.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onCancel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("取消訂單", color = t.textSub, fontSize = 11.sp) }
                    }
        }
        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))

        // Items
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (order.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 24.dp else 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛒", fontSize = 32.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("尚未點餐", color = t.textMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(order, key = { it.id }) { oi ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(oi.name, color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("NT${"$"}${oi.price.toLong()} × ${oi.quantity} = NT${"$"}${(oi.price * oi.quantity).toLong()}",
                                color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onDelete(oi) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "刪除", tint = t.error, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(t.border))
                }
            }
        }

        // Bottom: remark + total + button
        Column(
            modifier = Modifier.background(t.surface).padding(if (compact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            OutlinedTextField(
                value = remark, onValueChange = onRemarkChange,
                placeholder = { Text("備註（選填）", color = t.textMuted, fontSize = 13.sp) },
                maxLines = 2, modifier = Modifier.fillMaxWidth().heightIn(max = if (compact) 64.dp else 72.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
                    focusedTextColor = t.text, unfocusedTextColor = t.text,
                    cursorColor = t.accent
                ), shape = RoundedCornerShape(8.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("合計", color = t.textSub, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("NT${"$"}${total.toLong()}", color = t.accent, fontSize = if (compact) 24.sp else 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Button(
                onClick = onCheckout,
                enabled = order.isNotEmpty() && table != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = t.accent, disabledContainerColor = t.border),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("送出結帳 →", fontSize = if (compact) 15.sp else 17.sp, modifier = Modifier.padding(vertical = if (compact) 2.dp else 4.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CancelOrderDialog(tableName: String, onConfirm: () -> Unit, onDismiss: () -> Unit, t: PosColors) {
    var confirmEnabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text("取消訂單", color = t.error, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("確定取消 $tableName 的全部點餐？", color = t.text, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(t.error.copy(alpha = 0.1f))
                        .border(1.dp, t.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text("⚠️ 此操作無法復原，訂單將從報表中消失。", color = t.error, fontSize = 13.sp)
                }
                LaunchedEffect(Unit) {
                    delay(500L)
                    confirmEnabled = true
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = t.error, disabledContainerColor = t.border),
                shape = RoundedCornerShape(8.dp)
            ) { Text("確定取消訂單", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("返回", color = t.textSub) }
        }
    )
}

@Composable
private fun CheckoutDialog(tableName: String, orderItems: List<OrderItemEntity>, total: Double, remark: String, selectedDate: Long, errorMessage: String?, onRemarkChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, t: PosColors) {
    val quantityCount = orderItems.sumOf { it.quantity }
    val todayStart = remember { startOfDay(System.currentTimeMillis()) }
    val isToday = selectedDate == todayStart
    val dateLabel = if (isToday) "今天" else SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(selectedDate))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        // Step 1：禁止點外部或按返回鍵誤關
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("確認結帳", color = t.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (isToday) t.accentDim else t.error.copy(alpha = 0.15f))
                        .border(1.dp, if (isToday) t.accent else t.error, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(dateLabel, color = if (isToday) t.accent else t.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(t.accentDim).border(1.dp, t.accent, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(tableName, color = t.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text("共 ${orderItems.size} 個品項 $quantityCount 件", color = t.textMuted, fontSize = 13.sp)
                }
                Column(modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(12.dp)).background(t.bg).border(1.dp, t.border, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    orderItems.forEach { oi ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${oi.name} × ${oi.quantity}", color = t.text, fontSize = 14.sp)
                            Text("NT${"$"}${(oi.price * oi.quantity).toLong()}", color = t.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("應收金額", color = t.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("NT${"$"}${total.toLong()}", color = t.accent, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedTextField(
                    value = remark, onValueChange = onRemarkChange,
                    placeholder = { Text("備註（選填）", color = t.textMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.accent, unfocusedBorderColor = t.border, focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent),
                    shape = RoundedCornerShape(8.dp)
                )
                // Step 3：結帳失敗時在對話框內顯示錯誤
                if (!errorMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(t.error.copy(alpha = 0.12f))
                            .border(1.dp, t.error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text("⚠️ $errorMessage", color = t.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = t.accent), shape = RoundedCornerShape(10.dp)) { Text("✓ 確認收款", fontWeight = FontWeight.Bold) } },
        dismissButton = { OutlinedButton(onClick = onDismiss, border = androidx.compose.foundation.BorderStroke(1.dp, t.border), shape = RoundedCornerShape(10.dp)) { Text("返回修改", color = t.textSub) } }
    )
}
