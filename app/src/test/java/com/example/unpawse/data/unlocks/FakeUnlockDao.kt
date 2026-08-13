package com.example.unpawse.data.unlocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [UnlockDao] for tests, mirroring `FakeUsageDao`. Only the abstract members are
 * overridden, so the `@Transaction` insert-then-increment in [UnlockDao.addUnlock] is exercised for
 * real rather than reimplemented here.
 *
 * [observeUnlocksBetween] emits a single snapshot; it satisfies the contract rather than modelling
 * live updates.
 */
internal class FakeUnlockDao : UnlockDao() {

    private val rows = mutableMapOf<String, DailyUnlocksEntity>()

    override fun observeUnlocksBetween(
        startDate: String,
        endDate: String,
    ): Flow<List<DailyUnlocksEntity>> =
        flowOf(rows.values.filter { it.date >= startDate && it.date <= endDate }.sortedBy { it.date })

    override suspend fun allUnlocks(): List<DailyUnlocksEntity> = rows.values.sortedBy { it.date }

    override suspend fun insertIfAbsent(row: DailyUnlocksEntity) {
        rows.putIfAbsent(row.date, row)
    }

    override suspend fun insertUnlocks(newRows: List<DailyUnlocksEntity>) {
        newRows.forEach { rows[it.date] = it }
    }

    override suspend fun incrementUnlocks(date: String) {
        rows[date]?.let { rows[date] = it.copy(unlockCount = it.unlockCount + 1) }
    }

    override suspend fun clearUnlocks() {
        rows.clear()
    }
}
