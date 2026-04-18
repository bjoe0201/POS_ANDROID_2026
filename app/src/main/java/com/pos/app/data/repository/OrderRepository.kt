package com.pos.app.data.repository

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
    private val orderItemDao: OrderItemDao
) {
    fun getOpenOrderForTable(tableId: Long): Flow<OrderEntity?> =
        orderDao.getOpenOrderForTable(tableId)

    fun getAllOpenOrders(): Flow<List<OrderEntity>> = orderDao.getAllOpenOrders()

    fun getAllOrders(): Flow<List<OrderEntity>> = orderDao.getAllOrders()

    fun getItemsForOrder(orderId: Long): Flow<List<OrderItemEntity>> =
        orderItemDao.getItemsForOrder(orderId)

    suspend fun createOrder(tableId: Long, tableName: String): Long =
        orderDao.insert(OrderEntity(tableId = tableId, tableName = tableName))

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
    }

    suspend fun removeItem(item: OrderItemEntity) = orderItemDao.delete(item)

    suspend fun payOrder(orderId: Long, remark: String = "") =
        orderDao.closeOrder(orderId, "PAID", System.currentTimeMillis(), remark)

    suspend fun cancelOrder(orderId: Long) =
        orderDao.closeOrder(orderId, "CANCELLED", System.currentTimeMillis(), "")

    suspend fun softDeleteOrder(orderId: Long) = orderDao.softDelete(orderId)

    suspend fun getAllOrderItems(): List<OrderItemEntity> = orderItemDao.getAllOrderItems()
}
