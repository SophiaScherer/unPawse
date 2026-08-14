package com.example.unpawse.service

import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Turns [ForegroundAppMonitor] ticks into accrued screen time and signals when a monitored app must
 * be blocked — its daily budget is spent, a [FocusSession] is running, or a schedule window covers
 * this moment. Deliberately knows nothing about *showing* a block — it only emits [blockRequired];
 * the overlay service consumes it. Held as a singleton by the AppContainer so the UI can observe the
 * signal while the service drives [run].
 */
class UsageTracker(
    private val usageRepository: UsageRepository,
    private val monitor: ForegroundAppMonitor,
    private val now: () -> Long = System::currentTimeMillis,
    private val focusSession: FocusSession = FocusSession(),
    /**
     * Minutes of remaining budget at which to warn, or 0 for off. A lambda so the setting is read
     * fresh each tick, keeping DataStore out of the tracker (see `AppContainer.warningMinutes`).
     */
    private val warningMinutes: () -> Int = { 0 },
    /**
     * The schedule window blocking an app right now, or null. Same lambda shape and reasoning as
     * [warningMinutes]: read fresh each tick so an edit takes effect immediately, and Room stays out
     * of the tracker (see `AppContainer.scheduleGate`).
     */
    private val scheduleBlock: (String) -> ScheduleWindow? = { null },
) {
    private val _blockRequired = MutableSharedFlow<BlockEvent>(extraBufferCapacity = EVENT_BUFFER)

    private val _warningRequired = MutableSharedFlow<WarningEvent>(extraBufferCapacity = EVENT_BUFFER)

    /**
     * Apps about to run out of budget. Fires once as an app crosses the threshold, and re-arms only
     * when it climbs back above it — never on an app switch, unlike [blockRequired]. A block must
     * reappear every time the user returns to the app; a warning said twice is just nagging.
     */
    val warningRequired: SharedFlow<WarningEvent> = _warningRequired.asSharedFlow()

    /**
     * Apps that must be blocked right now, with *why* (daily limit vs. focus session). Fires once per
     * breach, not once per tick; the overlay differs by [BlockReason].
     *
     * Each emission is also counted into `daily_usage.blockedCount`, which is what Stats' "Prevented"
     * card reports. Note that [signalledFor] re-arms on an app switch, so leaving a blocked app and
     * coming back counts as a **second** prevented interruption — deliberately, since that is a
     * second time the user was stopped. Don't "fix" it into a once-per-day count.
     */
    val blockRequired: SharedFlow<BlockEvent> = _blockRequired.asSharedFlow()

    private val _blockReleased = MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER)

    /**
     * Apps that were blocked and no longer are, while the user is still sitting on them.
     *
     * The overlay otherwise has no way down without an app switch: [BlockReason.FOCUS] has a session
     * end time the service can wait on, but a schedule window simply stops covering the current
     * minute, and a limit can be lifted by earned time. This fires on the first tick after the
     * reason clears, so the block lifts within a poll interval.
     */
    val blockReleased: SharedFlow<String> = _blockReleased.asSharedFlow()

    private val _foregroundApp = MutableStateFlow<String?>(null)

    /**
     * The app currently in front, republished from the monitor so other components can observe it
     * without starting a second poller (the monitor's flow is cold — collecting it twice would poll
     * twice).
     */
    val foregroundApp: StateFlow<String?> = _foregroundApp.asStateFlow()

    /**
     * Collects foreground ticks until cancelled, crediting the time *between* ticks to whichever
     * app was in front for that interval. Runs inside [UsageMonitorService].
     */
    suspend fun run() {
        var previousPackage: String? = null
        var previousTick = now()
        // The app we've already signalled a breach for, so the overlay isn't re-triggered every
        // second while the user sits on a blocked app.
        var signalledFor: String? = null
        // Apps already warned about. A set, not a single slot: two apps can each be near their
        // limit, and warning about one must not re-arm the other.
        val warnedFor = mutableSetOf<String>()

        monitor.foregroundApp().collect { currentPackage ->
            _foregroundApp.value = currentPackage
            val tick = now()
            val elapsed = accrualMillis(tick - previousTick, MAX_TICK.inWholeMilliseconds)
            previousTick = tick

            // Crediting and enforcing are keyed on different apps on purpose. The elapsed interval
            // belongs to whoever was in front *for* it; the block belongs to whoever is in front
            // *now*. Evaluating against the previous package too cost a whole poll interval on the
            // way back into a blocked app — it only became `previousPackage` on the following tick.
            val attributedTo = previousPackage
            if (attributedTo != null && elapsed > 0 && usageRepository.isMonitoredAndEnabled(attributedTo)) {
                usageRepository.addUsage(attributedTo, elapsed.milliseconds)
            }

            // Before the evaluation below, not after: a stale `signalledFor` from the last visit
            // would otherwise suppress the very re-block this ordering exists to make immediate.
            if (currentPackage != previousPackage) {
                // A different app came to the front; re-arm so returning to a blocked app re-signals.
                signalledFor = null
            }
            previousPackage = currentPackage

            if (currentPackage != null && usageRepository.isMonitoredAndEnabled(currentPackage)) {
                // Three reasons to block, in descending precedence: a focus session the user
                // started, a schedule window they set, then the daily budget. The order decides
                // which overlay they see when several apply at once, and it runs from most
                // deliberate to least — a focus block is a promise they just made to themselves,
                // and both of the first two are escape-less, so offering the camera because the
                // budget also happened to run out would be a lie.
                val focusActive = focusSession.isActive()
                val window = if (focusActive) null else scheduleBlock(currentPackage)
                val overLimit = usageRepository.isLimitReached(currentPackage)
                if (focusActive || window != null || overLimit) {
                    if (signalledFor != currentPackage) {
                        val event = when {
                            focusActive -> BlockEvent(currentPackage, BlockReason.FOCUS)
                            window != null -> BlockEvent(
                                packageName = currentPackage,
                                reason = BlockReason.SCHEDULE,
                                endsAtMinuteOfDay = window.endMinuteOfDay,
                            )
                            else -> BlockEvent(currentPackage, BlockReason.LIMIT)
                        }
                        _blockRequired.emit(event)
                        // Recorded here rather than where the overlay is raised, so the stored
                        // count is provably the same set of events as `blockRequired` — this
                        // branch already owns the once-per-breach de-dup, and the service never
                        // filters an event out. It also keeps the count testable without Android.
                        usageRepository.recordBlock(currentPackage)
                        signalledFor = currentPackage
                    }
                } else {
                    if (signalledFor == currentPackage) {
                        // Every reason has cleared — bonus minutes earned, a window ended, a focus
                        // session stopped. Allow a fresh signal later, and tell the service to take
                        // the overlay down: the user is still here, so nothing else will.
                        signalledFor = null
                        _blockReleased.emit(currentPackage)
                    }
                    maybeWarn(currentPackage, warnedFor)
                }
            }
        }
    }

    /**
     * Emits a warning the first time [packageName] drops to the configured threshold or below.
     *
     * Only ever called from the not-yet-blocked branch, so a remaining count of zero still means
     * there is time left — [UsageRepository.remainingMinutes] floors, so anything under a minute
     * reads as 0. That is the most urgent moment to warn, not one to skip.
     */
    private suspend fun maybeWarn(packageName: String, warnedFor: MutableSet<String>) {
        val threshold = warningMinutes()
        if (threshold <= WARNING_OFF) return

        val remaining = usageRepository.remainingMinutes(packageName) ?: return
        if (remaining <= threshold) {
            if (warnedFor.add(packageName)) {
                _warningRequired.emit(WarningEvent(packageName, remaining))
            }
        } else {
            // Climbed back above the line — a later approach deserves a fresh warning.
            warnedFor.remove(packageName)
        }
    }

    companion object {
        private const val EVENT_BUFFER = 8

        /** A threshold of zero means the user turned warnings off. */
        const val WARNING_OFF = 0

        /**
         * Ceiling on a single tick's credit. The poll is ~1s, so a much larger gap means the process
         * was suspended (doze, killed service) rather than the user really being in the app that
         * long — clamping keeps a stall from silently burning someone's whole budget.
         */
        private val MAX_TICK = 5.seconds
    }
}

