package com.example.unpawse.service

import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.schedule.activeWindowFor
import com.example.unpawse.data.schedule.minuteOfDay
import java.time.LocalDateTime

/**
 * Answers "is this app inside a blocking window right now?" — the schedule counterpart to
 * [FocusSession], and deliberately the same shape: a tiny class holding no state of its own, with
 * injectable lambdas so every rule it applies is unit-testable without Room or a device.
 *
 * [windows] is a snapshot function rather than a Flow because this is read once per enforcement
 * tick, in the same style as `UsageTracker`'s `warningMinutes` lambda: the caller (the AppContainer)
 * owns the subscription, and the gate just asks for the current value. The whole decision lives in
 * `ScheduleMath`; this class only supplies the clock.
 */
class ScheduleGate(
    private val windows: () -> List<ScheduleWindow>,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    /**
     * The window blocking [packageName] at this instant, or null if none is. The caller has already
     * established that the app is monitored and enabled, so a global window applies unconditionally.
     */
    fun activeWindowFor(packageName: String): ScheduleWindow? {
        val moment = now()
        return activeWindowFor(
            windows = windows(),
            packageName = packageName,
            day = moment.dayOfWeek,
            minuteOfDay = minuteOfDay(moment.toLocalTime()),
        )
    }

    /** Whether any window is blocking [packageName] right now. */
    fun isBlocked(packageName: String): Boolean = activeWindowFor(packageName) != null
}
