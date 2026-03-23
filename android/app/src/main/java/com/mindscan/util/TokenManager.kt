package com.mindscan.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mindscan_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY    = stringPreferencesKey("jwt_token")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val EMAIL_KEY    = stringPreferencesKey("email")
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun saveUserInfo(username: String, email: String) {
        context.dataStore.edit {
            it[USERNAME_KEY] = username
            it[EMAIL_KEY]    = email
        }
    }

    fun getToken(): Flow<String?>    = context.dataStore.data.map { it[TOKEN_KEY] }
    fun getUsername(): Flow<String?> = context.dataStore.data.map { it[USERNAME_KEY] }
    fun getEmail(): Flow<String?>    = context.dataStore.data.map { it[EMAIL_KEY] }

    suspend fun clearAll() { context.dataStore.edit { it.clear() } }
}
