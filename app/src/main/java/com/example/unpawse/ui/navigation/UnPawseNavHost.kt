package com.example.unpawse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.unpawse.data.SampleData
import com.example.unpawse.service.OverlayPermission
import com.example.unpawse.service.UsageAccess
import com.example.unpawse.ui.apppicker.AppPickerRoute
import com.example.unpawse.ui.block.BlockOverlayScreen
import com.example.unpawse.ui.camera.CameraRoute
import com.example.unpawse.ui.gallery.GalleryRoute
import com.example.unpawse.ui.home.HomeRoute
import com.example.unpawse.ui.settings.SettingsScreen
import com.example.unpawse.ui.settings.SettingsViewModel
import com.example.unpawse.ui.stats.StatsRoute

/**
 * Central navigation graph. Every destination now renders from a real ViewModel via its `XxxRoute`,
 * except the Block Overlay — which is only reachable here as a design/debug entry (in production the
 * service draws it over the offending app), so it still uses [SampleData].
 *
 * [darkMode] / [onToggleDarkMode] are threaded down from [com.example.unpawse.UnPawseApp] so the
 * Settings switch actually flips the app theme.
 */
@Composable
fun UnPawseNavHost(
    navController: NavHostController,
    darkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeRoute(
                // Design/debug entry only. The real trigger is UsageMonitorService, which draws the
                // block as a system overlay over the offending app; this in-app route just lets the
                // screen be reviewed without burning through a real limit.
                onPauseProtection = { navController.navigate(Routes.BLOCK) },
                // Both quick actions concern the same editor — the App Picker owns app selection and
                // per-app daily limits.
                onEditLimits = { navController.navigate(Routes.APP_PICKER) },
                onManageApps = { navController.navigate(Routes.APP_PICKER) },
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
            StatsRoute()
        }

        composable(Routes.GALLERY) {
            GalleryRoute()
        }

        composable(Routes.SETTINGS) {
            // Persisted settings come from the SettingsViewModel; dark mode is the exception — it
            // lives in UnPawseApp so it can drive the whole theme, and is overlaid here for display.
            val context = LocalContext.current
            val settingsViewModel: SettingsViewModel =
                viewModel(factory = SettingsViewModel.factory(context))
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            // The permission chips read state that only changes while the user is away in system
            // Settings, so re-read it on the way back. Starting the monitor is not this screen's
            // job — UnPawseApp does it app-wide on every resume.
            LifecycleResumeEffect(Unit) {
                settingsViewModel.refreshPermissions()
                onPauseOrDispose { }
            }

            SettingsScreen(
                state = settingsState.copy(darkMode = darkMode),
                onBack = { navController.navigateToTab(TopLevelDestination.HOME) },
                onToggleDarkMode = onToggleDarkMode,
                onToggleLivePhoto = settingsViewModel::setRequireLivePhoto,
                onToggleDailySummary = settingsViewModel::setDailySummary,
                onSensitivityChange = settingsViewModel::setSensitivity,
                onRowClick = { rowId ->
                    // Only these rows lead anywhere so far; the rest stay inert.
                    when (rowId) {
                        SettingsRowIds.APP_LIMITS -> navController.navigate(Routes.APP_PICKER)
                        SettingsRowIds.USAGE_ACCESS -> context.startActivity(UsageAccess.settingsIntent(context))
                        SettingsRowIds.OVERLAY_ACCESS ->
                            context.startActivity(OverlayPermission.settingsIntent(context))
                    }
                },
            )
        }

        composable(Routes.APP_PICKER) {
            AppPickerRoute(onBack = { navController.popBackStack() })
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
