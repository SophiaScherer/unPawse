package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure rule behind [UsageStatsForegroundAppMonitor]'s poll window: given what was in front and
 * the transitions since, what is in front now — including "nothing we know of".
 */
class ForegroundResolutionTest {

    private val chromeMain = ForegroundActivity("com.android.chrome", "Main")
    private val chromeTabs = ForegroundActivity("com.android.chrome", "Tabs")
    private val launcher = ForegroundActivity("com.android.launcher3", "Launcher")

    private fun resumed(activity: ForegroundActivity) = ForegroundTransition(activity, resumed = true)
    private fun left(activity: ForegroundActivity) = ForegroundTransition(activity, resumed = false)

    @Test
    fun `a quiet window leaves the foreground unchanged`() {
        // Most windows look like this: the user is sitting still in one app.
        assertEquals(chromeMain, resolveForeground(chromeMain, emptyList()))
    }

    @Test
    fun `a resume takes the foreground`() {
        assertEquals(launcher, resolveForeground(null, listOf(resumed(launcher))))
    }

    @Test
    fun `the trailing stop of the app we just left is ignored`() {
        // The usual switch order — A paused, B resumed, A stopped — must land on B, not nothing.
        val transitions = listOf(left(chromeMain), resumed(launcher), left(chromeMain))
        assertEquals(launcher, resolveForeground(chromeMain, transitions))
    }

    @Test
    fun `moving between two screens of one app stays in that app`() {
        val transitions = listOf(left(chromeMain), resumed(chromeTabs), left(chromeMain))
        assertEquals(chromeTabs, resolveForeground(chromeMain, transitions))
    }

    @Test
    fun `pausing the tracked app with nothing after it is unknown`() {
        assertNull(resolveForeground(chromeMain, listOf(left(chromeMain))))
    }

    @Test
    fun `a resume followed by its own pause is unknown, not the resumed app`() {
        // Order decides, not presence: a "latest resume wins" rule would answer Chrome here.
        assertNull(resolveForeground(null, listOf(resumed(chromeMain), left(chromeMain))))
    }

    @Test
    fun `a stop for something we were not tracking changes nothing`() {
        assertEquals(chromeMain, resolveForeground(chromeMain, listOf(left(launcher))))
    }

    @Test
    fun `a pause while the foreground is already unknown stays unknown`() {
        assertNull(resolveForeground(null, listOf(left(chromeMain))))
    }

    @Test
    fun `the same package in a different activity does not clear the foreground`() {
        assertEquals(chromeTabs, resolveForeground(chromeTabs, listOf(left(chromeMain))))
    }
}
