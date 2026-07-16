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
}

/** Row ids emitted by `SettingsScreen.onRowClick`; only the wired ones are listed. */
object SettingsRowIds {
    const val APP_LIMITS = "app_limits"

    /** Opens system Settings — usage access is an app-op, not a runtime permission. */
    const val USAGE_ACCESS = "usage_access"

    /** Opens system Settings — "display over other apps" is likewise not a runtime permission. */
    const val OVERLAY_ACCESS = "overlay_access"
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
