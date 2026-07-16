package com.example.unpawse

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.unpawse.service.UsageMonitorController
import com.example.unpawse.ui.navigation.Routes
import kotlinx.coroutines.launch
import com.example.unpawse.ui.navigation.UnPawseBottomBar
import com.example.unpawse.ui.navigation.UnPawseNavHost
import com.example.unpawse.ui.navigation.navigateToTab
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * Root composable: owns the theme, the persisted dark-mode override, and the app scaffold
 * (bottom navigation + nav host).
 *
 * [initialRoute] lets a launch intent deep-link somewhere other than Home (the block overlay uses
 * it to open the camera); null keeps the normal Home start.
 */
@Composable
fun UnPawseApp(initialRoute: String? = null) {
    // Dark mode is a persisted override: null = follow the system, an explicit value = user choice.
    // Stored in DataStore via the SettingsRepository so it survives process death.
    val context = LocalContext.current
    val settings = remember(context) { context.appContainer().settingsRepository }
    val scope = rememberCoroutineScope()
    val darkThemeOverride by settings.darkModeOverride.collectAsStateWithLifecycle(initialValue = null)
    val darkMode = darkThemeOverride ?: isSystemInDarkTheme()

    // Monitoring starts here, at the root, rather than from any one screen: usage access is granted
    // in system Settings, so we only learn about it on the way back — and the user may well come
    // back to Home, not to the screen that sent them out. Bound to the Activity lifecycle, so this
    // re-fires on every resume; startIfPermitted is idempotent and refuses without usage access.
    LifecycleResumeEffect(Unit) {
        UsageMonitorController.startIfPermitted(context)
        onPauseOrDispose { }
    }

    UnPawseTheme(darkTheme = darkMode) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        // Honour a deep-link once per launch intent; keyed so a recomposition doesn't re-navigate.
        LaunchedEffect(initialRoute) {
            if (initialRoute != null) navController.navigate(initialRoute)
        }

        // The Block Overlay is a full-screen takeover — no bottom bar.
        val showBottomBar = currentRoute != Routes.BLOCK

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    UnPawseBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = navController::navigateToTab,
                    )
                }
            },
        ) { innerPadding ->
            UnPawseNavHost(
                navController = navController,
                darkMode = darkMode,
                onToggleDarkMode = { enabled -> scope.launch { settings.setDarkModeOverride(enabled) } },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
