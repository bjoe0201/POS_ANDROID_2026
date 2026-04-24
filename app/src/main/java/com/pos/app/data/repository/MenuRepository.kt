package com.pos.app.data.repository

import com.pos.app.data.db.dao.MenuItemDao
import com.pos.app.data.db.entity.MenuItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuRepository @Inject constructor(private val dao: MenuItemDao) {

    fun getAllItems(): Flow<List<MenuItemEntity>> = dao.getAllItems()

    fun getItemsByCategory(category: String): Flow<List<MenuItemEntity>> =
        dao.getItemsByCategory(category)

    suspend fun insert(item: MenuItemEntity): Long = dao.insert(item)

    suspend fun update(item: MenuItemEntity) = dao.update(item)

    suspend fun delete(item: MenuItemEntity) = dao.delete(item)

    suspend fun setAvailability(id: Long, available: Boolean) =
        dao.setAvailability(id, available)

    suspend fun replaceAll(items: List<MenuItemEntity>) {
        dao.deleteAll()
        dao.insertAll(items)
    }
}
