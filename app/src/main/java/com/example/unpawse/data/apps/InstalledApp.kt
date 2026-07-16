package com.example.unpawse.data.apps

/**
 * A launchable app installed on the device, as offered in the app picker. Deliberately carries no
 * icon: icons are `Drawable`s (a UI concern) and are loaded lazily per visible row instead of
 * eagerly for every installed app — see `ui/apppicker/AppIcon.kt`.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
)
