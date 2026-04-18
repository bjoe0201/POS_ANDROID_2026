package com.pos.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    suspend fun setPin(newPin: String) {
        context.dataStore.edit { prefs ->
            prefs[PIN_HASH] = hashPin(newPin)
            prefs[IS_DEFAULT_PIN] = (newPin == DEFAULT_PIN)
        }
    }

    fun verifyPin(inputPin: String, storedHash: String): Boolean = hashPin(inputPin) == storedHash
}
