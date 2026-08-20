package com.example.unpawse.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reports which app is in the foreground. An interface so the rest of the app never depends on
 * *how* we detect it — see AGENTS.md "Resolved": we poll [UsageStatsManager] rather than run an
 * `AccessibilityService`, and an Accessibility implementation could be slotted in here later.
 */
interface ForegroundAppMonitor {
    /**
     * Emits the current foreground package about once per poll interval, or `null` when it isn't
     * known — the screen is off, nothing has been seen yet, or the app we were tracking went away
     * and nothing replaced it. Emits on every tick — including repeats — so callers can measure
     * elapsed time between ticks.
     */
    fun foregroundApp(): Flow<String?>
}

/**
 * [UsageStatsManager]-backed monitor. Each tick queries only the events *since the last tick* and
 * folds them over what was in front before, so a user sitting still in one app keeps reporting that
 * app without re-querying a wide window.
 *
 * It consumes departures as well as arrivals. Reporting the last resumed package forever made the
 * block permanently escapable: returning from Recents raises no fresh resume, so the monitor stayed
 * pointed at the launcher, usage stopped accruing and no block ever fired again.
 *
 * Requires the `PACKAGE_USAGE_STATS` app-op (see [UsageAccess]); without it `queryEvents` simply
 * returns nothing and this emits `null` forever rather than crashing.
 */
class UsageStatsForegroundAppMonitor(
    context: Context,
    private val pollInterval: Duration = POLL_INTERVAL,
    private val now: () -> Long = System::currentTimeMillis,
) : ForegroundAppMonitor {

    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    override fun foregroundApp(): Flow<String?> = flow {
        var lastKnown: ForegroundActivity? = null
        var cursor = now() - INITIAL_LOOKBACK_MILLIS

        while (true) {
            val tick = now()
            lastKnown = if (powerManager?.isInteractive != false) {
                resolveForeground(lastKnown, transitionsIn(cursor, tick))
            } else {
                // Screen off: nothing is in the foreground, so time must stop accruing. Without
                // this the last app would keep "being used" all night.
                null
            }
            cursor = tick

            // Callers only ever need the package; the activity is this class's bookkeeping.
            emit(lastKnown?.packageName)
            delay(pollInterval)
        }
    }.flowOn(Dispatchers.IO)

    /** Every foreground arrival and departure in the window, oldest first. */
    @Suppress(
        // MOVE_TO_FOREGROUND/BACKGROUND == ACTIVITY_RESUMED/PAUSED (API 29+); same values, and the
        // deprecated names are the ones that compile against minSdk 26.
        "DEPRECATION",
        // ACTIVITY_STOPPED is API 29 but a compile-time constant, so it inlines; older platforms
        // simply never emit that type and the pause alone carries them.
        "InlinedApi",
    )
    private fun transitionsIn(beginMillis: Long, endMillis: Long): List<ForegroundTransition> {
        val manager = usageStatsManager ?: return emptyList()
        val events = manager.queryEvents(beginMillis, endMillis)
        // One instance, refilled per event by the platform — copy what we keep out of it.
        val event = UsageEvents.Event()
        val transitions = mutableListOf<ForegroundTransition>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val resumed = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> true
                UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_STOPPED -> false
                else -> continue
            }
            val packageName = event.packageName ?: continue
            transitions += ForegroundTransition(
                activity = ForegroundActivity(packageName, event.className),
                resumed = resumed,
            )
        }
        return transitions
    }

    companion object {
        /** Detection latency ceiling. 1s is the usual trade-off between responsiveness and battery. */
        val POLL_INTERVAL = 1.seconds

        /** On the first tick, look back far enough to learn what's already on screen. */
        private const val INITIAL_LOOKBACK_MILLIS = 60_000L
    }
}

/**
 * What is in front, as the platform reports it: a package *and* the activity within it.
 *
 * The class name is load-bearing — moving between two screens of one app emits a pause for the old
 * activity *after* the resume of the new one, so tracking the package alone would read that trailing
 * pause as "the app went away" while the user is still sitting in it.
 */
internal data class ForegroundActivity(val packageName: String, val className: String?)

/** One foreground transition, with the platform's event constants already stripped off. */
internal data class ForegroundTransition(val activity: ForegroundActivity, val resumed: Boolean)

/**
 * Folds one poll window's [transitions] over what was in front at the start of it.
 *
 * A pause/stop only clears the state when it names the activity we're tracking, so the usual app
 * switch (A paused, B resumed, A stopped) lands on B rather than on nothing. An empty window leaves
 * [seed] alone — that's the user sitting still, which is most windows. Returning null is the point
 * of the whole function: "the app we were tracking went away and nothing replaced it" has to be
 * distinguishable from "nothing happened", or a block can never re-fire.
 *
 * Pure, so the rule is unit-tested without `UsageStatsManager`.
 */
internal fun resolveForeground(
    seed: ForegroundActivity?,
    transitions: List<ForegroundTransition>,
): ForegroundActivity? = transitions.fold(seed) { current, transition ->
    when {
        transition.resumed -> transition.activity
        transition.activity == current -> null
        else -> current
    }
}
