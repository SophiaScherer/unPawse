package com.example.unpawse.data.schedule

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Pure schedule arithmetic — the entire "is this app blocked right now?" rule, extracted from any
 * store or service so it is unit-testable without Room or a device (same spirit as `UsageMath`).
 *
 * Time is expressed as minutes since local midnight throughout; the caller supplies the day and the
 * minute, so there is no clock in here to pin.
 */

/** Minutes in a day — the modulus every window position is expressed against. */
const val MINUTES_PER_DAY = 24 * 60

/** Bit for one ISO day, Monday = bit 0 … Sunday = bit 6. */
fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

/** Every day selected. */
const val EVERY_DAY_MASK = 0b111_1111

/** Monday–Friday. */
const val WEEKDAYS_MASK = 0b001_1111

/** Saturday and Sunday. */
const val WEEKENDS_MASK = 0b110_0000

/** Builds a mask from a set of days — the form the UI's day chips produce. */
fun daysMaskOf(days: Iterable<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or dayBit(day) }

/** The days a mask selects, Monday first — the form the UI renders back. */
fun daysIn(mask: Int): List<DayOfWeek> = DayOfWeek.values().filter { mask and dayBit(it) != 0 }

/** Whether [mask] selects [day]. */
fun maskCovers(mask: Int, day: DayOfWeek): Boolean = mask and dayBit(day) != 0

/** Clamps an arbitrary minute count into a single day, wrapping rather than saturating. */
fun wrapMinuteOfDay(minuteOfDay: Int): Int =
    ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

/** Minutes since local midnight for [time]. */
fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

/**
 * Whether [window] covers [day] at [minuteOfDay]. The start is inclusive, the end exclusive.
 *
 * A window whose end is at or below its start wraps past midnight, and that makes the days mask
 * mean something specific: it names the days the window *starts* on. "Bedtime 22:00–07:00, Fri"
 * therefore runs Friday 22:00 to Saturday 07:00 — which is what someone picking "Friday night"
 * means. The tail after midnight is matched by testing the *previous* day against the mask.
 *
 * `start == end` is not empty: it reads as a full 24 hours from `start`, which falls out of the
 * wrapping branch and is the only sensible reading of "from 08:00 until 08:00".
 */
internal fun isActiveAt(window: ScheduleWindow, day: DayOfWeek, minuteOfDay: Int): Boolean {
    if (!window.enabled) return false
    val start = window.startMinuteOfDay
    val end = window.endMinuteOfDay
    return if (start < end) {
        maskCovers(window.daysMask, day) && minuteOfDay >= start && minuteOfDay < end
    } else {
        (maskCovers(window.daysMask, day) && minuteOfDay >= start) ||
            (maskCovers(window.daysMask, day.minus(1)) && minuteOfDay < end)
    }
}

/**
 * The first window blocking [packageName] at this moment, or null if none is. A window with no
 * package applies to every monitored app; the caller has already established that the app *is*
 * monitored, so there is no need to re-check that here.
 */
internal fun activeWindowFor(
    windows: List<ScheduleWindow>,
    packageName: String,
    day: DayOfWeek,
    minuteOfDay: Int,
): ScheduleWindow? = windows.firstOrNull { window ->
    (window.packageName == null || window.packageName == packageName) &&
        isActiveAt(window, day, minuteOfDay)
}
