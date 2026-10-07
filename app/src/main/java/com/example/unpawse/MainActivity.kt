package com.example.unpawse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.unpawse.ui.navigation.Routes

class MainActivity : ComponentActivity() {

    // A launch request the nav graph hasn't acted on yet; cleared once it has navigated.
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only on a fresh start: a recreation (rotation, process restore) still carries the old
        // intent and would replay it over whatever the user has done since.
        if (savedInstanceState == null) pendingRoute = routeFor(intent)

        setContent {
            UnPawseApp(
                deepLink = pendingRoute,
                onDeepLinkHandled = { pendingRoute = null },
            )
        }
    }

    // singleTop: a block's "Open Camera" reaches the running activity here instead of recreating
    // it, which keeps the user's back stack.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFor(intent)?.let { pendingRoute = it }
    }

    private fun routeFor(intent: Intent?): String? =
        if (intent?.getBooleanExtra(EXTRA_OPEN_CAMERA, false) == true) Routes.CAMERA else null

    companion object {
        /** Set by the block overlay to send the user straight to the cat camera. */
        const val EXTRA_OPEN_CAMERA = "com.example.unpawse.extra.OPEN_CAMERA"
    }
}
