package com.example.unpawse.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful wrapper around [HomeScreen]: owns the [HomeViewModel] and streams real usage/captures in.
 * This is what the NavHost renders; [HomeUiState.sample] survives for `@Preview` only.
 */
@Composable
fun HomeRoute(
    onPauseProtection: () -> Unit,
    onEditLimits: () -> Unit,
    onManageApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        modifier = modifier,
        onPauseProtection = onPauseProtection,
        onEditLimits = onEditLimits,
        onManageApps = onManageApps,
    )
}
