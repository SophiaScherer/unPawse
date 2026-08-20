package com.example.unpawse.ui.stats

import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UNLIMITED_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The derived achievement rules. Every case pins [today] rather than reading the clock, and asserts
 * the *earliest* qualifying date — the badges sort by recency, so a rule that returned the latest
 * date would silently reorder the rail.
 */
class AchievementRulesTest {

    // A Thursday.
    private val today = LocalDate.of(2026, 7, 16)

    private fun app(
        pkg: String = "a",
        limitMinutes: Int = 60,
        enabled: Boolean = true,
        weekendLimitMinutes: Int? = null,
    ) = MonitoredApp(pkg, pkg.uppercase(), limitMinutes, enabled, weekendLimitMinutes)

    private fun usage(
        pkg: String,
        daysAgo: Long,
        usedMinutes: Int,
        earnedMinutes: Int = 0,
        blockedCount: Int = 0,
    ) = DailyUsage(
        pkg,
        today.minusDays(daysAgo).toString(),
        usedMinutes * 60L,
        earnedMinutes * 60L,
        blockedCount,
    )

    private fun evaluate(
        captureDates: List<LocalDate> = emptyList(),
        usage: List<DailyUsage> = emptyList(),
        apps: List<MonitoredApp> = listOf(app()),
    ) = evaluateAchievements(AchievementInput(captureDates, usage, apps, today))

    private fun day(daysAgo: Long) = today.minusDays(daysAgo)

    // --- Nothing earned on a fresh install ------------------------------------------------------

    @Test
    fun `an empty history earns nothing`() {
        val earned = evaluate()

        AchievementId.entries.forEach { id ->
            assertNull("$id should not be earned", earned[id])
        }
    }

    @Test
    fun `every catalogue entry is evaluated`() {
        // A rule added to the enum but not to evaluateAchievements would render permanently locked.
        assertEquals(AchievementId.entries.toSet(), evaluate().keys)
        assertEquals(AchievementId.entries.toSet(), ACHIEVEMENT_CATALOGUE.map { it.id }.toSet())
    }

    // --- First Cat / Cat Collector --------------------------------------------------------------

    @Test
    fun `first cat is earned on the earliest capture, not the latest`() {
        val earned = evaluate(captureDates = listOf(day(1), day(30), day(9)))

        assertEquals(day(30), earned[AchievementId.FIRST_CAT])
    }

    @Test
    fun `cat collector needs the full target and lands on the tenth capture`() {
        val nine = (1..9).map { day(it.toLong()) }
        assertNull(evaluate(captureDates = nine)[AchievementId.CAT_COLLECTOR])

        // The 10th chronologically is day(1); adding an older one shifts which capture is 10th.
        val ten = nine + day(0)
        assertEquals(day(0), evaluate(captureDates = ten)[AchievementId.CAT_COLLECTOR])
    }

    @Test
    fun `several captures on one day each count toward the collector`() {
        val sameDay = List(CAT_COLLECTOR_TARGET) { day(3) }

        assertEquals(day(3), evaluate(captureDates = sameDay)[AchievementId.CAT_COLLECTOR])
    }

    // --- 7-day streak ---------------------------------------------------------------------------

    @Test
    fun `six consecutive days is not a week`() {
        val six = (1..6).map { day(it.toLong()) }

        assertNull(evaluate(captureDates = six)[AchievementId.WEEK_STREAK])
    }

    @Test
    fun `seven consecutive days is earned on the seventh`() {
        val seven = (0..6).map { day(it.toLong()) }

        // Oldest is day(6); the run completes on day(0).
        assertEquals(day(0), evaluate(captureDates = seven)[AchievementId.WEEK_STREAK])
    }

    @Test
    fun `a longer run still reports the day the streak completed`() {
        val twelve = (0..11).map { day(it.toLong()) }

        // Run starts at day(11); the 7th day of it is day(5), not day(0).
        assertEquals(day(5), evaluate(captureDates = twelve)[AchievementId.WEEK_STREAK])
    }

