package com.example.unpawse.data.unlocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The source of truth for how often the device has been unlocked. Wraps [UnlockDao] so callers never
 * touch Room, mirroring `UsageRepository`.
 *
 * @param today supplies the current local date, injected so tests can pin it and exercise the daily
 * rollover without a real clock. Read fresh on each call, so a long-lived process crossing midnight
 * starts writing to the new day's row of its own accord.
 */
class UnlockRepository(
    private val dao: UnlockDao,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    /** Records one device unlock against today. Called by the monitor service's receiver. */
    suspend fun recordUnlock() = dao.addUnlock(today().toString())

    /**
     * Unlocks over the last [days] days, today inclusive. Days with no unlocks simply have no row;
     * callers fill the gaps with zero — same contract as `observeRecentUsage`.
     */
    fun observeRecentUnlocks(days: Long): Flow<List<DailyUnlocks>> {
        val end = today()
        val start = end.minusDays(days - 1)
        return dao.observeUnlocksBetween(start.toString(), end.toString())
            .map { rows -> rows.map(DailyUnlocksEntity::toDomain) }
    }

    /** The complete history, oldest first. Used by the data export, not by any screen. */
    suspend fun allUnlocks(): List<DailyUnlocks> = dao.allUnlocks().map(DailyUnlocksEntity::toDomain)

    /** Drops the whole unlock history. Used only by the full reset. */
    suspend fun clearAll() = dao.clearUnlocks()
}
