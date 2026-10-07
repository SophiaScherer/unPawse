package com.example.unpawse

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.unpawse.service.UsageMonitorController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.unpawse.ui.navigation.UnPawseBottomBar
import com.example.unpawse.ui.navigation.UnPawseNavHost
import com.example.unpawse.ui.navigation.navigateToTab
import com.example.unpawse.ui.navigation.openDeepLink
import com.example.unpawse.ui.navigation.owningTab
import com.example.unpawse.ui.navigation.showsBottomBar
import com.example.unpawse.ui.navigation.startDestinationFor
import com.example.unpawse.ui.theme.UnPawseTheme
import com.example.unpawse.ui.theme.isDark
import com.example.unpawse.ui.theme.overrideFor
import com.example.unpawse.ui.theme.themeModeFrom

/**
 * Root composable: owns the theme, the persisted dark-mode override, and the app scaffold
 * (bottom navigation + nav host).
 *
 * [deepLink] is a route a launch or new intent asked for (the block overlay's "Open Camera"); it is
 * navigated to once and then reported through [onDeepLinkHandled]. Null keeps the normal start.
 */
@Composable
fun UnPawseApp(deepLink: String? = null, onDeepLinkHandled: () -> Unit = {}) {
    // Dark mode is a persisted override: null = follow the system, an explicit value = user choice.
    // Stored in DataStore via the SettingsRepository so it survives process death.
    val context = LocalContext.current
    val settings = remember(context) { context.appContainer().settingsRepository }
    val scope = rememberCoroutineScope()
    val darkThemeOverride by settings.darkModeOverride.collectAsStateWithLifecycle(initialValue = null)
    val themeMode = themeModeFrom(darkThemeOverride)
    val darkMode = themeMode.isDark(isSystemInDarkTheme())

    // Monitoring starts here, at the root, rather than from any one screen: usage access is granted
    // in system Settings, so we only learn about it on the way back — and the user may well come
    // back to Home, not to the screen that sent them out. Bound to the Activity lifecycle, so this
    // re-fires on every resume; startIfPermitted is idempotent and refuses without usage access.
    LifecycleResumeEffect(Unit) {
        UsageMonitorController.startIfPermitted(context)
        onPauseOrDispose { }
    }

    // A NavHost's start destination is fixed at composition, and DataStore answers asynchronously —
    // so this is read once and latched, rather than collected. Collecting it would also rebuild the
    // whole graph the moment onboarding sets the flag, tearing down the navigation that completing
    // the tour had just performed.
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings) {
        startDestination = startDestinationFor(settings.onboardingComplete.first(), deepLink)
    }

    UnPawseTheme(darkTheme = darkMode) {
        val start = startDestination
        if (start == null) {
            // One or two frames while DataStore answers. Guessing Home and correcting would flash
            // the app at a first-run user before the tour they haven't seen yet.
            Surface(modifier = Modifier.fillMaxSize()) {}
            return@UnPawseTheme
        }

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        // Cleared once handled, so neither a recomposition nor a later rotation navigates again.
        LaunchedEffect(deepLink) {
            val route = deepLink ?: return@LaunchedEffect
            navController.openDeepLink(route)
            onDeepLinkHandled()
        }

        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val showBottomBar = showsBottomBar(currentRoute, landscape)

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    UnPawseBottomBar(
                        selectedTab = owningTab(currentRoute),
                        onNavigate = navController::navigateToTab,
                    )
                }
            },
        ) { innerPadding ->
            UnPawseNavHost(
                navController = navController,
                themeMode = themeMode,
                onThemeModeChange = { mode ->
                    scope.launch { settings.setDarkModeOverride(overrideFor(mode)) }
                },
                modifier = Modifier.padding(innerPadding),
                startDestination = start,
            )
        }
    }
}
