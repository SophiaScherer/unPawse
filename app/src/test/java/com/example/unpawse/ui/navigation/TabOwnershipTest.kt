package com.example.unpawse.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabOwnershipTest {

    @Test
    fun `every tab owns its own root`() {
        TopLevelDestination.entries.forEach { tab ->
            assertEquals(tab, owningTab(tab.route))
        }
    }

    /** Home's "Edit limits" and Stats' "Details" both open it, and neither may keep a copy. */
    @Test
    fun `the app picker belongs to Settings`() {
        assertEquals(TopLevelDestination.SETTINGS, owningTab(Routes.APP_PICKER))
    }

    @Test
    fun `every other Settings sub-screen belongs to Settings`() {
        listOf(Routes.SCHEDULES, Routes.PHOTO_STORAGE, Routes.PRIVACY_POLICY).forEach { route ->
            assertEquals(route, TopLevelDestination.SETTINGS, owningTab(route))
        }
    }

    @Test
    fun `the photo viewer belongs to Gallery`() {
        assertEquals(TopLevelDestination.GALLERY, owningTab(Routes.CAPTURE_VIEWER))
    }

    @Test
    fun `the tour sits outside every tab`() {
        assertNull(owningTab(Routes.ONBOARDING))
    }

    /** A new sub-screen without an owner would highlight no tab and could land on any stack. */
    @Test
    fun `every route but the takeovers has an owner`() {
        val takeovers = setOf(Routes.ONBOARDING, Routes.BLOCK)
        val routes = Routes::class.java.declaredFields
            .filter { it.type == String::class.java && it.name != "ARG_CAPTURE_ID" }
            .map { it.get(null) as String }
        assertTrue(routes.isNotEmpty())
        routes.filterNot { it in takeovers }.forEach { route ->
            assertNotNull("$route has no owning tab", owningTab(route))
        }
    }

    @Test
    fun `no destination yet and an unknown route have no owner`() {
        assertNull(owningTab(null))
        assertNull(owningTab("nowhere"))
    }
}
