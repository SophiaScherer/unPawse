package com.example.unpawse.data.usage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises [UsageRepository] against an in-memory fake DAO, with an injected clock so the daily
 * rollover is deterministic.
 */
class UsageRepositoryTest {

    private val dao = FakeUsageDao()
    private var today = LocalDate.of(2026, 7, 15)
    private val repo = UsageRepository(dao, today = { today })

    @Test
    fun `usage accrues against the daily limit`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.addUsage("com.ig", 4.minutes)

        assertEquals(6, repo.remainingMinutes("com.ig"))
        assertFalse(repo.isLimitReached("com.ig"))
    }

    @Test
    fun `earned minutes extend the budget`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.addUsage("com.ig", 10.minutes)
        assertTrue(repo.isLimitReached("com.ig"))

        repo.addEarnedMinutes("com.ig", 5)

        assertFalse(repo.isLimitReached("com.ig"))
        assertEquals(5, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `a new day resets usage`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.addUsage("com.ig", 10.minutes)
        assertTrue(repo.isLimitReached("com.ig"))

        today = today.plusDays(1)

        assertEquals(10, repo.remainingMinutes("com.ig"))
        assertFalse(repo.isLimitReached("com.ig"))
    }

    @Test
    fun `unmonitored or disabled apps report null remaining`() = runBlocking {
        assertNull(repo.remainingMinutes("com.unknown"))
        assertFalse(repo.isLimitReached("com.unknown"))

        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10, enabled = false)
        assertNull(repo.remainingMinutes("com.ig"))
        assertFalse(repo.isLimitReached("com.ig"))
    }
}

/** Minimal in-memory [UsageDao]; the `@Transaction` accrual helpers are inherited unchanged. */
private class FakeUsageDao : UsageDao() {
    private val apps = mutableMapOf<String, MonitoredAppEntity>()
    private val usage = mutableMapOf<Pair<String, String>, DailyUsageEntity>()

    override fun observeMonitoredApps(): Flow<List<MonitoredAppEntity>> = flowOf(apps.values.toList())

    override suspend fun monitoredApp(packageName: String): MonitoredAppEntity? = apps[packageName]

    override suspend fun upsertMonitoredApp(app: MonitoredAppEntity) {
        apps[app.packageName] = app
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        apps[packageName]?.let { apps[packageName] = it.copy(enabled = enabled) }
    }

    override suspend fun removeMonitoredApp(packageName: String) {
        apps.remove(packageName)
    }

    override fun observeUsageForDate(date: String): Flow<List<DailyUsageEntity>> =
        flowOf(usage.values.filter { it.date == date })

    override suspend fun usageFor(packageName: String, date: String): DailyUsageEntity? =
        usage[packageName to date]

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
