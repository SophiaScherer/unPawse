package com.example.unpawse.data

import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.service.BlockSession
import com.example.unpawse.service.FocusSession

/**
 * Erases everything unPawse has stored, returning it to a first-launch state: screen-time history,
 * monitored apps and their limits, blocking schedules, every cat photo (row and JPEG), and all
 * preferences.
 *
 * In-memory session state is cleared too, and that is not incidental. A focus session hard-blocks
 * every monitored app and survives in `FocusSession` independently of the database — leaving it
 * running after a wipe would keep blocking apps the app no longer has any record of. Likewise an
 * armed [BlockSession] would leave a debt owed for a limit that no longer exists.
 *
 * Deliberately *not* stopping the monitor service: with no monitored apps left,
 * `isMonitoredAndEnabled` returns false for everything, so it accrues nothing and blocks nothing.
 * Stopping and restarting it would add a failure mode (a start that the platform refuses) in
 * exchange for nothing.
 */
class ResetRepository(
    private val usage: UsageRepository,
    private val schedules: ScheduleRepository,
    private val captures: CaptureRepository,
    private val unlocks: UnlockRepository,
    private val focusSession: FocusSession,
    private val blockSession: BlockSession,
    /** Wraps the row deletes so a failure part-way leaves every table as it was. */
    private val transactor: Transactor,
    /**
     * Clears the preference store. Injected as a function rather than taking a
     * [SettingsRepository], which needs a `Context` and so cannot be built in a JVM unit test —
     * same reasoning as the `now`/`today` lambdas elsewhere in the data layer.
     */
    private val clearSettings: suspend () -> Unit,
) {
    /** Rows first in one transaction, then the steps that can't be rolled back, preferences last. */
    suspend fun eraseEverything() {
        transactor.inTransaction { eraseRows() }
        afterRowsErased()
        clearSettings()
    }

    /** Every Room delete, for a caller that owns the transaction; an import adds its restore to it. */
    suspend fun eraseRows() {
        captures.deleteAllRows()
        usage.clearAll()
        // Schedules must go too, or a window would keep blocking apps the app no longer monitors.
        schedules.clearAll()
        unlocks.clearAll()
    }

    /**
     * What can't be undone, so it runs only after [eraseRows] has committed. The sessions stop before
     * any preference write, so the focus-persistence collector's `null` can't re-add a key afterwards.
     */
    suspend fun afterRowsErased() {
        captures.deleteAllFiles()
        focusSession.stop()
        blockSession.clear()
    }
}
