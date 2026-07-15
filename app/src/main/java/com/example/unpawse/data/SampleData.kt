package com.example.unpawse.data

import com.example.unpawse.ui.block.BlockUiState
import com.example.unpawse.ui.home.HomeUiState
import com.example.unpawse.ui.settings.SettingsUiState
import com.example.unpawse.ui.stats.StatsUiState

/**
 * Single source of hardcoded screen state for this UI-only build. The NavHost injects these into
 * the stateless screens — the exact seam where a ViewModel goes later:
 *
 *   composable(HOME) { HomeScreen(state = SampleData.homeState, ...) }
 *   // becomes → val state by viewModel.uiState.collectAsStateWithLifecycle()
 *
 * The concrete mockup strings/numbers live in each screen's `XxxUiState.sample()` factory; this
 * object just names them in one place so callers don't reach into UI packages for sample data.
 *
 * Camera and Gallery no longer appear here — they now render from real ViewModels (CameraRoute /
 * GalleryRoute); their `.sample()` factories remain for `@Preview` use.
 */
object SampleData {
    val homeState: HomeUiState = HomeUiState.sample()
    val statsState: StatsUiState = StatsUiState.sample()
    val settingsState: SettingsUiState = SettingsUiState.sample()
    val blockState: BlockUiState = BlockUiState.sample()
}
