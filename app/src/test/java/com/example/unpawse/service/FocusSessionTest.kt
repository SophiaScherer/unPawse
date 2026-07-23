package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure timing behaviour of [FocusSession] with an injected clock — no device, no delays. */
class FocusSessionTest {

    private var nowMillis = 1_000_000L
    private val session = FocusSession(now = { nowMillis })

    @Test
    fun `a fresh session is inactive`() {
        assertFalse(session.isActive())
        assertNull(session.endTimeMillis.value)
    }

    @Test
    fun `start sets an end time in the future and is active`() {
        session.start(durationMinutes = 30)

        assertEquals(nowMillis + 30 * 60_000L, session.endTimeMillis.value)
        assertTrue(session.isActive())
    }

    @Test
    fun `a session expires once its end time passes`() {
        session.start(durationMinutes = 1)
        assertTrue(session.isActive())

        nowMillis += 61_000L
        assertFalse(session.isActive())
    }

    @Test
    fun `stop ends the session immediately`() {
        session.start(durationMinutes = 30)
        session.stop()

        assertFalse(session.isActive())
        assertNull(session.endTimeMillis.value)
    }

    @Test
    fun `restore re-arms a future end time`() {
        session.restore(nowMillis + 10 * 60_000L)

        assertTrue(session.isActive())
    }

    @Test
    fun `restore ignores a past or absent end time`() {
        session.restore(nowMillis - 1)
        assertFalse(session.isActive())

        session.restore(null)
        assertFalse(session.isActive())
    }
}
