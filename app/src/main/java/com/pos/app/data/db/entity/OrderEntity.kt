package com.pos.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val tableName: String,   // snapshot so records stay readable after table rename/delete
    val remark: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: String = "OPEN",  // OPEN, PAID, CANCELLED
    val isDeleted: Boolean = false  // soft delete flag
)
