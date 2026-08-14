package com.example.unpawse.ui.format

/** Shown wherever the user hasn't set a name yet; blank is the stored "not set" state. */
const val DEFAULT_DISPLAY_NAME = "friend"

/** The name to show for a possibly-unset [userName]. */
fun displayNameOf(userName: String): String = userName.ifBlank { DEFAULT_DISPLAY_NAME }

/**
 * The avatar letter for [userName]. Shared so every header derives it the same way — Home, Settings,
 * Stats and Gallery each used to do their own thing, and the last two defaulted to a hardcoded 'S'
 * left over from the mockup's "Sophia".
 */
fun avatarInitialFor(userName: String): Char =
    displayNameOf(userName).first().uppercaseChar()

/** The letter for an unset name, for UI-state defaults that have no name to hand yet. */
val DEFAULT_AVATAR_INITIAL: Char = avatarInitialFor("")
