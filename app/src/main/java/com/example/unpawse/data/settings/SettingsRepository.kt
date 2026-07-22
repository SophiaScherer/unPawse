package com.example.unpawse.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single Preferences DataStore instance for the process (the delegate enforces one per file). */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists the app's scalar settings via Preferences DataStore. Mirrors the [CaptureRepository]
 * pattern (a single class exposing `Flow`s + suspend writers) so callers never touch DataStore
 * directly. Owned as a singleton by [com.example.unpawse.data.AppContainer].
 *
 * Dark mode is stored as a nullable override: absent means "follow the system", matching the
 * previous session-only behaviour — the UI resolves `null` against `isSystemInDarkTheme()`.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val darkModeOverride: Flow<Boolean?> = dataStore.data.map { it[Keys.DARK_MODE_OVERRIDE] }

    val sensitivity: Flow<Float> = dataStore.data.map { it[Keys.SENSITIVITY] ?: DEFAULT_SENSITIVITY }

    val requireLivePhoto: Flow<Boolean> =
        dataStore.data.map { it[Keys.REQUIRE_LIVE_PHOTO] ?: DEFAULT_REQUIRE_LIVE_PHOTO }

    val dailySummaryEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.DAILY_SUMMARY] ?: DEFAULT_DAILY_SUMMARY }

    /** The user's display name for the Home greeting/avatar; blank until they set one. */
    val userName: Flow<String> = dataStore.data.map { it[Keys.USER_NAME] ?: DEFAULT_USER_NAME }

    suspend fun setDarkModeOverride(enabled: Boolean) =
        edit { it[Keys.DARK_MODE_OVERRIDE] = enabled }

    suspend fun setSensitivity(value: Float) = edit { it[Keys.SENSITIVITY] = value }

    suspend fun setRequireLivePhoto(value: Boolean) = edit { it[Keys.REQUIRE_LIVE_PHOTO] = value }

    suspend fun setDailySummary(value: Boolean) = edit { it[Keys.DAILY_SUMMARY] = value }

    suspend fun setUserName(value: String) = edit { it[Keys.USER_NAME] = value }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(transform)
    }

    private object Keys {
        val DARK_MODE_OVERRIDE = booleanPreferencesKey("dark_mode_override")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val REQUIRE_LIVE_PHOTO = booleanPreferencesKey("require_live_photo")
        val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        val USER_NAME = stringPreferencesKey("user_name")
    }

    companion object {
        /** Defaults match the previous `SettingsUiState.sample()` values so behaviour is unchanged. */
        const val DEFAULT_SENSITIVITY = 0.65f
        const val DEFAULT_REQUIRE_LIVE_PHOTO = false
        const val DEFAULT_DAILY_SUMMARY = false

        /** Blank means "no name set yet"; the UI substitutes a friendly fallback. */
        const val DEFAULT_USER_NAME = ""
    }
}
