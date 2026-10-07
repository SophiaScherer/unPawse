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

/**
 * Whether [route] shows the bottom bar. Takeovers hide it, and so does the camera in landscape,
 * where the bar ate about a quarter of the viewfinder; the camera's own X and system back still
 * leave it.
 */
fun showsBottomBar(route: String?, landscape: Boolean): Boolean = when (route) {
    Routes.CAPTURE_VIEWER, Routes.ONBOARDING -> false
    Routes.CAMERA -> !landscape
    else -> true
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

/**
 * Open a route a launch or new intent asked for. An open tour steps aside first, as it would never
 * have started under a cold deep link (see [startDestinationFor]); its flag is untouched, so it
 * returns on the next plain launch.
 */
fun NavHostController.openDeepLink(route: String) {
    if (currentDestination?.route == Routes.ONBOARDING) leaveOnboarding()
    val tab = TopLevelDestination.entries.firstOrNull { it.route == route }
    if (tab != null) navigateToTab(tab) else navigateWithinTab(route)
}

/**
 * Leave the tour. Decided by the back stack, not the start destination: that is latched once per
 * composition, so a replay finished in the same session as the first run would otherwise push a
 * second Home over Settings.
 */
fun NavHostController.leaveOnboarding() {
    if (previousBackStackEntry == null) {
        // Nothing underneath (first run): Home replaces the tour outright, so a back press from
        // Home leaves the app rather than replaying it.
        navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
    } else {
        // Pushed over Settings (replay) or Home (after a reset); return there.
        popBackStack()
    }
}

private fun NavHostController.hasOnBackStack(route: String): Boolean =
    runCatching { getBackStackEntry(route) }.isSuccess
