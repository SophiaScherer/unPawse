package com.example.unpawse.service

import com.example.unpawse.data.schedule.EVERY_DAY_MASK
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.usage.FakeUsageDao
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Pure clamping rule for a single tick's credit. */
class AccrualMathTest {

    @Test
    fun `a normal tick is credited in full`() {
        assertEquals(1_000L, accrualMillis(1_000L, maxTickMillis = 5_000L))
    }

    @Test
    fun `a suspended process cannot burn the whole budget`() {
        // Process slept an hour; credit is capped at one max tick rather than 3.6M ms.
        assertEquals(5_000L, accrualMillis(3_600_000L, maxTickMillis = 5_000L))
    }

    @Test
    fun `clock skew never credits negative time`() {
        assertEquals(0L, accrualMillis(-500L, maxTickMillis = 5_000L))
    }
}

/**
 * Drives [UsageTracker] with a scripted foreground sequence and a fake clock that ticks 1s per
 * emission, so accrual is exact and no device is involved.
 */
class UsageTrackerTest {

    private val dao = FakeUsageDao()
    private val today = LocalDate.of(2026, 7, 15)
    private val repo = UsageRepository(dao, today = { today })
    private var clockMillis = 0L

    /** Emits [ticks], advancing the clock 1s before each — mimicking a 1s poll. */
    private fun monitorOf(ticks: List<String?>) = object : ForegroundAppMonitor {
        override fun foregroundApp(): Flow<String?> = flow {
            ticks.forEach { pkg ->
                clockMillis += 1_000L
                emit(pkg)
            }
        }
    }

    private val focusSession = FocusSession(now = { clockMillis })

    // No scheduleBlock: these cover the limit/focus paths, and the tracker's default reports no
    // window. Schedule behaviour lives in [ScheduledBlockTest].
    private fun tracker(ticks: List<String?>, warningMinutes: Int = UsageTracker.WARNING_OFF) =
        UsageTracker(
            repo,
            monitorOf(ticks),
            now = { clockMillis },
            focusSession = focusSession,
            warningMinutes = { warningMinutes },
        )

    private suspend fun usedSecondsFor(pkg: String) = dao.usageFor(pkg, today.toString())?.usedSeconds

