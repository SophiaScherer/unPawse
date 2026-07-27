package com.example.unpawse.service

import com.example.unpawse.ui.format.formatMinutes

/** A reminder interval of zero means the user turned reminders off. */
const val REMINDER_OFF = 0

/** Notification heading, e.g. "Instagram check-in". */
fun reminderTitle(appLabel: String): String = "$appLabel check-in"

/**
 * Reminder body, e.g. "45m today · 15m left". Reports what has been spent and what is left rather
 * than telling the user off — the block already handles the hard stop.
 *
 * Pure, so the copy is unit-tested. [remainingMinutes] is null when the app stopped being monitored
 * between the timer starting and firing.
 */
fun reminderText(usedMinutes: Int, remainingMinutes: Int?): String {
    val used = "${formatMinutes(usedMinutes)} today"
    val left = when {
        remainingMinutes == null -> null
        remainingMinutes <= 0 -> "under a minute left"
        else -> "${formatMinutes(remainingMinutes)} left"
    }
    return listOfNotNull(used, left).joinToString(" · ")
}
