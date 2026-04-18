package com.pos.app.data.db.dao

import androidx.room.*
import com.pos.app.data.db.entity.MenuItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items ORDER BY category, sortOrder, name")
    fun getAllItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE category = :category AND isAvailable = 1 ORDER BY sortOrder, name")
    fun getItemsByCategory(category: String): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getItemById(id: Long): MenuItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MenuItemEntity): Long

    @Update
    suspend fun update(item: MenuItemEntity)

    @Delete
    suspend fun delete(item: MenuItemEntity)

    @Query("UPDATE menu_items SET isAvailable = :available WHERE id = :id")
    suspend fun setAvailability(id: Long, available: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MenuItemEntity>)

    @Query("DELETE FROM menu_items")
    suspend fun deleteAll()
}
