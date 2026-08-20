package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure rule behind [UsageStatsForegroundAppMonitor]'s poll window: given the activities already
 * on screen and the transitions since, what is the user looking at — including "we've lost track".
 */
class ForegroundResolutionTest {

    private val chromeMain = ForegroundActivity("com.android.chrome", "ChromeTabbedActivity")
    private val chromeTabs = ForegroundActivity("com.android.chrome", "TabSwitcher")
    private val launcher = ForegroundActivity("com.google.android.apps.nexuslauncher", "NexusLauncherActivity")

    private fun resumed(activity: ForegroundActivity) = ForegroundTransition(activity, resumed = true)
    private fun stopped(activity: ForegroundActivity) = ForegroundTransition(activity, resumed = false)

    @Test
    fun `a quiet window leaves the stack alone`() {
        // Most windows look like this: the user is sitting still in one app.
        val stack = resolveForeground(listOf(chromeMain), emptyList())

        assertEquals("com.android.chrome", stack.foregroundPackage())
    }

    @Test
    fun `a resume goes to the top`() {
        val stack = resolveForeground(emptyList(), listOf(resumed(launcher)))

        assertEquals(listOf(launcher), stack)
    }

    @Test
    fun `an app switch leaves only the app switched to`() {
        val transitions = listOf(resumed(launcher), stopped(chromeMain))

        assertEquals(listOf(launcher), resolveForeground(listOf(chromeMain), transitions))
    }

    @Test
    fun `leaving Recents uncovers the app underneath`() {
        // The measured API-36 sequence, and the reason this is a stack at all: the app is never
        // paused on the way into Recents and never resumed on the way out, so the only usable
        // signal is the launcher stopping.
        val intoRecents = resolveForeground(listOf(chromeMain), listOf(resumed(launcher)))
        assertEquals("com.google.android.apps.nexuslauncher", intoRecents.foregroundPackage())

        val backToChrome = resolveForeground(intoRecents, listOf(stopped(launcher)))
        assertEquals("com.android.chrome", backToChrome.foregroundPackage())
    }

    @Test
    fun `moving between two screens of one app stays in that app`() {
        val transitions = listOf(resumed(chromeTabs), stopped(chromeMain))

        assertEquals("com.android.chrome", resolveForeground(listOf(chromeMain), transitions).foregroundPackage())
    }

    @Test
    fun `resuming something already tracked promotes it instead of duplicating it`() {
        val transitions = listOf(resumed(chromeTabs), resumed(chromeMain))

        assertEquals(listOf(chromeTabs, chromeMain), resolveForeground(listOf(chromeMain), transitions))
    }

    @Test
    fun `stopping the last tracked activity loses track`() {
        assertNull(resolveForeground(listOf(chromeMain), listOf(stopped(chromeMain))).foregroundPackage())
    }

    @Test
    fun `stopping something buried does not change what is on top`() {
        val stack = resolveForeground(listOf(launcher, chromeMain), listOf(stopped(launcher)))

        assertEquals(listOf(chromeMain), stack)
    }

    @Test
    fun `stopping something we never tracked changes nothing`() {
        assertEquals(listOf(chromeMain), resolveForeground(listOf(chromeMain), listOf(stopped(launcher))))
    }

    @Test
    fun `the stack is bounded when stops never arrive`() {
        // API 26-28 emit no ACTIVITY_STOPPED at all, so resumes would otherwise accumulate forever.
        val resumes = (1..40).map { resumed(ForegroundActivity("com.app$it", "Main")) }
        val stack = resolveForeground(emptyList(), resumes)

        assertEquals(16, stack.size)
        assertEquals("com.app40", stack.foregroundPackage())
    }

    @Test
    fun `an empty stack reports nothing`() {
        assertNull(emptyList<ForegroundActivity>().foregroundPackage())
    }
}
