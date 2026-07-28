package com.example.unpawse.service

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

