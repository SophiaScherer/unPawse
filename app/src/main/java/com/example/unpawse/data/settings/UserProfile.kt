package com.example.unpawse.data.settings

/**
 * The two answers that identify the user in every header. Read as one so a screen already at the
 * five-flow `combine` limit can show the avatar without giving up another slot.
 */
data class UserProfile(
    val name: String = SettingsRepository.DEFAULT_USER_NAME,
    val avatarId: Int = AVATAR_NONE,
)
