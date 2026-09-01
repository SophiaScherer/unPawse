package com.example.unpawse.data.usage

/**
 * Which apps a screen-time figure counts.
 *
 * unPawse's own `daily_usage` table only ever holds monitored apps — `UsageTracker` gates every
 * write on `isMonitoredAndEnabled` — so a figure built from it answers "how long did you spend in
 * the apps you chose to limit?", not "how long were you on your phone?". Both are useful questions
 * and they are not the same one, so the user picks which the Stats screen is answering and the card
 * says which on its face.
 *
 * [ALL] is served by the platform's own figures via
 * [com.example.unpawse.data.apps.DeviceUsageProvider], not by `daily_usage`.
 *
 * Stored as [name] rather than an ordinal, for the same reason [AppCategory] is: the column survives
 * a reorder of this enum and reads correctly in a `sqlite3` dump.
 */
enum class UsageScope(val label: String) {
    TRACKED("Tracked apps"),
    ALL("All apps"),
}

/**
 * Reads a stored value back. Anything unrecognised — an absent key, or a name from a build with more
 * scopes — reads as [UsageScope.TRACKED] rather than throwing, so a downgrade can't crash the app.
 * Tracked is also the safer fallback: it is what the app measured itself, with no permission behind
 * it beyond the one enforcement already needs.
 */
fun usageScopeNamed(stored: String?): UsageScope =
    UsageScope.entries.firstOrNull { it.name == stored } ?: UsageScope.TRACKED
