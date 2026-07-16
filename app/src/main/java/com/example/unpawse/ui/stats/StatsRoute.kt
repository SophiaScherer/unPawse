package com.example.unpawse.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful wrapper around [StatsScreen]: owns the [StatsViewModel] and streams real history in.
 * This is what the NavHost renders; [StatsUiState.sample] survives for `@Preview` only.
 */
@Composable
fun StatsRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(state = state, modifier = modifier)
}
