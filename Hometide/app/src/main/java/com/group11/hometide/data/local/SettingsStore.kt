package com.group11.hometide.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

/** DataStore 包裝（7.3）。 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val isLoggedIn: Flow<Boolean> = store.data.map { it[IS_LOGGED_IN] ?: false }
    val guestMode: Flow<Boolean> = store.data.map { it[GUEST_MODE] ?: false }
    val themeMode: Flow<ThemeMode> = store.data.map { p ->
        p[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    val lastSyncAt: Flow<Long?> = store.data.map { it[LAST_SYNC_AT] }
    val aiCallsWindow: Flow<String> = store.data.map { it[AI_CALLS_WINDOW] ?: "" }
    val lastNextRoute: Flow<Long?> = store.data.map { it[LAST_NEXT_ROUTE] }
    val lastDriveRoute: Flow<Long?> = store.data.map { it[LAST_DRIVE_ROUTE] }

    /** 第一次呼叫時產生匿名 UUID 並保存，之後固定回傳同一值 */
    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        var id = ""
        store.edit { p -> id = p[DEVICE_ID] ?: UUID.randomUUID().toString().also { p[DEVICE_ID] = it } }
        return id
    }

    suspend fun setLoggedIn(value: Boolean) = set(IS_LOGGED_IN, value)
    suspend fun setGuestMode(value: Boolean) = set(GUEST_MODE, value)
    suspend fun setThemeMode(value: ThemeMode) = set(THEME_MODE, value.name)
    suspend fun setLastSyncAt(value: Long) = set(LAST_SYNC_AT, value)
    suspend fun setAiCallsWindow(value: String) = set(AI_CALLS_WINDOW, value)
    suspend fun setLastNextRoute(value: Long) = set(LAST_NEXT_ROUTE, value)
    suspend fun setLastDriveRoute(value: Long) = set(LAST_DRIVE_ROUTE, value)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    private companion object {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val GUEST_MODE = booleanPreferencesKey("guest_mode")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val AI_CALLS_WINDOW = stringPreferencesKey("ai_calls_window")
        val LAST_NEXT_ROUTE = longPreferencesKey("last_next_route")
        val LAST_DRIVE_ROUTE = longPreferencesKey("last_drive_route")
    }
}
