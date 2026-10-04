package com.example.unpawse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.unpawse.data.SampleData
import com.example.unpawse.ui.about.PrivacyPolicyScreen
import com.example.unpawse.ui.apppicker.AppPickerRoute
import com.example.unpawse.ui.block.BlockOverlayScreen
import com.example.unpawse.ui.camera.CameraRoute
import com.example.unpawse.ui.gallery.CaptureViewerRoute
import com.example.unpawse.ui.gallery.GalleryRoute
import com.example.unpawse.ui.home.HomeRoute
import com.example.unpawse.ui.onboarding.OnboardingRoute
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
    /**
     * [Routes.ONBOARDING] on a fresh install, [Routes.HOME] afterwards. Fixed for the life of the
     * composition — see the note in `UnPawseApp`, which latches it.
     */
    startDestination: String = Routes.HOME,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeRoute(
                // "Edit Limits" opens the App Picker, which owns app selection and per-app limits.
                onEditLimits = { navController.navigate(Routes.APP_PICKER) },
                // The two permission rows that fix this live in Settings and already deep-link out
                // to the system screens, so Home hands off rather than duplicating that.
                onFixProtection = { navController.navigateToTab(TopLevelDestination.SETTINGS) },
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
            GalleryRoute(
                onOpenViewer = { id -> navController.navigate(Routes.captureViewer(id)) },
            )
        }

        composable(Routes.CAPTURE_VIEWER) { entry ->
            // The viewer shares the Gallery's ViewModel so it pages over the same filtered list;
            // null once Gallery has left the stack, which CaptureViewerRoute falls back from.
            val galleryOwner = remember(entry) {
                runCatching { navController.getBackStackEntry(Routes.GALLERY) }.getOrNull()
            }
            CaptureViewerRoute(
                captureId = entry.arguments?.getString(Routes.ARG_CAPTURE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                galleryOwner = galleryOwner,
            )
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

        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                onFinished = {
                    // Decided by the back stack, not by the start destination: that is latched once
                    // per composition, so a replay finished in the same session as the first run
                    // would otherwise push a second Home over Settings.
                    if (navController.previousBackStackEntry == null) {
                        // Nothing underneath (first run): Home replaces the tour outright, so a back
                        // press from Home leaves the app rather than replaying it.
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    } else {
                        // Pushed over Settings (replay) or Home (after a reset); return there.
                        navController.popBackStack()
                    }
                },
            )
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
