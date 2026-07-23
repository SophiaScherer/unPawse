package com.example.unpawse.service

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
 * be blocked — either its daily budget is spent or a [FocusSession] is running. Deliberately knows
 * nothing about *showing* a block — it only emits [blockRequired]; the overlay service consumes it.
 * Held as a singleton by the AppContainer so the UI can observe the signal while the service drives
 * [run].
 */
class UsageTracker(
    private val usageRepository: UsageRepository,
    private val monitor: ForegroundAppMonitor,
    private val now: () -> Long = System::currentTimeMillis,
    private val focusSession: FocusSession = FocusSession(),
) {
    private val _blockRequired = MutableSharedFlow<BlockEvent>(extraBufferCapacity = EVENT_BUFFER)

    /**
     * Apps that must be blocked right now, with *why* (daily limit vs. focus session). Fires once per
     * breach, not once per tick; the overlay differs by [BlockReason].
     */
    val blockRequired: SharedFlow<BlockEvent> = _blockRequired.asSharedFlow()

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

        monitor.foregroundApp().collect { currentPackage ->
            _foregroundApp.value = currentPackage
            val tick = now()
            val elapsed = accrualMillis(tick - previousTick, MAX_TICK.inWholeMilliseconds)
            previousTick = tick

            val attributedTo = previousPackage
            if (attributedTo != null && elapsed > 0 && usageRepository.isMonitoredAndEnabled(attributedTo)) {
                usageRepository.addUsage(attributedTo, elapsed.milliseconds)

                // A focus session hard-blocks every monitored app; otherwise the daily limit does.
                // Focus wins when both apply, so an over-budget app still shows the escape-less block.
                val focusActive = focusSession.isActive()
                val overLimit = usageRepository.isLimitReached(attributedTo)
                if (focusActive || overLimit) {
                    if (signalledFor != attributedTo) {
                        val reason = if (focusActive) BlockReason.FOCUS else BlockReason.LIMIT
                        _blockRequired.emit(BlockEvent(attributedTo, reason))
                        signalledFor = attributedTo
                    }
                } else if (signalledFor == attributedTo) {
                    // Back under budget (e.g. bonus minutes earned) — allow a fresh signal later.
                    signalledFor = null
                }
            }

            if (currentPackage != previousPackage) {
                // A different app came to the front; re-arm so returning to a blocked app re-signals.
                signalledFor = null
            }
            previousPackage = currentPackage
        }
    }

    companion object {
        private const val EVENT_BUFFER = 8

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
}

/** One "block this app now" signal from the tracker. */
data class BlockEvent(val packageName: String, val reason: BlockReason)

/**
 * How much of an inter-tick gap to credit: never negative (clock skew) and never more than
 * [maxTickMillis]. Pure, so the clamping rule is unit-tested without a device.
 */
internal fun accrualMillis(elapsedMillis: Long, maxTickMillis: Long): Long =
    elapsedMillis.coerceIn(0L, maxTickMillis)