    @Test
    fun `time accrues for a monitored app`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)

        // Instagram is in front for the two intervals ending at ticks 2 and 3.
        tracker(listOf("com.ig", "com.ig", "com.ig")).run()

        assertEquals(2L, usedSecondsFor("com.ig"))
    }

    @Test
    fun `unmonitored apps never get a usage row`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)

        tracker(listOf("com.ig", "com.other", "com.other")).run()

        assertEquals(1L, usedSecondsFor("com.ig"))
        assertNull(usedSecondsFor("com.other"))
    }

    @Test
    fun `a disabled app is not tracked`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10, enabled = false)

        tracker(listOf("com.ig", "com.ig", "com.ig")).run()

        assertNull(usedSecondsFor("com.ig"))
    }

    @Test
    fun `screen off stops accrual`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)

        // Only the first interval has Instagram in front; then the screen goes off (null).
        tracker(listOf("com.ig", null, null, null)).run()

        assertEquals(1L, usedSecondsFor("com.ig"))
    }

    @Test
    fun `limit reached fires once per breach not once per tick`() = runBlocking {
        // A 0-minute limit means the first credited second is already over budget.
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)
        val tracker = tracker(List(5) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(listOf(BlockEvent("com.ig", BlockReason.LIMIT)), signals)
    }

    @Test
    fun `returning to a blocked app signals again`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)
        // Over limit → leave for another app → come back: the overlay must be re-triggered.
        val tracker = tracker(listOf("com.ig", "com.ig", "com.other", "com.ig", "com.ig"))

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(listOf(BlockEvent("com.ig", BlockReason.LIMIT), BlockEvent("com.ig", BlockReason.LIMIT)), signals)
    }

    @Test
    fun `a focus session hard-blocks a monitored app that is under its limit`() = runBlocking {
        // Well under a 10-minute limit, so only a focus session can trigger a block.
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        focusSession.start(durationMinutes = 30)
        val tracker = tracker(List(3) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(listOf(BlockEvent("com.ig", BlockReason.FOCUS)), signals)
    }

    @Test
    fun `focus takes precedence over an exhausted limit`() = runBlocking {
        // Over budget (would normally be a LIMIT block) but focus is running → escape-less FOCUS block.
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)
        focusSession.start(durationMinutes = 30)
        val tracker = tracker(List(3) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(listOf(BlockEvent("com.ig", BlockReason.FOCUS)), signals)
    }

    // --- Prevented count ------------------------------------------------------------------------
    // Every blockRequired emission is also counted into daily_usage.blockedCount, which is what the
    // Stats "Prevented" card reports. The two must not be able to drift apart.

    private suspend fun blockedCountFor(pkg: String) = dao.usageFor(pkg, today.toString())?.blockedCount

    @Test
    fun `a block is counted once per breach, not once per tick`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)

        tracker(List(5) { "com.ig" }).run()

        assertEquals(1, blockedCountFor("com.ig"))
    }

    @Test
    fun `returning to a blocked app counts a second interruption`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)

        tracker(listOf("com.ig", "com.ig", "com.other", "com.ig", "com.ig")).run()

        assertEquals(2, blockedCountFor("com.ig"))
    }

    @Test
    fun `the count lands on the blocked app, not whatever else was open`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)
        repo.setLimit("com.tok", "TikTok", dailyLimitMinutes = 10)

        tracker(listOf("com.ig", "com.ig", "com.tok", "com.tok")).run()

        assertEquals(1, blockedCountFor("com.ig"))
        assertEquals(0, blockedCountFor("com.tok"))
    }

    @Test
    fun `a focus block counts too`() = runBlocking {
        // Under its limit, so only the focus session can block — still an interruption prevented.
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        focusSession.start(durationMinutes = 30)

        tracker(List(3) { "com.ig" }).run()

        assertEquals(1, blockedCountFor("com.ig"))
    }

    @Test
    fun `an app that is never blocked stays at zero`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)

        tracker(List(3) { "com.ig" }).run()

        assertEquals(0, blockedCountFor("com.ig"))
    }

    // --- Warning before lock --------------------------------------------------------------------

    @Test
    fun `a warning fires once as the budget runs low`() = runBlocking {
        // A 2-minute limit: after the first credited second, 119s remain — 1 whole minute once
        // floored, i.e. exactly at the threshold. Five ticks would warn five times if it weren't
        // armed once.
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 2)
        val tracker = tracker(List(5) { "com.ig" }, warningMinutes = 1)

        val (warnings, collector) = collectWarnings(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(listOf(WarningEvent("com.ig", remainingMinutes = 1)), warnings)
    }

    /**
     * Remaining minutes are floored, so the last sub-minute of budget reads as 0 while the app is
     * still usable. That is the sharpest moment to warn — an earlier `>= 1` guard skipped it.
     */
    @Test
    fun `the final sub-minute still warns`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 1)
        val tracker = tracker(List(3) { "com.ig" }, warningMinutes = 1)

        val (warnings, collector) = collectWarnings(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(listOf(WarningEvent("com.ig", remainingMinutes = 0)), warnings)
    }

    @Test
    fun `no warning fires when the setting is off`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 1)
        val tracker = tracker(List(5) { "com.ig" }, warningMinutes = UsageTracker.WARNING_OFF)

        val (warnings, collector) = collectWarnings(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(emptyList<WarningEvent>(), warnings)
    }

    @Test
    fun `no warning while the budget is still comfortable`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 60)
        val tracker = tracker(List(5) { "com.ig" }, warningMinutes = 5)

        val (warnings, collector) = collectWarnings(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(emptyList<WarningEvent>(), warnings)
    }

    /** Once the app is blocked, the block itself is the message — warning too would duplicate it. */
    @Test
    fun `an already-blocked app is not also warned about`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)
        val tracker = tracker(List(5) { "com.ig" }, warningMinutes = 5)

        val (warnings, collector) = collectWarnings(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(emptyList<WarningEvent>(), warnings)
    }

    /** Unlike a block, a warning must not repeat just because the user switched apps and back. */
    @Test
    fun `returning to a warned app does not warn again`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 2)
        val tracker = tracker(
            listOf("com.ig", "com.ig", "com.other", "com.ig", "com.ig"),
            warningMinutes = 1,
        )

        val (warnings, collector) = collectWarnings(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(1, warnings.size)
    }

    /**
     * A run's warned-set lives for that run, so this exercises the rule that matters across a
     * grant: once a cat lifts the app clear of the threshold, it is no longer in warning range.
     */
    @Test
    fun `earning time back lifts the app out of warning range`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 2)

        val first = tracker(List(2) { "com.ig" }, warningMinutes = 1)
        val (warnings, collector) = collectWarnings(first)
        first.run()
        collector.cancel()
        assertEquals("precondition: warned once", 1, warnings.size)

        repo.addEarnedMinutes("com.ig", 30)

        val second = tracker(List(3) { "com.ig" }, warningMinutes = 1)
        val (moreWarnings, secondCollector) = collectWarnings(second)
        second.run()
        secondCollector.cancel()

        assertEquals(emptyList<WarningEvent>(), moreWarnings)
    }

    /**
     * Subscribes to [UsageTracker.blockRequired] on [Dispatchers.Unconfined] so each emission is
     * handled inline at the emit point. The fake DAO never really suspends, so `run()` would
     * otherwise finish without ever yielding to a normally-dispatched collector.
     */
    private fun CoroutineScope.collectSignals(tracker: UsageTracker): Pair<List<BlockEvent>, Job> {
        val signals = mutableListOf<BlockEvent>()
        val job = launch(Dispatchers.Unconfined) { tracker.blockRequired.collect { signals.add(it) } }
        return signals to job
    }

    /** Same dispatcher reasoning as [collectSignals]. */
    private fun CoroutineScope.collectWarnings(tracker: UsageTracker): Pair<List<WarningEvent>, Job> {
        val warnings = mutableListOf<WarningEvent>()
        val job = launch(Dispatchers.Unconfined) { tracker.warningRequired.collect { warnings.add(it) } }
        return warnings to job
    }
}

