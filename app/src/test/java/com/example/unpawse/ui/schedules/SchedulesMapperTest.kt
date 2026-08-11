package com.example.unpawse.ui.schedules

import com.example.unpawse.data.schedule.EVERY_DAY_MASK
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.schedule.WEEKDAYS_MASK
import com.example.unpawse.data.schedule.WEEKENDS_MASK
import com.example.unpawse.data.schedule.dayBit
import com.example.unpawse.data.usage.MonitoredApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.util.Locale

/** Pure shaping for the Schedules screen. Locale is pinned so this doesn't depend on the machine. */
class SchedulesMapperTest {

    private val us = Locale.US

    private fun window(
        id: Long = 1,
        label: String = "Bedtime",
        packageName: String? = null,
        start: Int = 22 * 60,
        end: Int = 7 * 60,
        days: Int = EVERY_DAY_MASK,
        enabled: Boolean = true,
    ) = ScheduleWindow(id, label, packageName, start, end, days, enabled)

    private val apps = listOf(
        MonitoredApp("com.instagram.android", "Instagram", 30, enabled = true),
        MonitoredApp("com.zhiliaoapp.musically", "TikTok", 45, enabled = true),
        MonitoredApp("com.spotify.music", "Spotify", 60, enabled = false),
    )

    // --- Day labels -----------------------------------------------------------------------------

    @Test
    fun `the common masks get their own names`() {
        assertEquals("Every day", daysLabel(EVERY_DAY_MASK, us))
        assertEquals("Weekdays", daysLabel(WEEKDAYS_MASK, us))
        assertEquals("Weekends", daysLabel(WEEKENDS_MASK, us))
    }

    @Test
    fun `a few days are spelled out in week order`() {
        val mask = dayBit(FRIDAY) or dayBit(MONDAY) or dayBit(WEDNESDAY)

        assertEquals("Mon, Wed, Fri", daysLabel(mask, us))
    }

    @Test
    fun `more than three days collapses to a count`() {
        val mask = dayBit(MONDAY) or dayBit(WEDNESDAY) or dayBit(THURSDAY) or dayBit(FRIDAY)

        assertEquals("4 days a week", daysLabel(mask, us))
    }

    @Test
    fun `an empty mask reads as never`() {
        assertEquals("Never", daysLabel(0, us))
    }

    // --- Scope ----------------------------------------------------------------------------------

    @Test
    fun `a global window names every app`() {
        assertEquals(ALL_APPS_LABEL, scopeLabel(null, apps))
    }

    @Test
    fun `a scoped window uses the app's display name`() {
        assertEquals("Instagram", scopeLabel("com.instagram.android", apps))
    }

    @Test
    fun `a window pointing at an app that is no longer monitored falls back to the package`() {
        // Claiming it applies to "all apps" would be worse than showing the raw package name.
        assertEquals("com.deleted.app", scopeLabel("com.deleted.app", apps))
    }

    @Test
    fun `the scope picker offers the global option first, then enabled apps only`() {
        val options = appOptions(apps)

        assertEquals(
            listOf(ALL_APPS_LABEL, "Instagram", "TikTok"),
            options.map { it.label },
        )
        assertEquals(null, options.first().packageName)
    }

    // --- Items ----------------------------------------------------------------------------------

    @Test
    fun `an item carries the formatted range and its origin draft`() {
        val item = toScheduleItem(window(start = 21 * 60 + 30, end = 6 * 60), apps, us)

        assertEquals("9:30 PM – 6:00 AM", item.timeRange)
        assertEquals("Every day", item.daysLabel)
        assertEquals(ALL_APPS_LABEL, item.scopeLabel)
        assertEquals("Bedtime", item.label)
        assertEquals(1L, item.draft.id)
    }

    @Test
    fun `a window whose end is before its start is flagged as overnight`() {
        assertTrue(toScheduleItem(window(start = 22 * 60, end = 7 * 60), apps, us).isOvernight)
        assertFalse(toScheduleItem(window(start = 9 * 60, end = 15 * 60), apps, us).isOvernight)
    }

    @Test
    fun `a 24-hour window counts as overnight because it crosses midnight`() {
        assertTrue(toScheduleItem(window(start = 8 * 60, end = 8 * 60), apps, us).isOvernight)
    }

    @Test
    fun `the ui state keeps the order it was given and stops loading`() {
        val state = toSchedulesUiState(
            windows = listOf(window(id = 1, label = "Morning"), window(id = 2, label = "Evening")),
            monitoredApps = apps,
            locale = us,
        )

        assertEquals(listOf("Morning", "Evening"), state.windows.map { it.label })
        assertFalse(state.isLoading)
        assertEquals(2, state.activeCount)
    }

    @Test
    fun `a paused window does not count as active`() {
        val state = toSchedulesUiState(
            windows = listOf(window(id = 1), window(id = 2, enabled = false)),
            monitoredApps = apps,
            locale = us,
        )

        assertEquals(1, state.activeCount)
    }

    // --- Draft round-trip -----------------------------------------------------------------------

    @Test
    fun `a window survives a round trip through the editor's draft`() {
        val original = window(id = 7, label = "School", packageName = "com.instagram.android", days = WEEKDAYS_MASK)

        assertEquals(original, original.toDraft().toWindow())
    }

    @Test
    fun `a blank name is saved as a fallback rather than an empty row`() {
        val draft = ScheduleDraft(label = "   ")

        assertEquals(DEFAULT_LABEL, draft.toWindow().label)
    }

    @Test
    fun `a name is trimmed on save`() {
        assertEquals("Bedtime", ScheduleDraft(label = "  Bedtime  ").toWindow().label)
    }

    @Test
    fun `a fresh draft is marked new and defaults to an overnight window`() {
        val draft = ScheduleDraft()

        assertTrue(draft.isNew)
        assertEquals(ScheduleRepository.NEW_WINDOW_ID, draft.id)
        assertEquals(DEFAULT_START_MINUTE, draft.startMinuteOfDay)
        assertEquals(DEFAULT_END_MINUTE, draft.endMinuteOfDay)
    }

    // --- Settings summary -----------------------------------------------------------------------

    @Test
    fun `no windows reads as nothing scheduled`() {
        assertEquals("No schedules yet", schedulesSummary(emptyList(), us))
    }

    @Test
    fun `the summary counts active windows and names the first`() {
        val windows = listOf(window(id = 1, label = "Bedtime", start = 22 * 60), window(id = 2, label = "School"))

        assertEquals("2 schedules · Bedtime 10:00 PM", schedulesSummary(windows, us))
    }

    @Test
    fun `one active window is not pluralised`() {
        assertEquals("1 schedule · Bedtime 10:00 PM", schedulesSummary(listOf(window()), us))
    }

    @Test
    fun `windows that all exist but are paused say so`() {
        val windows = listOf(window(id = 1, enabled = false), window(id = 2, enabled = false))

        assertEquals("All schedules paused", schedulesSummary(windows, us))
    }
}
