package com.example.unpawse.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.unpawse.service.OverlayPermission
import com.example.unpawse.service.UsageAccess
import com.example.unpawse.service.rememberNotificationPermissionState
import com.example.unpawse.ui.navigation.Routes
import com.example.unpawse.ui.navigation.SettingsRowIds
import com.example.unpawse.ui.theme.ThemeMode

private const val EXPORT_MIME_TYPE = "application/json"

/**
 * Stateful wrapper for [SettingsScreen] — the `XxxRoute` every other screen already had. Settings
 * used to be wired inline in the NavHost, which meant its row dispatch grew inside the navigation
 * graph rather than beside the screen it belongs to.
 *
 * Rows that leave the app for system Settings are handled here, since they need only a `Context`.
 * Rows that go to another destination call [onNavigate] with a [Routes] constant, keeping the
 * NavHost free of any knowledge about Settings' internals.
 *
 * [themeMode] / [onThemeModeChange] are threaded in from `UnPawseApp`: the theme drives the whole
 * app, so it is owned up there and merely displayed here.
 */
@Composable
fun SettingsRoute(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The user picks where the export lands, so the app never writes to shared storage on its own
    // and needs no storage permission. A null uri means they backed out of the picker.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
    ) { uri -> uri?.let(viewModel::exportTo) }

    // Writing a file produces nothing visible on screen; surface the outcome so a successful export
    // is distinguishable from a silent failure.
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // The permission rows read state that only changes while the user is away in system Settings,
    // so re-read it on the way back. Starting the monitor is not this screen's job — UnPawseApp
    // does it app-wide on every resume.
    // Unlike the two special permissions, this one has a runtime dialog — but it can also be
    // changed in system Settings, so both this handle and the ViewModel re-read on resume.
    val notificationPermission = rememberNotificationPermissionState()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        notificationPermission.refresh()
        onPauseOrDispose { }
    }

    SettingsScreen(
        state = state.copy(themeMode = themeMode),
        modifier = modifier,
        onBack = onBack,
        onThemeModeChange = onThemeModeChange,
        onToggleDailySummary = viewModel::setDailySummary,
        onSensitivityChange = viewModel::setSensitivity,
        onEarnedMinutesChange = viewModel::setEarnedMinutesPerCat,
        // Leave Settings once the wipe lands: staying would show a screen still rendering the data
        // that was just deleted.
        onEraseEverything = { viewModel.eraseEverything(onFinished = onBack) },
        onNameChange = viewModel::setUserName,
        onRowClick = { rowId ->
            when (rowId) {
                SettingsRowIds.APP_LIMITS -> onNavigate(Routes.APP_PICKER)
                SettingsRowIds.PRIVACY_POLICY -> onNavigate(Routes.PRIVACY_POLICY)
                SettingsRowIds.MANAGE_PHOTOS -> onNavigate(Routes.PHOTO_STORAGE)
                SettingsRowIds.EXPORT -> exportLauncher.launch(viewModel.exportFileName())
                SettingsRowIds.USAGE_ACCESS ->
                    context.startActivity(UsageAccess.settingsIntent(context))
                SettingsRowIds.OVERLAY_ACCESS ->
                    context.startActivity(OverlayPermission.settingsIntent(context))
                SettingsRowIds.NOTIFICATION_ACCESS -> {
                    notificationPermission.request()
                    // The dialog's result lands in the permission handle; mirror it into the state
                    // the screen renders, which the resume effect would otherwise not refresh until
                    // the user leaves and returns.
                    viewModel.refreshPermissions()
                }
                // Rows still being built out. Explicit so an unhandled id is a visible gap here
                // rather than a `when` that silently falls through.
                else -> Unit
            }
        },
    )
}
