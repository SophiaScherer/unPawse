package com.example.unpawse.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * App routes as plain string constants. We deliberately avoid kotlinx-serialization type-safe
 * routes to sidestep adding the serialization compiler plugin (a second version-matching risk
 * on top of the Compose plugin).
 */
object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val STATS = "stats"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val BLOCK = "block"

    /** Settings sub-screen: choose monitored apps and their daily limits. */
    const val APP_PICKER = "app_picker"

    /** Settings sub-screen: recurring windows during which apps are blocked outright. */
    const val SCHEDULES = "schedules"

    /** Settings sub-screen: the in-app privacy policy. */
    const val PRIVACY_POLICY = "privacy_policy"

    /** Settings sub-screen: cat-photo library size, retention window and bulk delete. */
    const val PHOTO_STORAGE = "photo_storage"
}

/**
 * Every row id `SettingsScreen.onRowClick` can emit. `SettingsRoute` maps them to a destination or
 * a system intent; ids marked "not yet handled" are rows whose behaviour a later phase supplies.
 */
object SettingsRowIds {
    const val APP_LIMITS = "app_limits"

    /** Opens [Routes.SCHEDULES], where blocking windows are created and edited. */
    const val SCHEDULES = "schedules_row"

    /** Opens system Settings — usage access is an app-op, not a runtime permission. */
    const val USAGE_ACCESS = "usage_access"

    /** Opens system Settings — "display over other apps" is likewise not a runtime permission. */
    const val OVERLAY_ACCESS = "overlay_access"

    /** Requests POST_NOTIFICATIONS, or opens system Settings once it can no longer be asked for. */
    const val NOTIFICATION_ACCESS = "notification_access"

    const val MANAGE_PHOTOS = "manage_photos"
    const val EXPORT = "export"

    /** Opens the in-app policy at [Routes.PRIVACY_POLICY]. */
    const val PRIVACY_POLICY = "privacy_policy_row"
}

/**
 * The five bottom-navigation destinations, in display order. The Block Overlay is intentionally
 * NOT here — it is a full-screen route without the bottom bar.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(
        route = Routes.HOME,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    CAMERA(
        route = Routes.CAMERA,
        label = "Camera",
        selectedIcon = Icons.Filled.PhotoCamera,
        unselectedIcon = Icons.Outlined.PhotoCamera,
    ),
    STATS(
        route = Routes.STATS,
        label = "Stats",
        selectedIcon = Icons.Filled.Equalizer,
        unselectedIcon = Icons.Outlined.Equalizer,
    ),
    GALLERY(
        route = Routes.GALLERY,
        label = "Gallery",
        // Material Icons has no AutoAwesomeMosaic; GridView is the closest match to the mockup glyph.
        selectedIcon = Icons.Outlined.GridView,
        unselectedIcon = Icons.Outlined.GridView,
    ),
    SETTINGS(
        route = Routes.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}
