package com.pos.app.ui.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pos.app.data.db.entity.ReservationEntity
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.ui.theme.LocalPosColors
import com.pos.app.ui.theme.PosColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

// ─── Layout constants ─────────────────────────────────────────────────────────
private val TIME_COL_W  = 52.dp
private val TABLE_COL_W = 96.dp
private val HOUR_H      = 64.dp
private val HEADER_H    = 40.dp

private val importanceColors = listOf(
    Color(0xFF4CAF50),
    Color(0xFFFFC107),
    Color(0xFFF44336)
)

// ─── Helpers ──────────────────────────────────────────────────────────────────
private fun timeToMinutes(t: String): Int {
    val p = t.split(":")
    return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
}
private fun minutesToTime(m: Int): String = "%02d:%02d".format(m / 60 % 24, m % 60)

// ─── Drag state ───────────────────────────────────────────────────────────────
private data class ResDragState(
    val reservation: ReservationEntity,
    val origTableIndex: Int,
    val accumDeltaPx: Offset = Offset.Zero
)
private data class DragTarget(val tableIndex: Int, val startMin: Int, val endMin: Int)
private data class DialogConfig(
    val reservation: ReservationEntity?,
    val tableId: Long,
    val startTime: String
)

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun ReservationDayScreen(viewModel: ReservationViewModel, date: LocalDate) {
    val t               = LocalPosColors.current
    val reservations    by viewModel.dayReservations.collectAsState()
    val tables          by viewModel.activeTables.collectAsState()
    val bizStart        by viewModel.bizStart.collectAsState()
    val bizEnd          by viewModel.bizEnd.collectAsState()
    val breakStart      by viewModel.breakStart.collectAsState()
    val breakEnd        by viewModel.breakEnd.collectAsState()
    val defaultDuration by viewModel.defaultDuration.collectAsState()

    val dowLabel    = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("zh", "TW"))
    val bizStartMin = timeToMinutes(bizStart)
    val bizEndMin   = timeToMinutes(bizEnd)

    val hours = remember(bizStart, bizEnd) {
        buildList {
            var cur = bizStartMin
            while (cur < bizEndMin) { add(minutesToTime(cur)); cur += 60 }
        }
    }

    var dialogState by remember { mutableStateOf<DialogConfig?>(null) }
    // Use mutableStateOf so closures always read current value via .value
    val dragStateHolder = remember { mutableStateOf<ResDragState?>(null) }
    var dragState by dragStateHolder

    // Stable screen-space origin of the table grid Box (captured once, not scroll-dependent)
    var gridBoxOffset by remember { mutableStateOf(Offset.Zero) }

    val vertScroll  = rememberScrollState()
    val horizScroll = rememberScrollState()
    val density     = LocalDensity.current
    val tableColWPx = with(density) { TABLE_COL_W.toPx() }
    val hourHPx     = with(density) { HOUR_H.toPx() }

    // Visual ghost target — recomputed each frame from current dragState
    val dragTarget: DragTarget? = dragState?.let { ds ->
        val origStart = timeToMinutes(ds.reservation.startTime)
        val origEnd   = timeToMinutes(ds.reservation.endTime)
        val duration  = (origEnd - origStart).coerceAtLeast(15)

        val rawTable  = ds.origTableIndex + (ds.accumDeltaPx.x / tableColWPx)
        val tblIdx    = rawTable.roundToInt().coerceIn(0, (tables.size - 1).coerceAtLeast(0))

        val deltaMin  = (ds.accumDeltaPx.y / hourHPx * 60).roundToInt()
        val rawStart  = origStart + deltaMin
        val snapped   = (rawStart / 5 * 5).coerceIn(bizStartMin, (bizEndMin - duration).coerceAtLeast(bizStartMin))
        DragTarget(tblIdx, snapped, snapped + duration)
    }

    // Callbacks — built fresh each composition so onDragEnd always computes from live dragState
    val onDragStartCb: (ReservationEntity, Int) -> Unit = { res, tblIdx ->
        dragState = ResDragState(res, tblIdx)
    }
    val onDragDeltaCb: (Offset) -> Unit = { delta ->
        dragState = dragState?.copy(accumDeltaPx = dragState!!.accumDeltaPx + delta)
    }
    // FIX: compute target here from the live MutableState, not from captured val
    val onDragEndCb: () -> Unit = {
        val ds = dragStateHolder.value
        if (ds != null && tables.isNotEmpty()) {
            val origStart  = timeToMinutes(ds.reservation.startTime)
            val origEnd    = timeToMinutes(ds.reservation.endTime)
            val duration   = (origEnd - origStart).coerceAtLeast(15)
            val rawTable   = ds.origTableIndex + (ds.accumDeltaPx.x / tableColWPx)
            val tblIdx     = rawTable.roundToInt().coerceIn(0, tables.size - 1)
            val deltaMin   = (ds.accumDeltaPx.y / hourHPx * 60).roundToInt()
            val rawStart   = origStart + deltaMin
            val snapped    = (rawStart / 5 * 5).coerceIn(bizStartMin, (bizEndMin - duration).coerceAtLeast(bizStartMin))
            val newTbl     = tables.getOrNull(tblIdx) ?: tables[ds.origTableIndex]
            viewModel.upsertReservation(
                ds.reservation.copy(
                    tableId   = newTbl.id,
                    tableName = newTbl.tableName,
                    startTime = minutesToTime(snapped),
                    endTime   = minutesToTime(snapped + duration)
                )
            )
        }
        dragState = null
    }
    val onDragCancelCb: () -> Unit = { dragState = null }

    // Dialog
    dialogState?.let { cfg ->
        ReservationDialog(
            initial         = cfg.reservation,
            defaultDate     = date,
            defaultTableId  = cfg.tableId,
            defaultStartTime = cfg.startTime,
            defaultDuration = defaultDuration,
            tables          = tables,
            onSave          = { viewModel.upsertReservation(it) },
            onDelete        = { viewModel.deleteReservation(it) },
            onDismiss       = { dialogState = null },
            t               = t
        )
    }

    Box(Modifier.fillMaxSize()) {

        Column(Modifier.fillMaxSize().background(t.bg)) {

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth().height(56.dp)
                    .background(t.topbar).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(4.dp).height(24.dp)
                        .clip(RoundedCornerShape(2.dp)).background(t.accent))
                    Column {
                        Text(
                            "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日（$dowLabel）",
                            color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                        Text("長按方塊可拖曳換桌或換時間", color = t.textMuted, fontSize = 11.sp)
                    }
                }
                androidx.compose.material3.TextButton(onClick = { viewModel.clearSelectedDate() }) {
                    Text("← 返回", color = t.textSub, fontSize = 13.sp)
                }
            }

            HorizontalDivider(color = t.border, thickness = 1.dp)

            if (tables.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("尚無啟用中的桌次，請先至「桌號設定」新增桌次",
                        color = t.textMuted, fontSize = 14.sp)
                }
                return@Column
            }

            // Sticky table header
            Row(
                modifier = Modifier
                    .fillMaxWidth().background(t.surface).height(HEADER_H)
                    .horizontalScroll(horizScroll, enabled = false)
            ) {
                Spacer(Modifier.width(TIME_COL_W))
                tables.forEach { tbl ->
                    Box(Modifier.width(TABLE_COL_W).fillMaxHeight(),
                        contentAlignment = Alignment.Center) {
                        Text(tbl.tableName, color = t.text, fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            HorizontalDivider(color = t.border, thickness = 1.dp)

            Row(Modifier.fillMaxSize()) {

                // Fixed time column
                Column(
                    modifier = Modifier
                        .width(TIME_COL_W).fillMaxHeight()
                        .verticalScroll(vertScroll)
                        .background(t.surface).zIndex(1f)
                ) {
                    hours.forEach { hour ->
                        Box(
                            modifier = Modifier
                                .width(TIME_COL_W).height(HOUR_H)
                                .border(Dp.Hairline, t.border),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(hour, color = t.textMuted, fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                // Table grid — capture stable screen origin
                Box(
                    modifier = Modifier
                        .weight(1f).fillMaxHeight()
                        .onGloballyPositioned { gridBoxOffset = it.positionInRoot() }
                        .horizontalScroll(horizScroll)
                ) {
                    Row(Modifier.verticalScroll(vertScroll)) {
                        tables.forEachIndexed { tableIdx, tbl ->
                            val tblRes = reservations.filter { it.tableId == tbl.id }
                            DraggableTableColumn(
                                table        = tbl,
                                tableIndex   = tableIdx,
                                hours        = hours,
                                reservations = tblRes,
                                bizStartMin  = bizStartMin,
                                breakStart   = breakStart,
                                breakEnd     = breakEnd,
                                t            = t,
                                draggingResId = dragState?.reservation?.id,
                                onEmptyClick  = { hour ->
                                    if (dragState == null)
                                        dialogState = DialogConfig(null, tbl.id, hour)
                                },
                                onBlockClick  = { res ->
                                    if (dragState == null)
                                        dialogState = DialogConfig(res, res.tableId, res.startTime)
                                },
                                onDragStart   = { res -> onDragStartCb(res, tableIdx) },
                                onDragDelta   = onDragDeltaCb,
                                onDragEnd     = onDragEndCb,
                                onDragCancel  = onDragCancelCb
                            )
                        }
                    }
                }
            }
        }

        // Ghost overlay
        if (dragState != null && dragTarget != null) {
            val ds     = dragState!!
            val target = dragTarget
            val duration   = (target.endMin - target.startMin).coerceAtLeast(15)
            val ghostX     = gridBoxOffset.x - horizScroll.value + target.tableIndex * tableColWPx
            val ghostY     = gridBoxOffset.y - vertScroll.value +
                    (target.startMin - bizStartMin) / 60f * hourHPx
            val ghostH     = duration / 60f * hourHPx

            Box(
                modifier = Modifier
                    .zIndex(100f)
                    .offset { IntOffset(ghostX.roundToInt(), ghostY.roundToInt()) }
                    .width(TABLE_COL_W)
                    .height(with(density) { ghostH.toDp() })
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(importanceColors[ds.reservation.importance.coerceIn(0, 2)])
                    .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(4.dp)
            ) {
                Column {
                    Text(ds.reservation.guestName,
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${minutesToTime(target.startMin)}–${minutesToTime(target.endMin)}",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp)
                    tables.getOrNull(target.tableIndex)?.let {
                        Text(it.tableName,
                            color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ─── Table column with per-block drag support ─────────────────────────────────
@Composable
private fun DraggableTableColumn(
    table: TableEntity,
    tableIndex: Int,
    hours: List<String>,
    reservations: List<ReservationEntity>,
    bizStartMin: Int,
    breakStart: String,
    breakEnd: String,
    t: PosColors,
    draggingResId: Long?,
    onEmptyClick: (String) -> Unit,
    onBlockClick: (ReservationEntity) -> Unit,
    onDragStart: (ReservationEntity) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val breakStartMin = if (breakStart.isNotEmpty()) timeToMinutes(breakStart) else -1
    val breakEndMin   = if (breakEnd.isNotEmpty())   timeToMinutes(breakEnd)   else -1

    Box(Modifier.width(TABLE_COL_W)) {
        // Background hour cells
        Column {
            hours.forEach { hour ->
                val hourMin = timeToMinutes(hour)
                val isBreak = breakStartMin >= 0 && breakEndMin >= 0
                        && hourMin >= breakStartMin && hourMin < breakEndMin
                Box(
                    modifier = Modifier
                        .width(TABLE_COL_W).height(HOUR_H)
                        .background(if (isBreak) t.border.copy(alpha = 0.4f) else t.bg)
                        .border(Dp.Hairline, t.border)
                        .clickable(enabled = !isBreak && draggingResId == null) { onEmptyClick(hour) }
                )
            }
        }

        // Reservation blocks — each uses key() so rememberUpdatedState is per-item
        reservations.forEach { res ->
            key(res.id) {
                // FIX: rememberUpdatedState ensures pointerInput always calls the latest lambda
                val latestOnDragStart  = rememberUpdatedState(onDragStart)
                val latestOnDragDelta  = rememberUpdatedState(onDragDelta)
                val latestOnDragEnd    = rememberUpdatedState(onDragEnd)
                val latestOnDragCancel = rememberUpdatedState(onDragCancel)

                val startMin  = timeToMinutes(res.startTime)
                val endMin    = timeToMinutes(res.endTime).coerceAtLeast(startMin + 15)
                val topDp     = ((startMin - bizStartMin) / 60f * HOUR_H.value).dp
                val heightDp  = ((endMin - startMin) / 60f * HOUR_H.value).dp.coerceAtLeast(20.dp)
                val color     = importanceColors[res.importance.coerceIn(0, 2)]
                val isDragging = res.id == draggingResId

                Box(
                    modifier = Modifier
                        .offset(y = topDp)
                        .width(TABLE_COL_W)
                        .height(heightDp)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .alpha(if (isDragging) 0.35f else 1f)
                        .background(color.copy(alpha = if (isDragging) 0.3f else 0.85f))
                        // Drag gesture (long press to start)
                        .pointerInput(res.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart  = { latestOnDragStart.value(res) },
                                onDrag       = { change, dragAmount ->
                                    change.consume()
                                    latestOnDragDelta.value(dragAmount)
                                },
                                onDragEnd    = { latestOnDragEnd.value() },
                                onDragCancel = { latestOnDragCancel.value() }
                            )
                        }
                        .clickable(enabled = !isDragging && draggingResId == null) {
                            onBlockClick(res)
                        }
                        .padding(4.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column {
                        Text(res.guestName, color = Color.White, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (heightDp > 36.dp) {
                            Text("${res.startTime}–${res.endTime}",
                                color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, maxLines = 1)
                        }
                        if (heightDp > 52.dp && res.guestPhone.isNotEmpty()) {
                            val ph = res.guestPhone
                            Text("☎ ${if (ph.length > 4) "…" + ph.takeLast(4) else ph}",
                                color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
