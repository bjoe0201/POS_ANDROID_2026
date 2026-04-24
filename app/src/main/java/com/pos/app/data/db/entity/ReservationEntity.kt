package com.pos.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reservations")
data class ReservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val tableName: String,          // 桌次快照
    val guestName: String,
    val guestPhone: String,
    val guestCount: Int = 0,
    val date: String,               // "yyyy-MM-dd"
    val startTime: String,          // "HH:mm"
    val endTime: String,            // "HH:mm"
    val importance: Int = 0,        // 0=一般, 1=重要, 2=非常重要
    val remark: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
