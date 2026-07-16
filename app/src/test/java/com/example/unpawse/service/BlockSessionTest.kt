package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockSessionTest {

    private val session = BlockSession()

    @Test
    fun `starts empty`() {
        assertNull(session.blockedPackage.value)
    }

    @Test
    fun `arming records the blocked app`() {
        session.start("com.ig")

        assertEquals("com.ig", session.blockedPackage.value)
    }

    @Test
    fun `clearing disarms the debt`() {
        session.start("com.ig")
        session.clear()

        assertNull(session.blockedPackage.value)
    }

    @Test
    fun `a newer block replaces an unpaid one`() {
        session.start("com.ig")
        session.start("com.tiktok")

        assertEquals("com.tiktok", session.blockedPackage.value)
    }
}
