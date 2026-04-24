package com.pos.app.data.repository

import com.pos.app.data.db.dao.ReservationDao
import com.pos.app.data.db.entity.ReservationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReservationRepository @Inject constructor(private val dao: ReservationDao) {
    fun getByDate(date: String): Flow<List<ReservationEntity>> = dao.getByDate(date)
    fun getByMonth(yearMonth: String): Flow<List<ReservationEntity>> = dao.getByMonth(yearMonth)
    suspend fun upsert(reservation: ReservationEntity): Long = dao.upsert(reservation)
    suspend fun delete(reservation: ReservationEntity) = dao.delete(reservation)
}
