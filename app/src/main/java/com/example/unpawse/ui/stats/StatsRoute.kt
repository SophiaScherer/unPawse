package com.example.unpawse.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.unpawse.service.UsageAccess

/**
 * Stateful wrapper around [StatsScreen]: owns the [StatsViewModel] and streams real history in.
 * This is what the NavHost renders; [StatsUiState.sample] survives for `@Preview` only.
 */
@Composable
fun StatsRoute(
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Usage access isn't observable, so re-read the all-apps figures on every resume — that is what
    // makes granting it from the notice on the card land without re-entering the screen. The
    // ViewModel makes this a no-op in tracked scope, which reads the platform not at all. Same
    // pattern `AppPickerRoute`, `HomeRoute` and `SettingsRoute` use.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    StatsScreen(
        state = state,
        modifier = modifier,
        onDetails = onDetails,
        onScopeChange = viewModel::onScopeChange,
        // The Route owns the intent, not the NavHost and not the screen — the split `AppPickerRoute`
        // already uses for the same notice.
        onGrantUsageAccess = { context.startActivity(UsageAccess.settingsIntent(context)) },
    )
}
