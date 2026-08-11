package com.example.unpawse.data.schedule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [ScheduleDao] for tests, shared by everything that needs a real [ScheduleRepository]
 * without Room (mirrors `FakeUsageDao`). Reproduces the DAO's ordering contract — earliest start
 * first, id as the tiebreak — because [activeWindowFor] returns the *first* match and the tests
 * would otherwise depend on insertion order.
 *
 * The observe* flows emit a single snapshot; they're here to satisfy the contract, not to model
 * live updates.
 */
internal class FakeScheduleDao : ScheduleDao {

    private val windows = mutableMapOf<Long, ScheduleWindowEntity>()
    private var nextId = 1L

    private fun ordered(rows: Collection<ScheduleWindowEntity>) =
        rows.sortedWith(compareBy({ it.startMinuteOfDay }, { it.id }))

    override fun observeWindows(): Flow<List<ScheduleWindowEntity>> = flowOf(ordered(windows.values))

    override fun observeWindowsFor(packageName: String): Flow<List<ScheduleWindowEntity>> =
        flowOf(ordered(windows.values.filter { it.packageName == null || it.packageName == packageName }))

    override suspend fun allWindows(): List<ScheduleWindowEntity> = ordered(windows.values)

    override suspend fun window(id: Long): ScheduleWindowEntity? = windows[id]

    override suspend fun upsertWindow(window: ScheduleWindowEntity): Long {
        val id = if (window.id == ScheduleRepository.NEW_WINDOW_ID) nextId++ else window.id
        windows[id] = window.copy(id = id)
        return id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        windows[id]?.let { windows[id] = it.copy(enabled = enabled) }
    }

    override suspend fun deleteWindow(id: Long) {
        windows.remove(id)
    }

    override suspend fun deleteWindowsFor(packageName: String) {
        windows.values.removeAll { it.packageName == packageName }
    }

    override suspend fun clearWindows() {
        windows.clear()
    }
}
