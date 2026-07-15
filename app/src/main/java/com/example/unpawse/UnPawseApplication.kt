package com.example.unpawse

import android.app.Application
import android.content.Context
import com.example.unpawse.data.AppContainer
import com.example.unpawse.data.DefaultAppContainer

/**
 * Process entry point. Builds the app-scoped [AppContainer] once and holds it for the whole
 * lifetime, so ViewModel factories can read shared dependencies instead of each reconstructing the
 * graph. Registered via `android:name` in the manifest.
 */
class UnPawseApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

/** Convenience accessor used by ViewModel factories and the root composable. */
fun Context.appContainer(): AppContainer =
    (applicationContext as UnPawseApplication).container
