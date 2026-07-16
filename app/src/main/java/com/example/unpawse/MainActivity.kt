package com.example.unpawse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.unpawse.ui.navigation.Routes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The block overlay's "Open Camera" launches us with CLEAR_TOP, so onCreate always sees the
        // fresh intent and no onNewIntent plumbing is needed.
        val initialRoute = if (intent?.getBooleanExtra(EXTRA_OPEN_CAMERA, false) == true) {
            Routes.CAMERA
        } else {
            null
        }

        setContent {
            UnPawseApp(initialRoute = initialRoute)
        }
    }

    companion object {
        /** Set by the block overlay to send the user straight to the cat camera. */
        const val EXTRA_OPEN_CAMERA = "com.example.unpawse.extra.OPEN_CAMERA"
    }
}
