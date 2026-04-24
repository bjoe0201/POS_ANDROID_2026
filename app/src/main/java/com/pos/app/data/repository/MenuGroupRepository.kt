package com.pos.app.data.repository

import com.pos.app.data.db.dao.MenuGroupDao
import com.pos.app.data.db.entity.MenuGroupEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuGroupRepository @Inject constructor(private val dao: MenuGroupDao) {
    fun getAllGroups(): Flow<List<MenuGroupEntity>> = dao.getAllGroups()

    fun getActiveGroups(): Flow<List<MenuGroupEntity>> = dao.getActiveGroups()

    suspend fun insert(group: MenuGroupEntity) = dao.insert(group)

    suspend fun update(group: MenuGroupEntity) = dao.update(group)

    suspend fun delete(group: MenuGroupEntity) = dao.delete(group)
}

