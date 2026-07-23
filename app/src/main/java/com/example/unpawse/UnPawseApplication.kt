package com.example.unpawse

import android.app.Application
import android.content.Context
import com.example.unpawse.data.AppContainer
import com.example.unpawse.data.DefaultAppContainer
import com.example.unpawse.service.CaptureRetentionWorker
import com.example.unpawse.service.MonitorHealthWorker
import com.example.unpawse.service.UsageMonitorController

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

        // Cover process starts that never resume the UI — a launch straight into the camera from the
        // block overlay, or the system recreating us after a kill. UnPawseApp's resume effect misses
        // those. Must follow the container build: the service reads usageTracker off it on start.
        UsageMonitorController.startIfPermitted(this)

        // Defense in depth: a 15-minute periodic re-arm, in case START_STICKY and the boot receiver
        // are both defeated by an aggressive OEM battery manager. KEEP policy makes this idempotent
        // across the many process starts that reach here.
        MonitorHealthWorker.schedule(this)

        // Daily purge of captures past the retention window (favorites exempt). Also KEEP-idempotent.
        CaptureRetentionWorker.schedule(this)
    }
}

/** Convenience accessor used by ViewModel factories and the root composable. */
fun Context.appContainer(): AppContainer =
    (applicationContext as UnPawseApplication).container
