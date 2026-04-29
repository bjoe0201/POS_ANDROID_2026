package com.pos.app.ui.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pos.app.data.db.entity.ReservationEntity
import com.pos.app.ui.theme.LocalPosColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val DOW_LABELS = listOf("日", "一", "二", "三", "四", "五", "六")
private val timeChipColors = listOf(
    Color(0xFF355C7D),
    Color(0xFF3D5A80),
    Color(0xFF4C5C96)
)
private val countChipColors = listOf(
    Color(0xFF2A9D8F),
    Color(0xFFE9C46A),
    Color(0xFFE76F51)
)

private data class TimeSlotSummary(
    val startTime: String,
    val tableCount: Int,
    val maxImportance: Int
)

@Composable
fun ReservationCalendarScreen(
    viewModel: ReservationViewModel,
    daySummaryVisibleRows: Int = 3
) {
    val t = LocalPosColors.current
    val yearMonth by viewModel.currentYearMonth.collectAsState()
    val reservations by viewModel.monthReservations.collectAsState()
    val chipsPerRow by viewModel.calendarChipsPerRow.collectAsState()
    val activeTables by viewModel.activeTables.collectAsState()
    val totalActiveTableCount = activeTables.size

    Column(Modifier.fillMaxSize().background(t.bg)) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(t.topbar).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(t.accent))
            Spacer(Modifier.width(8.dp))
            Text("訂位管理", color = t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))

            // Today / Prev / YearMonth / Next
            val isCurrentMonth = yearMonth == YearMonth.now()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MonthNavButton("今天", if (isCurrentMonth) Color(0xFFFDD835) else t.textMuted) { viewModel.goToToday() }
                MonthNavButton("<", t.accent) { viewModel.prevMonth() }
                Text(
                    "${yearMonth.year} 年 ${yearMonth.monthValue} 月",
                    color = t.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                MonthNavButton(">", t.accent) { viewModel.nextMonth() }
            }
        }

        // Day-of-week header
        Row(Modifier.fillMaxWidth().background(t.surface).padding(vertical = 6.dp)) {
            DOW_LABELS.forEachIndexed { idx, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (idx) { 0 -> t.error; 6 -> t.accent; else -> t.textMuted }
                )
            }
        }

        // Calendar grid — fills remaining height, auto-scales rows
        val today = LocalDate.now()
        val firstDay = yearMonth.atDay(1)
        val startOffset = firstDay.dayOfWeek.value % 7  // SUN=0, MON=1, …
        val daysInMonth = yearMonth.lengthOfMonth()
        val numWeeks = ((startOffset + daysInMonth + 6) / 7)  // 4, 5, or 6

        // Build rows: each row = 7 nullable day numbers
        val allCells = buildList<Int?> {
            repeat(startOffset) { add(null) }
            (1..daysInMonth).forEach { add(it) }
            repeat(numWeeks * 7 - size) { add(null) }
        }
        val weeks = allCells.chunked(7)

        var swipeDeltaX by remember { mutableFloatStateOf(0f) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipeDeltaX = 0f },
                        onDragEnd = {
                            if (swipeDeltaX > 200f) viewModel.prevMonth()
                            else if (swipeDeltaX < -200f) viewModel.nextMonth()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeDeltaX += dragAmount
                        }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val date = yearMonth.atDay(day)
                            val dayStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val dayRes = reservations.filter { it.date == dayStr }
                            DayCell(
                                day = day,
                                isToday = date == today,
                                isSunday = date.dayOfWeek == DayOfWeek.SUNDAY,
                                reservations = dayRes,
                                totalActiveTableCount = totalActiveTableCount,
                                maxVisibleRows = daySummaryVisibleRows,
                                chipsPerRow = chipsPerRow,
                                t = t,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { viewModel.selectDate(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSunday: Boolean,
    reservations: List<ReservationEntity>,
    totalActiveTableCount: Int,
    maxVisibleRows: Int,
    chipsPerRow: Int,
    t: com.pos.app.ui.theme.PosColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isToday) t.accentDim2.copy(alpha = 0.3f) else t.card)
            .border(0.5.dp, if (isToday) t.accent else t.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val usedTableCount = remember(reservations) { reservations.map { it.tableId }.distinct().size }
        val usageRate = if (totalActiveTableCount > 0) {
            usedTableCount.toFloat() / totalActiveTableCount.toFloat()
        } else {
            0f
        }
        val usageBadgeColor = when {
            usedTableCount == 0 -> t.textMuted.copy(alpha = 0.45f)
            totalActiveTableCount <= 0 -> t.textMuted
            usageRate < 0.60f -> Color(0xFF2E7D32)
            usageRate < 0.80f -> Color(0xFFF9A825)
            else -> Color(0xFFC62828)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(22.dp)
                    .then(if (isToday) Modifier.clip(CircleShape).background(t.accent) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    day.toString(),
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isToday  -> Color.White
                        isSunday -> t.error
                        else     -> t.text
                    }
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(usageBadgeColor)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    usedTableCount.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        usedTableCount == 0 -> t.textMuted
                        usageRate in 0.60f..0.79f -> Color(0xFF1F2937)
                        else -> Color.White
                    }
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Aggregate by start time and show table count per time slot.
        val timeSlotSummaries = remember(reservations) {
            reservations
                .groupBy { it.startTime }
                .toSortedMap()
                .map { (time, list) ->
                    TimeSlotSummary(
                        startTime = time,
                        tableCount = list.map { it.tableId }.distinct().size,
                        maxImportance = list.maxOf { it.importance }.coerceIn(0, timeChipColors.lastIndex)
                    )
                }
        }

        val rows = maxVisibleRows.coerceAtLeast(1)
        val perRow = chipsPerRow.coerceIn(1, 4)
        val summaryMaxHeight = (rows * 14 + (rows - 1) * 2).dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = summaryMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            timeSlotSummaries.chunked(perRow).forEach { rowSummaries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowSummaries.forEach { summary ->
                        val timeChipColor = timeChipColors[summary.maxImportance]
                        val countChipColor = countChipColors[summary.maxImportance]
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(timeChipColor.copy(alpha = 0.32f))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                    .weight(1f, fill = false)
                            ) {
                                Text(
                                    summary.startTime,
                                    fontSize = 7.sp,
                                    color = t.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(countChipColor.copy(alpha = 0.86f))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "${summary.tableCount}",
                                    fontSize = 7.sp,
                                    color = if (summary.maxImportance == 1) Color(0xFF1F2937) else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthNavButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
