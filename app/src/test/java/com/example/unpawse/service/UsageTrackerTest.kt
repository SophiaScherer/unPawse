package com.example.unpawse.service

import com.example.unpawse.data.usage.DailyUsageEntity
import com.example.unpawse.data.usage.MonitoredAppEntity
import com.example.unpawse.data.usage.UsageDao
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

    private fun tracker(ticks: List<String?>) =
        UsageTracker(repo, monitorOf(ticks), now = { clockMillis })

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

        assertEquals(listOf("com.ig"), signals)
    }

    @Test
    fun `returning to a blocked app signals again`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 0)
        // Over limit → leave for another app → come back: the overlay must be re-triggered.
        val tracker = tracker(listOf("com.ig", "com.ig", "com.other", "com.ig", "com.ig"))

        val (signals, collector) = collectSignals(tracker)
        tracker.run()
        collector.cancel()

        assertEquals(listOf("com.ig", "com.ig"), signals)
    }

    /**
     * Subscribes to [UsageTracker.limitReached] on [Dispatchers.Unconfined] so each emission is
     * handled inline at the emit point. The fake DAO never really suspends, so `run()` would
     * otherwise finish without ever yielding to a normally-dispatched collector.
     */
    private fun CoroutineScope.collectSignals(tracker: UsageTracker): Pair<List<String>, Job> {
        val signals = mutableListOf<String>()
        val job = launch(Dispatchers.Unconfined) { tracker.limitReached.collect { signals.add(it) } }
        return signals to job
    }
}

/** Minimal in-memory [UsageDao]; the `@Transaction` accrual helpers are inherited unchanged. */
private class FakeUsageDao : UsageDao() {
    private val apps = mutableMapOf<String, MonitoredAppEntity>()
    private val usage = mutableMapOf<Pair<String, String>, DailyUsageEntity>()

    override fun observeMonitoredApps(): Flow<List<MonitoredAppEntity>> = flowOf(apps.values.toList())
    override suspend fun monitoredApp(packageName: String) = apps[packageName]
    override suspend fun upsertMonitoredApp(app: MonitoredAppEntity) { apps[app.packageName] = app }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        apps[packageName]?.let { apps[packageName] = it.copy(enabled = enabled) }
    }

    override suspend fun removeMonitoredApp(packageName: String) { apps.remove(packageName) }

    override fun observeUsageForDate(date: String): Flow<List<DailyUsageEntity>> =
        flowOf(usage.values.filter { it.date == date })

    override suspend fun usageFor(packageName: String, date: String) = usage[packageName to date]

    override suspend fun insertUsageIfAbsent(row: DailyUsageEntity) {
        usage.putIfAbsent(row.packageName to row.date, row)
    }

    override suspend fun addUsedSeconds(packageName: String, date: String, seconds: Long) {
        usage[packageName to date]?.let {
            usage[packageName to date] = it.copy(usedSeconds = it.usedSeconds + seconds)
        }
    }

    override suspend fun addEarnedSeconds(packageName: String, date: String, seconds: Long) {
        usage[packageName to date]?.let {
            usage[packageName to date] = it.copy(earnedSeconds = it.earnedSeconds + seconds)
        }
    }
}
