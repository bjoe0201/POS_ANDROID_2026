package com.pos.app.data.repository

import com.pos.app.data.db.AppDatabase
import com.pos.app.data.db.dao.OrderDao
import com.pos.app.data.db.dao.OrderItemDao
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val orderDao: OrderDao,
    private val orderItemDao: OrderItemDao,
    private val appDatabase: AppDatabase
) {
    fun getOpenOrderForTable(tableId: Long): Flow<OrderEntity?> =
        orderDao.getOpenOrderForTable(tableId)

    fun getAllOpenOrders(): Flow<List<OrderEntity>> = orderDao.getAllOpenOrders()

    fun getAllOrders(): Flow<List<OrderEntity>> = orderDao.getAllOrders()

    fun getItemsForOrder(orderId: Long): Flow<List<OrderItemEntity>> =
        orderItemDao.getItemsForOrder(orderId)

    suspend fun createOrder(
        tableId: Long,
        tableName: String,
        createdAt: Long = System.currentTimeMillis()
    ): Long = orderDao.insert(OrderEntity(tableId = tableId, tableName = tableName, createdAt = createdAt))

    suspend fun addOrUpdateItem(
        orderId: Long,
        menuItemId: Long,
        name: String,
        price: Double,
        menuGroupCode: String,
        menuGroupName: String,
        delta: Int
    ) {
        val existing = orderItemDao.findItem(orderId, menuItemId)
        if (existing == null) {
            if (delta > 0) orderItemDao.insert(
                OrderItemEntity(
                    orderId = orderId,
                    menuItemId = menuItemId,
                    name = name,
                    price = price,
                    menuGroupCode = menuGroupCode,
                    menuGroupName = menuGroupName,
                    quantity = delta
                )
            )
        } else {
            val newQty = existing.quantity + delta
            if (newQty <= 0) orderItemDao.delete(existing)
            else orderItemDao.update(existing.copy(quantity = newQty))
        }
        flush()
    }

    suspend fun removeItem(item: OrderItemEntity) {
        orderItemDao.delete(item)
        flush()
    }

    suspend fun payOrder(orderId: Long, remark: String = "") {
        orderDao.closeOrder(orderId, "PAID", System.currentTimeMillis(), remark)
        flush()
    }

    suspend fun cancelOrder(orderId: Long) {
        orderDao.closeOrder(orderId, "CANCELLED", System.currentTimeMillis(), "")
        flush()
    }

    suspend fun softDeleteOrder(orderId: Long) = orderDao.softDelete(orderId)

    suspend fun getAllOrderItems(): List<OrderItemEntity> = orderItemDao.getAllOrderItems()

    /**
     * 強制將尚未落地的交易刷新到 DB 主檔。
     * 雖然目前已採用 TRUNCATE journal 模式 + synchronous=FULL，交易會立即寫入主檔，
     * 但此處仍額外呼叫 checkpoint，作為系統崩潰/斷電的最後一道防線。
     */
    fun flush() {
        runCatching {
            appDatabase.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .close()
        }
    }
}
