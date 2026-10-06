package com.example.unpawse.data.settings

/**
 * The profile picture is persisted as a plain id, never a bitmap. DataStore holds scalars, and a
 * cat drawn in Compose costs one `Int` instead of a file that would then have to be backed up,
 * exported, migrated and garbage-collected.
 *
 * The id space is deliberately open-ended, because a later branch adds a custom cat builder:
 *  - [AVATAR_NONE] means "not chosen" — every avatar surface falls back to the initials avatar.
 *  - The presets in [CatAvatar] occupy 1 upwards.
 *  - A custom-built cat takes ids from [FIRST_CUSTOM_AVATAR_ID] up.
 *
 * [catAvatarForId] answers null for anything it doesn't recognize, so a build that predates an id
 * (an older APK reading a newer DataStore, or a preset retired later) shows initials rather than
 * crashing or silently picking the wrong cat.
 */
const val AVATAR_NONE = 0

/** First id reserved for a user-built cat; presets never reach this far. */
const val FIRST_CUSTOM_AVATAR_ID = 1_000

/** The preset cats offered during onboarding. Ids are persisted, so they must never be reused. */
enum class CatAvatar(val id: Int, val label: String) {
    CREAM(1, "Cream"),
    GINGER(2, "Ginger"),
    TABBY(3, "Tabby"),
    TUXEDO(4, "Tuxedo"),
    SIAMESE(5, "Siamese"),
    CALICO(6, "Calico"),
    SMOKE(7, "Smoke"),
    MIDNIGHT(8, "Midnight"),
}

/** The preset for a stored id, or null for "none", a custom cat, or an id this build can't draw. */
fun catAvatarForId(id: Int): CatAvatar? = CatAvatar.entries.firstOrNull { it.id == id }

/** Whether [id] belongs to the range a user-built cat will claim. */
fun isCustomAvatarId(id: Int): Boolean = id >= FIRST_CUSTOM_AVATAR_ID
