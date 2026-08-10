package com.example.unpawse.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Bonus time a single verified cat buys back. Matches the mockup's "+15m" gallery chip. */
const val BONUS_MINUTES_PER_CAT = 15

/**
 * How long an armed block stays redeemable. Long enough to walk to a cat in the next room, short
 * enough that a block the user abandoned this morning can't be cashed in tonight.
 */
const val BLOCK_REDEEM_WINDOW_MINUTES = 5

/**
 * One armed block: which app owes a cat, when the debt was raised, and an [id] that makes *this*
 * block distinguishable from the next one for the same app.
 */
data class ArmedBlock(
    val id: String,
    val packageName: String,
    val armedAtMillis: Long,
)

/**
 * The app the user is currently trying to earn time back for — the thread connecting the block to
 * the camera.
 *
 * It deliberately outlives the overlay: "Open Camera" takes the overlay down (it would otherwise
 * cover the viewfinder) but the debt is still owed, so the session stays armed until a cat is
 * captured, the user walks away via "Exit App", or [BLOCK_REDEEM_WINDOW_MINUTES] elapses. Held as an
 * AppContainer singleton because the service arms it and the camera ViewModel settles it.
 *
 * The window is the point: without it an armed session lives forever, because
 * `UsageMonitorService.dismissBlockWhenUserLeaves` hides the overlay without clearing the debt. A
 * cat photographed hours later from the Camera *tab* would then silently pay off a block the user
 * had long since walked away from.
 *
 * **Not persisted, unlike [FocusSession].** Five minutes that span a process death aren't worth
 * restoring: the overlay is gone too, and returning to the blocked app re-arms within one tracker
 * tick (~1s). A DataStore key plus a container collector to preserve state whose whole value is
 * being short-lived would cost more than it buys.
 *
 * Takes an injectable [now] so the window is unit-testable, matching [FocusSession].
 */
class BlockSession(private val now: () -> Long = System::currentTimeMillis) {

    private val _armed = MutableStateFlow<ArmedBlock?>(null)

    /**
     * The armed block, if any — including one whose window has already lapsed. Callers deciding
     * whether to *pay out* must use [current], which applies the window.
     */
    val armed: StateFlow<ArmedBlock?> = _armed.asStateFlow()

    /** Arms a fresh debt for [packageName], replacing any unpaid one (with a new id and window). */
    fun start(packageName: String) {
        _armed.value = ArmedBlock(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            armedAtMillis = now(),
        )
    }

    fun clear() {
        _armed.value = null
    }

    /**
     * The block a capture may redeem right now, or null when nothing is armed or the window lapsed.
     *
     * Expiry is lazy and self-healing — a lapsed session is cleared as it is read — rather than
     * timer-driven: nothing needs to *react* to the moment a window closes, and a coroutine per
     * block would be state to leak.
     */
    fun current(): ArmedBlock? {
        val armed = _armed.value ?: return null
        if (now() - armed.armedAtMillis >= REDEEM_WINDOW_MILLIS) {
            _armed.value = null
            return null
        }
        return armed
    }

    private companion object {
        const val REDEEM_WINDOW_MILLIS = BLOCK_REDEEM_WINDOW_MINUTES * 60_000L
    }
}
