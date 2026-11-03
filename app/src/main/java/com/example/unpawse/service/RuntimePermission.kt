package com.example.unpawse.service

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** A runtime permission as the UI sees it: is it [granted], can it still be asked for, and how. */
@Stable
class RuntimePermissionState(
    val granted: Boolean,
    /**
     * Asks the system while a dialog can still help, and opens [settings][rememberRuntimePermissionState]
     * once it can't. Check [canAskSystem] before invoking this unprompted — a silent jump to Settings
     * is fine from a button, wrong from an on-entry effect.
     */
    val request: () -> Unit,
    /** Re-reads the current state; call on resume, since the user can change it in system Settings. */
    val refresh: () -> Unit,
    /** False once a dialog can no longer help — [request] then opens settings instead of asking. */
    val canAskSystem: Boolean,
)

/**
 * The one holder behind the camera and notification permissions, so the permanent-denial fallback
 * can't exist for one and be missing from the other (it was, for notifications).
 *
 * [permission] is null where no dialog exists at all (notifications below API 33). [isGranted] is the
 * app's own answer and may be stricter than the platform permission: notifications also need the
 * app's switch in system settings to be on, which no dialog can turn back on.
 */
@Composable
fun rememberRuntimePermissionState(
    permission: String?,
    isGranted: (Context) -> Boolean,
    settingsIntent: (Context) -> Intent,
): RuntimePermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(isGranted(context)) }
    // A counter, not a flag: snapshot state only invalidates readers when a write changes the value,
    // so a second denial writing `true` over `true` would be dropped at exactly the moment the
    // rationale flips to permanent and `canAskSystem` needs re-reading. Not persisted: a fresh process
    // inheriting a permanent denial wastes one tap, then self-corrects.
    var requestCount by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        granted = isGranted(context)
        requestCount++
    }

    val canAskSystem = canAskSystemAgain(
        dialogExists = permission != null,
        platformGranted = permission != null && hasPermission(context, permission),
        askedOnce = requestCount > 0,
        showRationale = permission != null && shouldShowPermissionRationale(context, permission),
    )

    return remember(granted, canAskSystem, requestCount) {
        RuntimePermissionState(
            granted = granted,
            request = {
                if (!granted && canAskSystem && permission != null) {
                    launcher.launch(permission)
                } else {
                    context.startActivity(settingsIntent(context))
                }
            },
            refresh = { granted = isGranted(context) },
            canAskSystem = canAskSystem,
        )
    }
}

/**
 * Whether asking the system could still produce a dialog — the platform has no such query, so it is
 * inferred. [showRationale] is false *both* before the first ask and after a permanent denial, and
 * [askedOnce] is what tells those apart. A permission the platform already granted can't be asked
 * for either: whatever is still off (notifications switched off wholesale) only settings can undo.
 */
internal fun canAskSystemAgain(
    dialogExists: Boolean,
    platformGranted: Boolean,
    askedOnce: Boolean,
    showRationale: Boolean,
): Boolean = dialogExists && !platformGranted && (!askedOnce || showRationale)

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Whether the platform would offer a rationale before asking again. False without an `Activity`,
 * which is the safe answer — it only ever widens [canAskSystemAgain] when true.
 */
private fun shouldShowPermissionRationale(context: Context, permission: String): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

/**
 * The hosting `Activity`, or null. Null-safe rather than a cast because composables here can render
 * in the service-owned overlay window, where the context is the Application.
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
