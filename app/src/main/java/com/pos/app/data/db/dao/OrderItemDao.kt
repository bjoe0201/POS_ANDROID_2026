package com.pos.app.data.db.dao

import androidx.room.*
import com.pos.app.data.db.entity.OrderItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderItemDao {
    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    fun getItemsForOrder(orderId: Long): Flow<List<OrderItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OrderItemEntity): Long

    @Update
    suspend fun update(item: OrderItemEntity)

    @Delete
    suspend fun delete(item: OrderItemEntity)

    @Query("DELETE FROM order_items WHERE orderId = :orderId AND menuItemId = :menuItemId")
    suspend fun deleteByMenuItemId(orderId: Long, menuItemId: Long)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId AND menuItemId = :menuItemId LIMIT 1")
    suspend fun findItem(orderId: Long, menuItemId: Long): OrderItemEntity?

    @Query("SELECT * FROM order_items WHERE orderId IN (SELECT id FROM orders)")
    suspend fun getAllOrderItems(): List<OrderItemEntity>
}
