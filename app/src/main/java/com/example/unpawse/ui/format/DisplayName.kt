package com.example.unpawse.ui.format

import java.text.BreakIterator

/** Shown wherever the user hasn't set a name yet; blank is the stored "not set" state. */
const val DEFAULT_DISPLAY_NAME = "friend"

/** Long enough for any real first name, short enough that the Home greeting stays on one line. */
const val MAX_DISPLAY_NAME_LENGTH = 30

/**
 * What a name field keeps of [input]; both name fields go through this so they agree. Counted in
 * user-perceived characters, so the cap never leaves half an emoji or a bare accent at the end.
 */
fun capDisplayName(input: String): String {
    val characters = BreakIterator.getCharacterInstance().apply { setText(input) }
    var end = 0
    repeat(MAX_DISPLAY_NAME_LENGTH) {
        val next = characters.next()
        if (next == BreakIterator.DONE) return input
        end = next
    }
    return input.substring(0, end)
}

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
