package com.pos.app.ui.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pos.app.data.db.entity.ReservationEntity
import com.pos.app.data.db.entity.TableEntity
import com.pos.app.ui.settings.TimePickerAlertDialog
import com.pos.app.ui.theme.PosColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val importanceColors = listOf(
    Color(0xFF4CAF50), // 0 一般
    Color(0xFFFFC107), // 1 重要
    Color(0xFFF44336)  // 2 非常重要
)
private val importanceLabels = listOf("一般", "重要", "非常重要")

@Composable
fun ReservationDialog(
    initial: ReservationEntity?,          // null = 新增
    defaultDate: LocalDate,
    defaultTableId: Long,
    defaultStartTime: String,
    defaultDuration: Int,
    tables: List<TableEntity>,
    onSave: (ReservationEntity) -> Unit,
    onDelete: (ReservationEntity) -> Unit,
    onDismiss: () -> Unit,
    t: PosColors
) {
    val isNew = initial == null
    val dateStr = defaultDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

    var guestName  by remember { mutableStateOf(initial?.guestName  ?: "") }
    var guestPhone by remember { mutableStateOf(initial?.guestPhone ?: "") }
    var guestCount by remember { mutableStateOf((initial?.guestCount ?: 0).let { if (it == 0) "" else it.toString() }) }
    var remark     by remember { mutableStateOf(initial?.remark     ?: "") }
    var importance by remember { mutableStateOf(initial?.importance ?: 0) }
    var startTime  by remember { mutableStateOf(initial?.startTime  ?: defaultStartTime) }
    var endTime    by remember { mutableStateOf(initial?.endTime    ?: addMinutes(defaultStartTime, defaultDuration)) }

    val initTableId = initial?.tableId ?: defaultTableId
    var selectedTable by remember {
        mutableStateOf(tables.firstOrNull { it.id == initTableId } ?: tables.firstOrNull())
    }

    var showTableMenu by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    if (showStartPicker) {
        TimePickerAlertDialog(currentTime = startTime, onConfirm = { showStartPicker = false; startTime = it }, onDismiss = { showStartPicker = false }, t = t)
    }
    if (showEndPicker) {
        TimePickerAlertDialog(currentTime = endTime, onConfirm = { showEndPicker = false; endTime = it }, onDismiss = { showEndPicker = false }, t = t)
    }
    if (showDeleteConfirm && initial != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = t.surface,
            title = { Text("確認刪除訂位", color = t.text, fontWeight = FontWeight.Bold) },
            text = { Text("確定要刪除 ${initial.guestName} 的訂位（${initial.tableName} ${initial.startTime}）？", color = t.textSub) },
            confirmButton = {
                Button(onClick = { onDelete(initial); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = t.error),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("確定刪除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = t.textSub) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Text(if (isNew) "新增訂位" else "編輯訂位", color = t.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Guest name
                OutlinedTextField(
                    value = guestName,
                    onValueChange = { guestName = it; nameError = false },
                    label = { Text("姓名 *", color = t.textMuted) },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(t)
                )

                // Phone
                OutlinedTextField(
                    value = guestPhone,
                    onValueChange = { guestPhone = it; phoneError = false },
                    label = { Text("電話 *", color = t.textMuted) },
                    isError = phoneError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(t)
                )

                // Table selector
                Box {
                    OutlinedButton(
                        onClick = { showTableMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("桌次：${selectedTable?.tableName ?: "（無桌次）"}", color = t.text)
                    }
                    DropdownMenu(expanded = showTableMenu, onDismissRequest = { showTableMenu = false }, containerColor = t.surface) {
                        tables.forEach { tbl ->
                            DropdownMenuItem(
                                text = { Text(tbl.tableName, color = if (selectedTable?.id == tbl.id) t.accent else t.text) },
                                onClick = { selectedTable = tbl; showTableMenu = false }
                            )
                        }
                    }
                }

                // Time row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("開始 $startTime", color = t.text) }
                    OutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, t.border),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("結束 $endTime", color = t.text) }
                }

                // Guest count
                OutlinedTextField(
                    value = guestCount,
                    onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) guestCount = it },
                    label = { Text("人數（選填）", color = t.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(t)
                )

                // Importance
                Text("重要性", color = t.textMuted, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    importanceLabels.forEachIndexed { idx, label ->
                        val selected = importance == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) importanceColors[idx].copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.5.dp, if (selected) importanceColors[idx] else t.border, RoundedCornerShape(8.dp))
                                .clickable { importance = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) importanceColors[idx] else t.textSub, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                // Remark
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("備註（選填）", color = t.textMuted) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(t)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("刪除", color = t.error, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消", color = t.textSub) }
                Button(
                    onClick = {
                        nameError  = guestName.isBlank()
                        phoneError = guestPhone.isBlank()
                        if (nameError || phoneError) return@Button
                        val tbl = selectedTable ?: return@Button
                        onSave(
                            ReservationEntity(
                                id         = initial?.id ?: 0,
                                tableId    = tbl.id,
                                tableName  = tbl.tableName,
                                guestName  = guestName.trim(),
                                guestPhone = guestPhone.trim(),
                                guestCount = guestCount.toIntOrNull() ?: 0,
                                date       = initial?.date ?: dateStr,
                                startTime  = startTime,
                                endTime    = endTime,
                                importance = importance,
                                remark     = remark.trim(),
                                createdAt  = initial?.createdAt ?: System.currentTimeMillis()
                            )
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = t.accent),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (isNew) "確認新增" else "儲存") }
            }
        }
    )
}

private fun addMinutes(time: String, minutes: Int): String {
    val parts = time.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val total = h * 60 + m + minutes
    return "%02d:%02d".format(total / 60 % 24, total % 60)
}

@Composable
private fun fieldColors(t: PosColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = t.accent, unfocusedBorderColor = t.border,
    focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.accent
)
