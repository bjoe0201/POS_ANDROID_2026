package com.pos.app.data.repository

import com.pos.app.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: SettingsDataStore) {
    val pinHash: Flow<String> = dataStore.pinHash
    val isDefaultPin: Flow<Boolean> = dataStore.isDefaultPin

    suspend fun setPin(newPin: String) = dataStore.setPin(newPin)
    fun verifyPin(input: String, hash: String) = dataStore.verifyPin(input, hash)
}