/** Why an app is being blocked — drives which overlay copy/affordances the service shows. */
enum class BlockReason {
    /** Daily budget spent; escapable by photographing a cat (+bonus minutes). */
    LIMIT,

    /** A focus session is running; a hard block with no camera escape until the timer ends. */
    FOCUS,

    /**
     * A schedule window covers this moment; a hard block until the window ends. No camera escape:
     * earned minutes raise a *budget*, which can't answer a rule about *when*.
     */
    SCHEDULE,
}

/**
 * One "block this app now" signal from the tracker.
 *
 * [endsAtMinuteOfDay] is set only for [BlockReason.SCHEDULE], carrying the window's end so the
 * service can say what the block is until without re-reading the schedule.
 */
data class BlockEvent(
    val packageName: String,
    val reason: BlockReason,
    val endsAtMinuteOfDay: Int? = null,
)

/** One "this app is nearly out of budget" signal, carrying how many minutes are left. */
data class WarningEvent(val packageName: String, val remainingMinutes: Int)

/**
 * How much of an inter-tick gap to credit: never negative (clock skew) and never more than
 * [maxTickMillis]. Pure, so the clamping rule is unit-tested without a device.
 */
internal fun accrualMillis(elapsedMillis: Long, maxTickMillis: Long): Long =
    elapsedMillis.coerceIn(0L, maxTickMillis)
