package com.example.unpawse.ui.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful wrapper around [GalleryScreen]: owns the [GalleryViewModel] and streams real captures in.
 * This is what the NavHost renders; [GalleryUiState.sample] survives for `@Preview` only.
 */
@Composable
fun GalleryRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GalleryScreen(state = state, modifier = modifier)
}
