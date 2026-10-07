package com.example.unpawse.ui.navigation

import androidx.navigation.NavHostController

/**
 * The bottom-bar tab a route belongs to, or null for a takeover that sits outside every tab (the
 * tour). Selection, reselection and cross-tab jumps all read this, so a sub-screen is always shown
 * inside, and highlighted as, the one tab that owns it.
 */
fun owningTab(route: String?): TopLevelDestination? = when (route) {
    Routes.HOME -> TopLevelDestination.HOME
    Routes.CAMERA -> TopLevelDestination.CAMERA
    Routes.STATS -> TopLevelDestination.STATS
    Routes.GALLERY, Routes.CAPTURE_VIEWER -> TopLevelDestination.GALLERY
    Routes.SETTINGS,
    Routes.APP_PICKER,
    Routes.SCHEDULES,
    Routes.PHOTO_STORAGE,
    Routes.PRIVACY_POLICY,
    -> TopLevelDestination.SETTINGS
    else -> null
}

/** The tab owning whatever is on screen now. */
val NavHostController.currentTab: TopLevelDestination?
    get() = owningTab(currentDestination?.route)

/**
 * Switch to [tab] with standard bottom-nav semantics (single instance, saved state), or return to
 * its root when it is already the current tab.
 */
fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    if (currentTab == tab && hasOnBackStack(tab.route)) {
        popBackStack(tab.route, inclusive = false)
        return
    }
    navigate(tab.route) {
        // Home by name, not `graph.startDestinationId`: on a fresh install the graph starts at
        // Onboarding, which completion pops off for good — leaving `popUpTo` aimed at a destination
        // that is no longer on the back stack, so every tab tap would stack another entry.
        popUpTo(Routes.HOME) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Open [route] inside the tab that owns it. A sub-screen pushed onto another tab's stack would
 * highlight the wrong tab, and once pushed onto Home it was saved as Home's state, so every later
 * Home tap restored it instead of Home.
 */
fun NavHostController.navigateWithinTab(route: String) {
    val owner = owningTab(route)
    if (owner != null && owner != currentTab) {
        navigateToTab(owner)
        // A cross-tab jump starts from the owner's root, not on top of whatever it last showed.
        popBackStack(owner.route, inclusive = false)
    }
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.hasOnBackStack(route: String): Boolean =
    runCatching { getBackStackEntry(route) }.isSuccess
