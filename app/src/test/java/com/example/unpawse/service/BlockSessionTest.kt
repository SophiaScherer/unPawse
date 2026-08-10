package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockSessionTest {

    /** Pinned clock, advanced by the tests that exercise the redemption window. */
    private var nowMillis = 1_000_000L
    private val session = BlockSession(now = { nowMillis })

    private val windowMillis = BLOCK_REDEEM_WINDOW_MINUTES * 60_000L

    @Test
    fun `starts empty`() {
        assertNull(session.armed.value)
        assertNull(session.current())
    }

    @Test
    fun `arming records the blocked app`() {
        session.start("com.ig")

        assertEquals("com.ig", session.current()?.packageName)
    }

    @Test
    fun `clearing disarms the debt`() {
        session.start("com.ig")
        session.clear()

        assertNull(session.armed.value)
    }

    @Test
    fun `a newer block replaces an unpaid one`() {
        session.start("com.ig")
        session.start("com.tiktok")

        assertEquals("com.tiktok", session.current()?.packageName)
    }

    @Test
    fun `arming stamps an id and the time`() {
        session.start("com.ig")

        val armed = session.armed.value!!
        assertEquals(nowMillis, armed.armedAtMillis)
        assertEquals(false, armed.id.isEmpty())
    }

    @Test
    fun `a newer block gets a new id`() {
        session.start("com.ig")
        val first = session.armed.value!!.id
        session.start("com.ig")

        assertNotEquals(first, session.armed.value!!.id)
    }

    @Test
    fun `a session is redeemable right up to the end of the window`() {
        session.start("com.ig")

        nowMillis += windowMillis - 1

        assertEquals("com.ig", session.current()?.packageName)
    }

    @Test
    fun `a session expires once the window elapses`() {
        session.start("com.ig")

        nowMillis += windowMillis

        assertNull(session.current())
    }

    @Test
    fun `an expired session is cleared on read`() {
        session.start("com.ig")
        nowMillis += windowMillis

        session.current()

        assertNull("the lapsed block should be dropped, not just refused", session.armed.value)
    }

    @Test
    fun `re-arming restarts the window`() {
        session.start("com.ig")
        nowMillis += windowMillis - 1
        session.start("com.ig")

        nowMillis += windowMillis - 1

        assertEquals("com.ig", session.current()?.packageName)
    }
}
