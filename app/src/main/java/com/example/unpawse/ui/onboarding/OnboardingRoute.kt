package com.example.unpawse.ui.onboarding

import androidx.activity.compose.BackHandler
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
import com.example.unpawse.ui.camera.rememberCameraPermissionState

/**
 * Stateful wrapper for [OnboardingScreen]. Owns the ViewModel and the two runtime-permission
 * handles, and is where the resume problem is actually solved.
 *
 * Usage access and "display over other apps" are system-Settings switches: we send the user out
 * with an `Intent` and the platform never tells us what they did. The only signal we get is our own
 * `onResume`, so that is where all four permissions are re-read — and the ViewModel advances the
 * step when the one being asked for has arrived. Without it, coming back from Settings having
 * granted the thing leaves the same screen still asking for it.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val cameraPermission = rememberCameraPermissionState()
    val notificationPermission = rememberNotificationPermissionState()

    // Only the handle knows whether Android will still show the camera dialog; the copy needs it to
    // offer app settings instead of a button that would silently do nothing.
    LaunchedEffect(cameraPermission.canAskSystem) {
        viewModel.setCameraCanAskSystem(cameraPermission.canAskSystem)
    }
    LaunchedEffect(notificationPermission.canAskSystem) {
        viewModel.setNotificationsCanAskSystem(notificationPermission.canAskSystem)
    }

    // A runtime dialog answers through its launcher, long after the tap that opened it. Keying on
    // the handles' own granted flags is what turns that late answer into an advanced step.
    LaunchedEffect(cameraPermission.granted, notificationPermission.granted) {
        viewModel.refreshPermissions()
    }

    LifecycleResumeEffect(Unit) {
        cameraPermission.refresh()
        notificationPermission.refresh()
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    // Back walks the tour rather than leaving it. Disabled on the first step, where there is no
    // previous step and back belongs to whoever pushed us — the system on a first run, Settings on
    // a replay.
    BackHandler(enabled = state.canGoBack, onBack = viewModel::back)

    OnboardingScreen(
        state = state,
        modifier = modifier,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onSkip = viewModel::skip,
        onNameChange = viewModel::setNameDraft,
        onAvatarSelected = viewModel::setAvatarId,
        onGrant = { step ->
            when (step) {
                // No dialog exists for either of these; system Settings is the only door.
                OnboardingStep.USAGE_ACCESS ->
                    context.startActivity(UsageAccess.settingsIntent(context))
                OnboardingStep.OVERLAY_ACCESS ->
                    context.startActivity(OverlayPermission.settingsIntent(context))
                // Both handles fall back to the app's settings page once the system stops asking,
                // so neither button can go dead.
                OnboardingStep.CAMERA -> cameraPermission.request()
                OnboardingStep.NOTIFICATIONS -> notificationPermission.request()
                else -> Unit
            }
        },
        onFinish = { viewModel.complete(onFinished) },
        onSkipIntro = { viewModel.skipIntro(onFinished) },
    )
}
