package com.example.unpawse

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.unpawse.ui.navigation.Routes
import com.example.unpawse.ui.navigation.UnPawseBottomBar
import com.example.unpawse.ui.navigation.UnPawseNavHost
import com.example.unpawse.ui.navigation.navigateToTab
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * Root composable: owns the theme, the session-scoped dark-mode override, and the app scaffold
 * (bottom navigation + nav host).
 */
@Composable
fun UnPawseApp() {
    // null = follow the system; the Settings dark-mode toggle sets an explicit override.
    // Session-scoped only — no persistence yet (that arrives with the settings data layer).
    var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val darkMode = darkThemeOverride ?: isSystemInDarkTheme()

    UnPawseTheme(darkTheme = darkMode) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

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
                onToggleDarkMode = { darkThemeOverride = it },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
