package com.example.unpawse.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Supplies the apps the user can choose to monitor. An interface so ViewModels stay testable and
 * never touch [PackageManager] directly (mirrors how `CatDetector` fences off ML Kit).
 */
interface InstalledAppsProvider {
    /** Launchable, user-facing apps, alphabetical, excluding unPawse itself. */
    suspend fun installedApps(): List<InstalledApp>
}

/**
 * [PackageManager]-backed implementation. Queries only apps with a LAUNCHER entry point, which is
 * the restrained approach to Android 11+ package visibility: it needs the matching `<queries>`
 * element in the manifest but **not** the `QUERY_ALL_PACKAGES` permission (which draws Play Store
 * policy review). Consequence: non-launchable apps are invisible to the picker — acceptable, since
 * a user can't spend screen time in an app they can't open.
 */
class PackageManagerInstalledAppsProvider(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InstalledAppsProvider {

    private val appContext = context.applicationContext

    override suspend fun installedApps(): List<InstalledApp> = withContext(ioDispatcher) {
        val packageManager = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        resolved
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .presentableApps(selfPackage = appContext.packageName)
    }
}

/**
 * Pure list shaping, split out so it can be unit-tested without a device: drop unPawse itself,
 * collapse packages that resolve to several launcher activities, and sort by label
 * case-insensitively.
 */
internal fun List<InstalledApp>.presentableApps(selfPackage: String): List<InstalledApp> =
    filterNot { it.packageName == selfPackage }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