    @Test
    fun `a gap breaks the streak`() {
        // Two runs of four with a missing day between them; neither reaches seven.
        val broken = listOf(0L, 1, 2, 3, 5, 6, 7, 8).map { day(it) }

        assertNull(evaluate(captureDates = broken)[AchievementId.WEEK_STREAK])
    }

    @Test
    fun `duplicate captures on one day do not inflate the streak`() {
        val padded = List(7) { day(2) } + listOf(day(1), day(0))

        assertNull(evaluate(captureDates = padded)[AchievementId.WEEK_STREAK])
    }

    // --- Under Budget ---------------------------------------------------------------------------

    @Test
    fun `a day inside every limit is earned`() {
        val earned = evaluate(usage = listOf(usage("a", 3, usedMinutes = 20)))

        assertEquals(day(3), earned[AchievementId.CLEAN_DAY])
    }

    @Test
    fun `a day over the limit does not qualify`() {
        val earned = evaluate(usage = listOf(usage("a", 3, usedMinutes = 90)))

        assertNull(earned[AchievementId.CLEAN_DAY])
    }

    /** Without this guard, every day the phone sat in a drawer would earn the badge. */
    @Test
    fun `a day with no usage at all does not qualify`() {
        val earned = evaluate(usage = listOf(usage("a", 3, usedMinutes = 0)))

        assertNull(earned[AchievementId.CLEAN_DAY])
    }

    /** Today is still in progress: a badge awarded at 9am and revoked by lunchtime is worse. */
    @Test
    fun `today never qualifies however good it looks`() {
        val earned = evaluate(usage = listOf(usage("a", 0, usedMinutes = 1)))

        assertNull(earned[AchievementId.CLEAN_DAY])
    }

    @Test
    fun `the earliest clean day wins`() {
        val earned = evaluate(
            usage = listOf(
                usage("a", 2, usedMinutes = 10),
                usage("a", 8, usedMinutes = 10),
                usage("a", 5, usedMinutes = 10),
            ),
        )

        assertEquals(day(8), earned[AchievementId.CLEAN_DAY])
    }

    @Test
    fun `one app over its limit spoils the day for all of them`() {
        val earned = evaluate(
            apps = listOf(app("a", limitMinutes = 60), app("b", limitMinutes = 60)),
            usage = listOf(usage("a", 3, usedMinutes = 10), usage("b", 3, usedMinutes = 300)),
        )

        assertNull(earned[AchievementId.CLEAN_DAY])
    }

    @Test
    fun `earned bonus minutes count toward staying inside the limit`() {
        // 70 minutes used against a 60-minute limit, but 15 were bought back with a cat.
        val earned = evaluate(usage = listOf(usage("a", 3, usedMinutes = 70, earnedMinutes = 15)))

        assertEquals(day(3), earned[AchievementId.CLEAN_DAY])
    }

    @Test
    fun `a weekend day is judged against the weekend budget`() {
        // today is Thursday 2026-07-16, so 5 days ago is Saturday the 11th.
        val saturday = day(5)
        assertEquals("precondition", java.time.DayOfWeek.SATURDAY, saturday.dayOfWeek)

        val apps = listOf(app("a", limitMinutes = 30, weekendLimitMinutes = 120))

        // 60 minutes breaks the weekday budget but sits inside the weekend one.
        assertEquals(
            saturday,
            evaluate(apps = apps, usage = listOf(usage("a", 5, usedMinutes = 60)))[AchievementId.CLEAN_DAY],
        )
    }

    @Test
    fun `an uncapped day cannot be exceeded but still needs real usage`() {
        val apps = listOf(app("a", limitMinutes = 30, weekendLimitMinutes = UNLIMITED_MINUTES))
        val saturday = day(5)

        assertEquals(
            saturday,
            evaluate(apps = apps, usage = listOf(usage("a", 5, usedMinutes = 600)))[AchievementId.CLEAN_DAY],
        )
    }

