package com.pos.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pos_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val IS_DEFAULT_PIN = booleanPreferencesKey("is_default_pin")
        private val TAB_MENU_ENABLED        = booleanPreferencesKey("tab_menu_enabled")
        private val TAB_TABLE_ENABLED       = booleanPreferencesKey("tab_table_enabled")
        private val TAB_REPORT_ENABLED      = booleanPreferencesKey("tab_report_enabled")
        private val TAB_RESERVATION_ENABLED = booleanPreferencesKey("tab_reservation_enabled")
        private val BIZ_START       = stringPreferencesKey("biz_start")
        private val BIZ_END         = stringPreferencesKey("biz_end")
        private val BREAK_START     = stringPreferencesKey("break_start")
        private val BREAK_END       = stringPreferencesKey("break_end")
        private val DEFAULT_DURATION = intPreferencesKey("default_duration")
        private val CALENDAR_CHIPS_PER_ROW = intPreferencesKey("calendar_chips_per_row")
        private val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val AUTO_BACKUP_IDLE_MINUTES = intPreferencesKey("auto_backup_idle_minutes")
        private val AUTO_BACKUP_RETENTION_DAYS = intPreferencesKey("auto_backup_retention_days")
        private val AUTO_BACKUP_EXTERNAL_TREE_URI = stringPreferencesKey("auto_backup_external_tree_uri")
        private val QTY_REPEAT_INTERVAL_MS = intPreferencesKey("qty_repeat_interval_ms")
        private val QTY_REPEAT_INITIAL_DELAY_MS = intPreferencesKey("qty_repeat_initial_delay_ms")
        private val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private const val DEFAULT_PIN = "1234"

        fun hashPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    val pinHash: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PIN_HASH] ?: hashPin(DEFAULT_PIN)
    }

    val isDefaultPin: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_DEFAULT_PIN] ?: true
    }

    val tabMenuEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TAB_MENU_ENABLED] ?: true
    }

    val tabTableEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TAB_TABLE_ENABLED] ?: true
    }

    val tabReportEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TAB_REPORT_ENABLED] ?: true
    }

    suspend fun setPin(newPin: String) {
        context.dataStore.edit { prefs ->
            prefs[PIN_HASH] = hashPin(newPin)
            prefs[IS_DEFAULT_PIN] = (newPin == DEFAULT_PIN)
        }
    }

    suspend fun setTabMenuEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[TAB_MENU_ENABLED] = enabled }
    }

    suspend fun setTabTableEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[TAB_TABLE_ENABLED] = enabled }
    }

    suspend fun setTabReportEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[TAB_REPORT_ENABLED] = enabled }
    }

    val tabReservationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TAB_RESERVATION_ENABLED] ?: true
    }

    suspend fun setTabReservationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[TAB_RESERVATION_ENABLED] = enabled }
    }

    val bizStart: Flow<String>     = context.dataStore.data.map { prefs -> prefs[BIZ_START]  ?: "11:00" }
    val bizEnd: Flow<String>       = context.dataStore.data.map { prefs -> prefs[BIZ_END]    ?: "22:00" }
    val breakStart: Flow<String>   = context.dataStore.data.map { prefs -> prefs[BREAK_START] ?: "" }
    val breakEnd: Flow<String>     = context.dataStore.data.map { prefs -> prefs[BREAK_END]   ?: "" }
    val defaultDuration: Flow<Int> = context.dataStore.data.map { prefs -> prefs[DEFAULT_DURATION] ?: 90 }
    val calendarChipsPerRow: Flow<Int> = context.dataStore.data.map { prefs -> prefs[CALENDAR_CHIPS_PER_ROW] ?: 2 }

    suspend fun setBizStart(v: String)   { context.dataStore.edit { it[BIZ_START]  = v } }
    suspend fun setBizEnd(v: String)     { context.dataStore.edit { it[BIZ_END]    = v } }
    suspend fun setBreakStart(v: String) { context.dataStore.edit { it[BREAK_START] = v } }
    suspend fun setBreakEnd(v: String)   { context.dataStore.edit { it[BREAK_END]   = v } }
    suspend fun setDefaultDuration(v: Int) { context.dataStore.edit { it[DEFAULT_DURATION] = v } }
    suspend fun setCalendarChipsPerRow(v: Int) { context.dataStore.edit { it[CALENDAR_CHIPS_PER_ROW] = v } }

    // ── 自動儲存（閒置備份）──
    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_BACKUP_ENABLED] ?: true }
    val autoBackupIdleMinutes: Flow<Int> = context.dataStore.data.map { it[AUTO_BACKUP_IDLE_MINUTES] ?: 5 }
    val autoBackupRetentionDays: Flow<Int> = context.dataStore.data.map { it[AUTO_BACKUP_RETENTION_DAYS] ?: 3 }
    /** 使用者指定的外部備份資料夾（SAF Tree URI），為空代表使用預設「下載／火鍋店POS備份」。 */
    val autoBackupExternalTreeUri: Flow<String> = context.dataStore.data.map { it[AUTO_BACKUP_EXTERNAL_TREE_URI] ?: "" }

    suspend fun setAutoBackupEnabled(v: Boolean) { context.dataStore.edit { it[AUTO_BACKUP_ENABLED] = v } }
    suspend fun setAutoBackupIdleMinutes(v: Int) { context.dataStore.edit { it[AUTO_BACKUP_IDLE_MINUTES] = v } }
    suspend fun setAutoBackupRetentionDays(v: Int) { context.dataStore.edit { it[AUTO_BACKUP_RETENTION_DAYS] = v } }
    suspend fun setAutoBackupExternalTreeUri(v: String) { context.dataStore.edit { it[AUTO_BACKUP_EXTERNAL_TREE_URI] = v } }

    // ── 點餐長按連續加減 ──
    val qtyRepeatIntervalMs: Flow<Int> = context.dataStore.data.map { it[QTY_REPEAT_INTERVAL_MS] ?: 100 }
    val qtyRepeatInitialDelayMs: Flow<Int> = context.dataStore.data.map { it[QTY_REPEAT_INITIAL_DELAY_MS] ?: 1000 }

    suspend fun setQtyRepeatIntervalMs(v: Int) { context.dataStore.edit { it[QTY_REPEAT_INTERVAL_MS] = v } }
    suspend fun setQtyRepeatInitialDelayMs(v: Int) { context.dataStore.edit { it[QTY_REPEAT_INITIAL_DELAY_MS] = v } }

    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { it[HAPTIC_ENABLED] ?: true }
    suspend fun setHapticEnabled(v: Boolean) { context.dataStore.edit { it[HAPTIC_ENABLED] = v } }

    fun verifyPin(inputPin: String, storedHash: String): Boolean = hashPin(inputPin) == storedHash
}
