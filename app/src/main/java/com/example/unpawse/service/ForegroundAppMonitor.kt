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
 * folds them into a stack of what is on screen, so a user sitting still in one app keeps reporting
 * that app without re-querying a wide window.
 *
 * It consumes stops as well as resumes, and the stack is what makes Recents survivable. Measured on
 * an API-36 emulator: swiping to Recents raises `RESUMED launcher` **without pausing the app**, and
 * tapping its card raises only `PAUSED launcher` + `STOPPED launcher` — the app is never resumed
 * again, because it was never not on top. A single "what resumed last" slot is therefore stuck on
 * the launcher for good; popping its stop uncovers the app that was underneath all along.
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
        var stack = emptyList<ForegroundActivity>()
        var cursor = now() - INITIAL_LOOKBACK_MILLIS

        while (true) {
            val tick = now()
            val interactive = powerManager?.isInteractive != false
            if (interactive) {
                stack = resolveForeground(stack, transitionsIn(cursor, tick))
                // Only advanced when we actually queried, so the first waking tick still covers
                // everything that happened in the dark rather than skipping past it.
                cursor = tick
            }

            // Screen off: nothing is in the foreground, so time must stop accruing — without this
            // the last app would keep "being used" all night. The stack is kept rather than
            // cleared, because waking doesn't re-resume anything: the app is still on top.
            // Callers only ever need the package; the activities are this class's bookkeeping.
            emit(if (interactive) stack.foregroundPackage() else null)
            delay(pollInterval)
        }
    }.flowOn(Dispatchers.IO)

    /** Every foreground arrival and departure in the window, oldest first. */
    @Suppress(
        // MOVE_TO_FOREGROUND == ACTIVITY_RESUMED (API 29+); same value, and the deprecated name is
        // the one that compiles against minSdk 26.
        "DEPRECATION",
        // ACTIVITY_STOPPED is API 29 but a compile-time constant, so it inlines. API 26-28 never
        // emit it, where this degrades to "whatever resumed last" — today's shipped behaviour.
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
            // ACTIVITY_PAUSED is deliberately not consumed: pausing is not leaving. Recents pauses
            // the launcher on the way *back into* the app underneath, and the app being returned to
            // was never paused at all.
            val resumed = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> true
                UsageEvents.Event.ACTIVITY_STOPPED -> false
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

/** One consumed event: an activity came to the top (`resumed`) or went away for good. */
internal data class ForegroundTransition(val activity: ForegroundActivity, val resumed: Boolean)

/** Deepest the stack is allowed to get — a stop that never arrives must not leak forever. */
private const val MAX_TRACKED_ACTIVITIES = 16

/**
 * Folds one poll window's [transitions] into [stack], the activities on screen with the topmost
 * last — the same model the activity manager itself keeps.
 *
 * A resume promotes rather than duplicates; a stop drops that activity wherever it sits, so
 * whatever was underneath resurfaces. That last part is the whole reason this is a stack: leaving
 * Recents is reported *only* as the launcher stopping, and the app the user is looking at is
 * recoverable solely by uncovering it. An empty window leaves the stack alone — the user sitting
 * still, which is most windows.
 *
 * Pure, so the rule is unit-tested without `UsageStatsManager`.
 */
internal fun resolveForeground(
    stack: List<ForegroundActivity>,
    transitions: List<ForegroundTransition>,
): List<ForegroundActivity> = transitions.fold(stack) { current, transition ->
    val remaining = current.filterNot { it == transition.activity }
    if (transition.resumed) {
        (remaining + transition.activity).takeLast(MAX_TRACKED_ACTIVITIES)
    } else {
        remaining
    }
}

/**
 * What the user is looking at, or null when we've lost track — nothing seen yet, or everything we
 * knew about has stopped. Null has to stay reachable: it is what re-arms the tracker's block.
 */
internal fun List<ForegroundActivity>.foregroundPackage(): String? = lastOrNull()?.packageName