    @Test
    fun `a disabled app is not judged`() {
        val earned = evaluate(
            apps = listOf(app("a", limitMinutes = 60), app("b", limitMinutes = 1, enabled = false)),
            usage = listOf(usage("a", 3, usedMinutes = 10), usage("b", 3, usedMinutes = 300)),
        )

        assertEquals(day(3), earned[AchievementId.CLEAN_DAY])
    }

    @Test
    fun `nothing monitored earns nothing rather than everything`() {
        val earned = evaluate(apps = emptyList(), usage = listOf(usage("a", 3, usedMinutes = 10)))

        assertNull(earned[AchievementId.CLEAN_DAY])
    }

    // --- Well Blocked ---------------------------------------------------------------------------

    @Test
    fun `blocks accumulate across days and apps`() {
        val earned = evaluate(
            usage = listOf(
                usage("a", 5, usedMinutes = 10, blockedCount = 10),
                usage("b", 5, usedMinutes = 10, blockedCount = 10),
                usage("a", 2, usedMinutes = 10, blockedCount = 5),
            ),
        )

        // 20 by day(5), reaching 25 on day(2).
        assertEquals(day(2), earned[AchievementId.BLOCKS_RESPECTED])
    }

    @Test
    fun `short of the target earns nothing`() {
        val earned = evaluate(usage = listOf(usage("a", 2, usedMinutes = 10, blockedCount = 24)))

        assertNull(earned[AchievementId.BLOCKS_RESPECTED])
    }

    @Test
    fun `the badge lands on the day the total was reached, not the last day with blocks`() {
        val earned = evaluate(
            usage = listOf(
                usage("a", 9, usedMinutes = 10, blockedCount = BLOCKS_RESPECTED_TARGET),
                usage("a", 1, usedMinutes = 10, blockedCount = 50),
            ),
        )

        assertEquals(day(9), earned[AchievementId.BLOCKS_RESPECTED])
    }

    // --- Presentation ---------------------------------------------------------------------------

    @Test
    fun `nothing earned renders every badge locked, never an empty rail`() {
        val rows = toAchievements(evaluate())

        assertEquals(ACHIEVEMENT_CATALOGUE.size, rows.size)
        assertTrue(rows.none { it.unlocked })
        assertTrue(rows.all { it.icon == AchievementIcon.LOCKED })
    }

    /**
     * A locked card states what earns it. It used to say "Coming Soon" on all five, which describes
     * an unreleased feature rather than an unearned reward — every rule here is implemented.
     */
    @Test
    fun `a locked badge states its criterion, not a roadmap promise`() {
        val rows = toAchievements(evaluate()).associateBy { it.title }

        ACHIEVEMENT_CATALOGUE.forEach { rule ->
            assertEquals(rule.subtitle, rows.getValue(rule.title).subtitle)
        }
        assertTrue(rows.values.none { it.subtitle.contains("Coming", ignoreCase = true) })
    }

    @Test
    fun `earned badges come first, most recent at the front`() {
        val rows = toAchievements(
            mapOf(
                AchievementId.FIRST_CAT to day(30),
                AchievementId.WEEK_STREAK to day(2),
                AchievementId.CLEAN_DAY to null,
                AchievementId.CAT_COLLECTOR to null,
                AchievementId.BLOCKS_RESPECTED to null,
            ),
        )

        assertEquals(listOf("7-Day Streak", "First Cat"), rows.take(2).map { it.title })
        assertTrue(rows.take(2).all { it.unlocked })
        assertTrue(rows.drop(2).none { it.unlocked })
    }

    @Test
    fun `an earned badge keeps its real subtitle and icon`() {
        val rows = toAchievements(mapOf(AchievementId.FIRST_CAT to day(1)))
        val firstCat = rows.single { it.title == "First Cat" }

        assertTrue(firstCat.unlocked)
        assertEquals("Your first verified cat", firstCat.subtitle)
        assertEquals(AchievementIcon.TROPHY, firstCat.icon)
    }
}
