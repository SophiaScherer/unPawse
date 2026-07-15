package com.example.unpawse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.unpawse.data.SampleData
import com.example.unpawse.ui.block.BlockOverlayScreen
import com.example.unpawse.ui.camera.CameraRoute
import com.example.unpawse.ui.gallery.GalleryRoute
import com.example.unpawse.ui.home.HomeScreen
import com.example.unpawse.ui.settings.SettingsScreen
import com.example.unpawse.ui.settings.SettingsViewModel
import com.example.unpawse.ui.stats.StatsScreen

/**
 * Central navigation graph. Each destination is fed its UI state (from [SampleData]) plus the
 * navigation callbacks here — this is the seam where a ViewModel replaces `SampleData.xxxState`
 * later without touching the screen composables.
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
            HomeScreen(
                state = SampleData.homeState,
                // Temporary review hook (per plan): the Pause Protection card opens the Block
                // Overlay. Rewire to the real limit-reached trigger once backend logic exists.
                onPauseProtection = { navController.navigate(Routes.BLOCK) },
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
            StatsScreen(state = SampleData.statsState)
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

            SettingsScreen(
                state = settingsState.copy(darkMode = darkMode),
                onBack = { navController.navigateToTab(TopLevelDestination.HOME) },
                onToggleDarkMode = onToggleDarkMode,
                onToggleLivePhoto = settingsViewModel::setRequireLivePhoto,
                onToggleDailySummary = settingsViewModel::setDailySummary,
                onSensitivityChange = settingsViewModel::setSensitivity,
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
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
