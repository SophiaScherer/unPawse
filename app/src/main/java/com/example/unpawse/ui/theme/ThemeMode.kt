package com.example.unpawse.ui.theme

/**
 * What the user chose in Settings → Appearance.
 *
 * The persisted form is a nullable `Boolean` override where absent means "follow the system"
 * ([com.example.unpawse.data.settings.SettingsRepository.darkModeOverride]). That third state was
 * unreachable from the UI: a two-position `Switch` could only ever write `true` or `false`, so the
 * first tap left the user permanently opted out of following their device. This type makes all
 * three states nameable, and [overrideFor] is what puts `null` back on the table.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** Reads the persisted override; `null` (absent) means the user hasn't overridden the system. */
fun themeModeFrom(override: Boolean?): ThemeMode = when (override) {
    null -> ThemeMode.SYSTEM
    true -> ThemeMode.DARK
    false -> ThemeMode.LIGHT
}

/** The value to persist for [mode]; `null` clears the override so the system is followed again. */
fun overrideFor(mode: ThemeMode): Boolean? = when (mode) {
    ThemeMode.SYSTEM -> null
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Resolves the mode against the device setting. Pure, taking [systemInDarkTheme] as a parameter
 * rather than calling `isSystemInDarkTheme()`, so the resolution is unit-testable.
 */
fun ThemeMode.isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
