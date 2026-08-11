package com.example.unpawse.ui.format

import com.example.unpawse.data.schedule.MINUTES_PER_DAY
import com.example.unpawse.data.schedule.wrapMinuteOfDay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Wall-clock copy for schedules: "10:00 PM" or "22:00" depending on the reader's locale. Sits beside
 * [formatMinutes], which covers durations — a schedule needs both ("blocked until 7:00 AM", "45m
 * left today") and they must not be confused for one another.
 *
 * Pure, with [locale] injectable so the formatting is unit-tested without depending on whatever
 * locale the test machine happens to run in.
 */
fun formatMinuteOfDay(minuteOfDay: Int, locale: Locale = Locale.getDefault()): String {
    val wrapped = wrapMinuteOfDay(minuteOfDay)
    val time = LocalTime.of(wrapped / 60, wrapped % 60)
    return time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        .normalizeSpaces()
}

/** A window's span, e.g. "10:00 PM – 7:00 AM". */
fun formatTimeRange(
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    locale: Locale = Locale.getDefault(),
): String = "${formatMinuteOfDay(startMinuteOfDay, locale)} – ${formatMinuteOfDay(endMinuteOfDay, locale)}"

/**
 * How long a window still has to run, from [fromMinuteOfDay] to [endMinuteOfDay], wrapping past
 * midnight. Always at least one minute: a window that ends this very minute is still blocking.
 */
fun minutesUntil(fromMinuteOfDay: Int, endMinuteOfDay: Int): Int {
    val delta = wrapMinuteOfDay(endMinuteOfDay - fromMinuteOfDay)
    return if (delta == 0) MINUTES_PER_DAY else delta
}

/**
 * CLDR separates the time from AM/PM with a narrow no-break space, and some locales use a regular
 * no-break space elsewhere. Both are invisible in source and in a diff, and these strings get
 * interpolated into overlay copy and compared in tests — so they're folded to a plain space rather
 * than left to surprise the next reader.
 */
private fun String.normalizeSpaces(): String =
    replace(NARROW_NO_BREAK_SPACE, ' ').replace(NO_BREAK_SPACE, ' ')

private const val NARROW_NO_BREAK_SPACE = '\u202F'
private const val NO_BREAK_SPACE = '\u00A0'
