package com.example.unpawse.data

import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.settings.SettingsRepository
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
    private val focusSession: FocusSession,
    private val blockSession: BlockSession,
    /**
     * Clears the preference store. Injected as a function rather than taking a
     * [SettingsRepository], which needs a `Context` and so cannot be built in a JVM unit test —
     * same reasoning as the `now`/`today` lambdas elsewhere in the data layer.
     */
    private val clearSettings: suspend () -> Unit,
) {
    /**
     * Order matters at one point only: the sessions are stopped first, so the container's
     * focus-persistence collector writes its `null` before preferences are cleared rather than
     * re-adding a key afterwards.
     */
    suspend fun eraseEverything() {
        focusSession.stop()
        blockSession.clear()

        captures.deleteAllCaptures()
        usage.clearAll()
        // Schedules are plain rows with no in-memory counterpart, so unlike the sessions above they
        // carry no ordering constraint — but they must go, or a window would keep blocking apps the
        // app no longer has any record of monitoring.
        schedules.clearAll()
        clearSettings()
    }
}
