package com.example.unpawse.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.unpawse.data.capture.CaptureRetention
import com.example.unpawse.data.export.ExportSettings
import com.example.unpawse.service.BONUS_MINUTES_PER_CAT
import com.example.unpawse.service.REMINDER_OFF
import com.example.unpawse.service.UsageTracker
import com.example.unpawse.ui.theme.overrideFor
import com.example.unpawse.ui.theme.themeModeNamed
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

    val dailySummaryEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.DAILY_SUMMARY] ?: DEFAULT_DAILY_SUMMARY }

    /** How much time one verified cat buys back; the reward loop reads this instead of a constant. */
    val earnedMinutesPerCat: Flow<Int> =
        dataStore.data.map { it[Keys.EARNED_MINUTES_PER_CAT] ?: DEFAULT_EARNED_MINUTES_PER_CAT }

    /**
     * Days a non-favorite photo is kept before the purge worker removes it;
     * [CaptureRetention.KEEP_FOREVER] disables the purge.
     */
    val retentionDays: Flow<Int> =
        dataStore.data.map { it[Keys.RETENTION_DAYS] ?: CaptureRetention.DEFAULT_WINDOW_DAYS }

    /**
     * Minutes of remaining budget at which to warn before an app is blocked;
     * [UsageTracker.WARNING_OFF] disables the warning.
     */
    val warningMinutes: Flow<Int> =
        dataStore.data.map { it[Keys.WARNING_MINUTES] ?: DEFAULT_WARNING_MINUTES }

    /**
     * How often to nudge the user while they sit in a limited app; [REMINDER_OFF] disables it.
     * Defaults to off — an unasked-for recurring notification is the kind of thing that gets an app
     * uninstalled.
     */
    val reminderMinutes: Flow<Int> =
        dataStore.data.map { it[Keys.REMINDER_MINUTES] ?: DEFAULT_REMINDER_MINUTES }

    /** The user's display name for the Home greeting/avatar; blank until they set one. */
    val userName: Flow<String> = dataStore.data.map { it[Keys.USER_NAME] ?: DEFAULT_USER_NAME }

    /**
     * Epoch-millis end time of the active focus session, or null when none is running. Persisted so
     * a focus session survives process death (the enforcement service is restored from it on start).
     */
    val focusEndMillis: Flow<Long?> = dataStore.data.map { it[Keys.FOCUS_END_MILLIS] }

    /**
     * Persists (or, with null, clears) the dark-mode override. Clearing is what returns the app to
     * following the system — the previous non-null-only signature made that a one-way door.
     */
    suspend fun setDarkModeOverride(enabled: Boolean?) = edit {
        if (enabled == null) it.remove(Keys.DARK_MODE_OVERRIDE) else it[Keys.DARK_MODE_OVERRIDE] = enabled
    }

    suspend fun setSensitivity(value: Float) = edit { it[Keys.SENSITIVITY] = value }

    suspend fun setDailySummary(value: Boolean) = edit { it[Keys.DAILY_SUMMARY] = value }

    suspend fun setEarnedMinutesPerCat(value: Int) = edit { it[Keys.EARNED_MINUTES_PER_CAT] = value }

    suspend fun setRetentionDays(value: Int) = edit { it[Keys.RETENTION_DAYS] = value }

    suspend fun setWarningMinutes(value: Int) = edit { it[Keys.WARNING_MINUTES] = value }

    suspend fun setReminderMinutes(value: Int) = edit { it[Keys.REMINDER_MINUTES] = value }

    suspend fun setUserName(value: String) = edit { it[Keys.USER_NAME] = value }

    /** Persists (or, with null, clears) the active focus session's end time. */
    suspend fun setFocusEndMillis(value: Long?) = edit {
        if (value == null) it.remove(Keys.FOCUS_END_MILLIS) else it[Keys.FOCUS_END_MILLIS] = value
    }

    /**
     * Drops every stored preference, returning the app to first-launch defaults. Used only by the
     * full reset in Settings.
     */
    suspend fun clearAll() = edit { it.clear() }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(transform)
    }

    /**
     * Writes an imported settings block. `minConfidence` is derived from [sensitivity] and has no
     * key of its own; `focusEndMillis` is deliberately not restored — it's a live session, and a
     * stale end time would resurrect a focus block the user never started.
     */
    suspend fun applyImported(settings: ExportSettings) {
        setUserName(settings.userName)
        setDarkModeOverride(overrideFor(themeModeNamed(settings.themeMode)))
        setSensitivity(settings.sensitivity)
        setEarnedMinutesPerCat(settings.earnedMinutesPerCat)
        setRetentionDays(settings.retentionDays)
        setDailySummary(settings.dailySummaryEnabled)
        setWarningMinutes(settings.warningMinutes)
        setReminderMinutes(settings.reminderMinutes)
    }

    private object Keys {
        val DARK_MODE_OVERRIDE = booleanPreferencesKey("dark_mode_override")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        val EARNED_MINUTES_PER_CAT = intPreferencesKey("earned_minutes_per_cat")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val WARNING_MINUTES = intPreferencesKey("warning_minutes")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val USER_NAME = stringPreferencesKey("user_name")
        val FOCUS_END_MILLIS = longPreferencesKey("focus_end_millis")
    }

    companion object {
        /** Defaults match the previous `SettingsUiState.sample()` values so behaviour is unchanged. */
        const val DEFAULT_SENSITIVITY = 0.65f
        const val DEFAULT_DAILY_SUMMARY = false

        /**
         * Aliased to the reward loop's own constant rather than repeated, so the shipped default and
         * the value the loop documents can't drift apart.
         */
        const val DEFAULT_EARNED_MINUTES_PER_CAT = BONUS_MINUTES_PER_CAT

        /** Bounds for the Settings stepper, in minutes. */
        const val MIN_EARNED_MINUTES_PER_CAT = 5
        const val MAX_EARNED_MINUTES_PER_CAT = 60
        const val EARNED_MINUTES_STEP = 5

        /** Warn five minutes out by default — enough time to finish what you're doing. */
        const val DEFAULT_WARNING_MINUTES = 5

        /** The choices offered in Settings; [UsageTracker.WARNING_OFF] first. */
        val WARNING_MINUTE_CHOICES = listOf(UsageTracker.WARNING_OFF, 1, 5, 10, 15)

        /** Off unless asked for; see [reminderMinutes]. */
        const val DEFAULT_REMINDER_MINUTES = REMINDER_OFF

        val REMINDER_MINUTE_CHOICES = listOf(REMINDER_OFF, 15, 30, 60)

        /** Blank means "no name set yet"; the UI substitutes a friendly fallback. */
        const val DEFAULT_USER_NAME = ""
    }
}
