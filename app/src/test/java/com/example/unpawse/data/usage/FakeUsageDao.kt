package com.example.unpawse.data.usage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [UsageDao] for tests, shared by everything that needs a real [UsageRepository] without
 * Room. Only the abstract members are overridden — the `@Transaction` accrual helpers are inherited
 * unchanged, so tests exercise the real insert-then-increment logic.
 *
 * The observe* flows emit a single snapshot; they're here to satisfy the contract, not to model
 * live updates. Tests that need those should use the suspend reads.
 */
internal class FakeUsageDao : UsageDao() {

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
