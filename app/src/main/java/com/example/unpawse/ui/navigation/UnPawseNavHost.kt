package com.example.unpawse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.unpawse.data.SampleData
import com.example.unpawse.ui.about.PrivacyPolicyScreen
import com.example.unpawse.ui.apppicker.AppPickerRoute
import com.example.unpawse.ui.block.BlockOverlayScreen
import com.example.unpawse.ui.camera.CameraRoute
import com.example.unpawse.ui.gallery.GalleryRoute
import com.example.unpawse.ui.home.HomeRoute
import com.example.unpawse.ui.photos.PhotoStorageRoute
import com.example.unpawse.ui.schedules.SchedulesRoute
import com.example.unpawse.ui.settings.SettingsRoute
import com.example.unpawse.ui.stats.StatsRoute
import com.example.unpawse.ui.theme.ThemeMode

/**
 * Central navigation graph. Every destination renders from a real ViewModel via its `XxxRoute`,
 * except the Block Overlay — which is only reachable here as a design/debug entry (in production the
 * service draws it over the offending app), so it still uses [SampleData].
 *
 * [themeMode] / [onThemeModeChange] are threaded down from [com.example.unpawse.UnPawseApp] so the
 * Settings appearance picker actually flips the app theme.
 */
@Composable
fun UnPawseNavHost(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeRoute(
                // "Edit Limits" opens the App Picker, which owns app selection and per-app limits.
                onEditLimits = { navController.navigate(Routes.APP_PICKER) },
            )
        }

        composable(Routes.CAMERA) {
            CameraRoute(
                onClose = { navController.navigateToTab(TopLevelDestination.HOME) },
                onOpenGallery = { navController.navigateToTab(TopLevelDestination.GALLERY) },
                onOpenSettings = { navController.navigateToTab(TopLevelDestination.SETTINGS) },
            )
        }

        composable(Routes.STATS) {
            StatsRoute(
                // The breakdown groups by category and the App Picker is where categories and limits
                // are set, so "Details" lands on the screen that can act on what the donut reports.
                onDetails = { navController.navigate(Routes.APP_PICKER) },
            )
        }

        composable(Routes.GALLERY) {
            GalleryRoute()
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onBack = { navController.navigateToTab(TopLevelDestination.HOME) },
                onNavigate = navController::navigate,
            )
        }

        composable(Routes.APP_PICKER) {
            AppPickerRoute(
                onBack = { navController.popBackStack() },
                onOpenSchedules = { navController.navigate(Routes.SCHEDULES) },
            )
        }

        composable(Routes.SCHEDULES) {
            SchedulesRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.PHOTO_STORAGE) {
            PhotoStorageRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.PRIVACY_POLICY) {
            // Static copy — no ViewModel, so no Route wrapper either.
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BLOCK) {
            BlockOverlayScreen(
                state = SampleData.blockState,
                onOpenCamera = { navController.navigateToTab(TopLevelDestination.CAMERA) },
                onExit = { navController.popBackStack() },
            )
        }
    }
}

/** Navigate to a top-level tab with standard bottom-nav semantics (single instance, saved state). */
fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
