package com.example.unpawse.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/**
 * Central navigation graph. Each destination is fed its UI state + navigation callbacks here — this
 * is the seam where `SampleData.xxxState` gets swapped for a ViewModel later without touching the
 * screen composables.
 *
 * Screens are placeholders for now; Phases 5–6 replace each [PlaceholderScreen] with the real one.
 */
@Composable
fun UnPawseNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            PlaceholderScreen(
                title = "Home",
                // Temporary hook so the Block Overlay is reachable for review (per plan);
                // rewire to the real limit-reached trigger once backend exists.
                onAction = { navController.navigate(Routes.BLOCK) },
                actionLabel = "Open Block Overlay",
            )
        }
        composable(Routes.CAMERA) { PlaceholderScreen(title = "Camera") }
        composable(Routes.STATS) { PlaceholderScreen(title = "Stats") }
        composable(Routes.GALLERY) { PlaceholderScreen(title = "Gallery") }
        composable(Routes.SETTINGS) { PlaceholderScreen(title = "Settings") }
        composable(Routes.BLOCK) {
            PlaceholderScreen(
                title = "Block Overlay",
                onAction = { navController.popBackStack() },
                actionLabel = "Back",
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

@Composable
private fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        if (onAction != null && actionLabel != null) {
            androidx.compose.material3.TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 64.dp),
            ) { Text(actionLabel) }
        }
    }
}
