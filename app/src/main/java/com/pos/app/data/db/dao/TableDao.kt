package com.pos.app.data.db.dao

import androidx.room.*
import com.pos.app.data.db.entity.TableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TableDao {
    @Query("SELECT * FROM tables ORDER BY sortOrder, tableName")
    fun getAllTables(): Flow<List<TableEntity>>

    @Query("SELECT * FROM tables WHERE isActive = 1 ORDER BY sortOrder, tableName")
    fun getActiveTables(): Flow<List<TableEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(table: TableEntity): Long

    @Update
    suspend fun update(table: TableEntity)

    @Delete
    suspend fun delete(table: TableEntity)

    @Query("UPDATE tables SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tables: List<TableEntity>)

    @Query("SELECT COUNT(*) FROM tables")
    suspend fun count(): Int

    @Query("DELETE FROM tables")
    suspend fun deleteAll()
}
