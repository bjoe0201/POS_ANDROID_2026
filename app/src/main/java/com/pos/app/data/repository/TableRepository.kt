package com.pos.app.data.repository

import com.pos.app.data.db.dao.TableDao
import com.pos.app.data.db.entity.TableEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TableRepository @Inject constructor(private val dao: TableDao) {
    fun getAllTables(): Flow<List<TableEntity>> = dao.getAllTables()
    fun getActiveTables(): Flow<List<TableEntity>> = dao.getActiveTables()
    suspend fun insert(table: TableEntity): Long = dao.insert(table)
    suspend fun update(table: TableEntity) = dao.update(table)
    suspend fun delete(table: TableEntity) = dao.delete(table)
    suspend fun setActive(id: Long, active: Boolean) = dao.setActive(id, active)
    suspend fun count(): Int = dao.count()
}
