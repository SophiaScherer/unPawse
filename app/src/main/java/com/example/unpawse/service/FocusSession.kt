package com.example.unpawse.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A running "focus session": for a fixed duration, every monitored app is hard-blocked regardless of
 * its remaining daily budget (no cat-photo escape — the block lifts only when the timer ends).
 *
 * The single source of truth is [endTimeMillis] (epoch millis, null when idle). It deliberately owns
 * no clock of its own for enforcement — callers pass the current time — but takes an injectable [now]
 * so `start`/`restore`/`isActive` are unit-testable. Held as an AppContainer singleton so the
 * enforcement service and the Home UI share one instance; persistence (across process death) is wired
 * in the container against [com.example.unpawse.data.settings.SettingsRepository].
 */
class FocusSession(private val now: () -> Long = System::currentTimeMillis) {

    private val _endTimeMillis = MutableStateFlow<Long?>(null)

    /** The active session's end time in epoch millis, or null when nothing is running. */
    val endTimeMillis: StateFlow<Long?> = _endTimeMillis.asStateFlow()

    /** Starts a session lasting [durationMinutes] from now. */
    fun start(durationMinutes: Int) {
        _endTimeMillis.value = now() + durationMinutes * MILLIS_PER_MINUTE
    }

    /** Ends the session immediately. */
    fun stop() {
        _endTimeMillis.value = null
    }

    /** Re-arm from a persisted end time; an absent or already-past time counts as no session. */
    fun restore(endMillis: Long?) {
        _endTimeMillis.value = endMillis?.takeIf { it > now() }
    }

    /** Whether a session is running right now (its end time is still in the future). */
    fun isActive(): Boolean = _endTimeMillis.value?.let { now() < it } ?: false

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
