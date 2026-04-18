package com.pos.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_groups")
data class MenuGroupEntity(
    @PrimaryKey val code: String,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

