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
 * Turns [ForegroundAppMonitor] ticks into accrued screen time and signals when a monitored app has
 * spent its daily budget. Deliberately knows nothing about *showing* a block — it only emits
 * [limitReached]; Phase 5's overlay consumes it. Held as a singleton by the AppContainer so the UI
 * can observe the signal while the service drives [run].
 */
class UsageTracker(
    private val usageRepository: UsageRepository,
    private val monitor: ForegroundAppMonitor,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _limitReached = MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER)

    /** Package names that have just hit their limit. Fires once per breach, not once per tick. */
    val limitReached: SharedFlow<String> = _limitReached.asSharedFlow()

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

                if (usageRepository.isLimitReached(attributedTo)) {
                    if (signalledFor != attributedTo) {
                        _limitReached.emit(attributedTo)
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

/**
 * How much of an inter-tick gap to credit: never negative (clock skew) and never more than
 * [maxTickMillis]. Pure, so the clamping rule is unit-tested without a device.
 */
internal fun accrualMillis(elapsedMillis: Long, maxTickMillis: Long): Long =
    elapsedMillis.coerceIn(0L, maxTickMillis)
