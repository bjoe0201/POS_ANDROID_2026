package com.pos.app.data.db.dao

import androidx.room.*
import com.pos.app.data.db.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE status = 'OPEN' AND tableId = :tableId LIMIT 1")
    fun getOpenOrderForTable(tableId: Long): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE status = 'OPEN'")
    fun getAllOpenOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: Long): OrderEntity?

    @Insert
    suspend fun insert(order: OrderEntity): Long

    @Update
    suspend fun update(order: OrderEntity)

    @Query("UPDATE orders SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE orders SET status = :status, closedAt = :closedAt, remark = :remark WHERE id = :id")
    suspend fun closeOrder(id: Long, status: String, closedAt: Long, remark: String)

    @Query("DELETE FROM orders")
    suspend fun deleteAll()
}
