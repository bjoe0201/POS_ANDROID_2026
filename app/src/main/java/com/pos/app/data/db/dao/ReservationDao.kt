package com.pos.app.data.db.dao

import androidx.room.*
import com.pos.app.data.db.entity.ReservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReservationDao {
    @Query("SELECT * FROM reservations WHERE date = :date ORDER BY startTime ASC")
    fun getByDate(date: String): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE date LIKE :yearMonth || '%' ORDER BY date ASC, startTime ASC")
    fun getByMonth(yearMonth: String): Flow<List<ReservationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reservation: ReservationEntity): Long

    @Delete
    suspend fun delete(reservation: ReservationEntity)

    @Query("DELETE FROM reservations")
    suspend fun deleteAll()
}