/**
 * Schedule windows as a third block reason, and the precedence between all three. Shares the fixture
 * shape of [UsageTrackerTest] — a scripted monitor and a fake clock, no device.
 */
class ScheduledBlockTest {

    private val dao = FakeUsageDao()
    private val today = LocalDate.of(2026, 7, 15)
    private val repo = UsageRepository(dao, today = { today })
    private var clockMillis = 0L
    private var activeWindow: ScheduleWindow? = null

    private fun monitorOf(ticks: List<String?>) = object : ForegroundAppMonitor {
        override fun foregroundApp(): Flow<String?> = flow {
            ticks.forEach { pkg ->
                clockMillis += 1_000L
                emit(pkg)
            }
        }
    }

    private val focusSession = FocusSession(now = { clockMillis })

    private fun tracker(ticks: List<String?>) = UsageTracker(
        repo,
        monitorOf(ticks),
        now = { clockMillis },
        focusSession = focusSession,
        scheduleBlock = { activeWindow },
    )

    private fun window(endHour: Int = 7) = ScheduleWindow(
        id = 1,
        label = "Bedtime",
        packageName = null,
        startMinuteOfDay = 22 * 60,
        endMinuteOfDay = endHour * 60,
        daysMask = EVERY_DAY_MASK,
        enabled = true,
    )

    private fun CoroutineScope.collectSignals(tracker: UsageTracker): Pair<List<BlockEvent>, Job> {
        val signals = mutableListOf<BlockEvent>()
        val job = launch(Dispatchers.Unconfined) { tracker.blockRequired.collect { signals.add(it) } }
        return signals to job
    }

    private fun CoroutineScope.collectReleases(tracker: UsageTracker): Pair<List<String>, Job> {
        val released = mutableListOf<String>()
        val job = launch(Dispatchers.Unconfined) { tracker.blockReleased.collect { released.add(it) } }
        return released to job
    }

    @Test
    fun `a schedule window blocks an app that is well under its limit`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        activeWindow = window()
        val tracker = tracker(List(3) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(
            listOf(BlockEvent("com.ig", BlockReason.SCHEDULE, endsAtMinuteOfDay = 7 * 60)),
            signals,
        )
    }

