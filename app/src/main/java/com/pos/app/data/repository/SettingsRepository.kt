package com.pos.app.data.repository

import com.pos.app.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: SettingsDataStore) {
    val pinHash: Flow<String> = dataStore.pinHash
    val isDefaultPin: Flow<Boolean> = dataStore.isDefaultPin
    val tabMenuEnabled: Flow<Boolean> = dataStore.tabMenuEnabled
    val tabTableEnabled: Flow<Boolean> = dataStore.tabTableEnabled
    val tabReportEnabled: Flow<Boolean> = dataStore.tabReportEnabled
    val tabReservationEnabled: Flow<Boolean> = dataStore.tabReservationEnabled
    val bizStart: Flow<String>     = dataStore.bizStart
    val bizEnd: Flow<String>       = dataStore.bizEnd
    val breakStart: Flow<String>   = dataStore.breakStart
    val breakEnd: Flow<String>     = dataStore.breakEnd
    val defaultDuration: Flow<Int> = dataStore.defaultDuration
    val calendarChipsPerRow: Flow<Int> = dataStore.calendarChipsPerRow
    val autoBackupEnabled: Flow<Boolean> = dataStore.autoBackupEnabled
    val autoBackupIdleMinutes: Flow<Int> = dataStore.autoBackupIdleMinutes
    val autoBackupRetentionDays: Flow<Int> = dataStore.autoBackupRetentionDays
    val autoBackupExternalTreeUri: Flow<String> = dataStore.autoBackupExternalTreeUri
    val qtyRepeatIntervalMs: Flow<Int> = dataStore.qtyRepeatIntervalMs
    val qtyRepeatInitialDelayMs: Flow<Int> = dataStore.qtyRepeatInitialDelayMs
    val hapticEnabled: Flow<Boolean> = dataStore.hapticEnabled

    suspend fun setPin(newPin: String) = dataStore.setPin(newPin)
    fun verifyPin(input: String, hash: String) = dataStore.verifyPin(input, hash)
    suspend fun setTabMenuEnabled(enabled: Boolean) = dataStore.setTabMenuEnabled(enabled)
    suspend fun setTabTableEnabled(enabled: Boolean) = dataStore.setTabTableEnabled(enabled)
    suspend fun setTabReportEnabled(enabled: Boolean) = dataStore.setTabReportEnabled(enabled)
    suspend fun setTabReservationEnabled(enabled: Boolean) = dataStore.setTabReservationEnabled(enabled)
    suspend fun setBizStart(v: String)     = dataStore.setBizStart(v)
    suspend fun setBizEnd(v: String)       = dataStore.setBizEnd(v)
    suspend fun setBreakStart(v: String)   = dataStore.setBreakStart(v)
    suspend fun setBreakEnd(v: String)     = dataStore.setBreakEnd(v)
    suspend fun setDefaultDuration(v: Int) = dataStore.setDefaultDuration(v)
    suspend fun setCalendarChipsPerRow(v: Int) = dataStore.setCalendarChipsPerRow(v)
    suspend fun setAutoBackupEnabled(v: Boolean) = dataStore.setAutoBackupEnabled(v)
    suspend fun setAutoBackupIdleMinutes(v: Int) = dataStore.setAutoBackupIdleMinutes(v)
    suspend fun setAutoBackupRetentionDays(v: Int) = dataStore.setAutoBackupRetentionDays(v)
    suspend fun setAutoBackupExternalTreeUri(v: String) = dataStore.setAutoBackupExternalTreeUri(v)
    suspend fun setQtyRepeatIntervalMs(v: Int) = dataStore.setQtyRepeatIntervalMs(v.coerceIn(30, 500))
    suspend fun setQtyRepeatInitialDelayMs(v: Int) = dataStore.setQtyRepeatInitialDelayMs(v.coerceIn(300, 2000))
    suspend fun setHapticEnabled(v: Boolean) = dataStore.setHapticEnabled(v)
}
