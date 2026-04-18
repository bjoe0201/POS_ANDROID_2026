package com.pos.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tables")
data class TableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val seats: Int? = null,
    val remark: String? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)