    /** A scheduled block is an interruption prevented like any other, so it counts too. */
    @Test
    fun `a scheduled block is counted`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        activeWindow = window()

        tracker(List(3) { "com.ig" }).run()

        assertEquals(1, dao.usageFor("com.ig", today.toString())?.blockedCount)
    }

    @Test
    fun `the window's end travels with the event`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        activeWindow = window(endHour = 6)
        val tracker = tracker(List(2) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(6 * 60, signals.single().endsAtMinuteOfDay)
    }

    @Test
    fun `a schedule takes precedence over an exhausted limit`() = runBlocking {
        // Over budget, so this would normally be a LIMIT block with a camera escape. A schedule is
        // escape-less, and offering the camera here would be a promise we couldn't keep.
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)
        activeWindow = window()
        val tracker = tracker(List(3) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(BlockReason.SCHEDULE, signals.single().reason)
    }

    @Test
    fun `focus takes precedence over a schedule`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        activeWindow = window()
        focusSession.start(durationMinutes = 30)
        val tracker = tracker(List(3) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(BlockReason.FOCUS, signals.single().reason)
        assertNull("focus blocks carry no window end", signals.single().endsAtMinuteOfDay)
    }

    @Test
    fun `an unmonitored app is untouched by a global window`() = runBlocking {
        activeWindow = window()
        val tracker = tracker(List(3) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(emptyList<BlockEvent>(), signals)
    }

    @Test
    fun `a schedule block fires once per breach not once per tick`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        activeWindow = window()
        val tracker = tracker(List(6) { "com.ig" })

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(1, signals.size)
    }

    @Test
    fun `the window ending releases the block while the user is still in the app`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        activeWindow = window()

        // Two ticks blocked, then the window ends without the user going anywhere. Nothing else
        // would take the overlay down — dismissBlockWhenUserLeaves only fires on an app switch.
        val ticks = listOf("com.ig", "com.ig", "com.ig", "com.ig")
        val monitor = object : ForegroundAppMonitor {
            override fun foregroundApp(): Flow<String?> = flow {
                ticks.forEachIndexed { index, pkg ->
                    clockMillis += 1_000L
                    if (index == 2) activeWindow = null
                    emit(pkg)
                }
            }
        }
        val tracker = UsageTracker(
            repo,
            monitor,
            now = { clockMillis },
            focusSession = focusSession,
            scheduleBlock = { activeWindow },
        )

        val (signals, signalCollector) = collectSignals(tracker)
        val (released, releaseCollector) = collectReleases(tracker)
        tracker.run()
        signalCollector.cancel()
        releaseCollector.cancel()

        assertEquals(BlockReason.SCHEDULE, signals.single().reason)
        assertEquals(listOf("com.ig"), released)
    }

    @Test
    fun `no release is emitted for an app that was never blocked`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        val tracker = tracker(List(4) { "com.ig" })

        val (released, collector) = collectReleases(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(emptyList<String>(), released)
    }

    @Test
    fun `earning time back also releases a limit block`() = runBlocking {
        // The release signal is not schedule-specific: it fires whenever every reason has cleared.
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)
        // Tick 1 attributes nothing (no previous app), tick 2 is the one that blocks. Credit after
        // that, so tick 3 is the first to see the app back in budget.
        val monitor = object : ForegroundAppMonitor {
            override fun foregroundApp(): Flow<String?> = flow {
                repeat(3) { index ->
                    clockMillis += 1_000L
                    emit("com.ig")
                    if (index == 1) repo.addEarnedMinutes("com.ig", 30)
                }
            }
        }
        val tracker = UsageTracker(
            repo,
            monitor,
            now = { clockMillis },
            focusSession = focusSession,
            scheduleBlock = { null },
        )

        val (released, collector) = collectReleases(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(listOf("com.ig"), released)
    }
}

/** Copy for the warning notification. */
class WarningTextTest {

    @Test
    fun `minutes are pluralised`() {
        assertEquals(
            "5 minutes of Instagram left today. Photograph a cat to earn more.",
            warningText("Instagram", 5),
        )
        assertEquals(
            "1 minute of Instagram left today. Photograph a cat to earn more.",
            warningText("Instagram", 1),
        )
    }
}

