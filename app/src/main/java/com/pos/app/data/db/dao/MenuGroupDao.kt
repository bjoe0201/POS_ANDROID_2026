package com.pos.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pos.app.data.db.entity.MenuGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuGroupDao {
    @Query("SELECT * FROM menu_groups ORDER BY sortOrder, name")
    fun getAllGroups(): Flow<List<MenuGroupEntity>>

    @Query("SELECT * FROM menu_groups WHERE isActive = 1 ORDER BY sortOrder, name")
    fun getActiveGroups(): Flow<List<MenuGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: MenuGroupEntity)

    @Update
    suspend fun update(group: MenuGroupEntity)

    @Delete
    suspend fun delete(group: MenuGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<MenuGroupEntity>)
}

