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
     * Emits the current foreground package about once per poll interval, or `null` when nothing is
     * in front (screen off) or it isn't known yet. Emits on every tick — including repeats — so
     * callers can measure elapsed time between ticks.
     */
    fun foregroundApp(): Flow<String?>
}

/**
 * [UsageStatsManager]-backed monitor. Each tick queries only the events *since the last tick* and
 * remembers the most recent "resumed" package, so a user sitting still in one app keeps reporting
 * that app without re-querying a wide window.
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
        var lastKnown: String? = null
        var cursor = now() - INITIAL_LOOKBACK_MILLIS

        while (true) {
            val tick = now()
            if (powerManager?.isInteractive != false) {
                latestResumedPackage(cursor, tick)?.let { lastKnown = it }
            } else {
                // Screen off: nothing is in the foreground, so time must stop accruing. Without
                // this the last app would keep "being used" all night.
                lastKnown = null
            }
            cursor = tick

            emit(lastKnown)
            delay(pollInterval)
        }
    }.flowOn(Dispatchers.IO)

    /** The package of the most recent foreground transition in the window, or null if there was none. */
    @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND == ACTIVITY_RESUMED (API 29+); same value, works on minSdk 26.
    private fun latestResumedPackage(beginMillis: Long, endMillis: Long): String? {
        val manager = usageStatsManager ?: return null
        val events = manager.queryEvents(beginMillis, endMillis)
        val event = UsageEvents.Event()
        var latest: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latest = event.packageName
            }
        }
        return latest
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
