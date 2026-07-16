package com.example.unpawse.data

import com.example.unpawse.ui.block.BlockUiState

/**
 * The last hardcoded screen state in the running app.
 *
 * Home, Stats, Settings, Camera and Gallery all render from real ViewModels now (`XxxRoute` +
 * repositories); their `.sample()` factories survive for `@Preview` only. What remains is the Block
 * Overlay's **in-app design/debug entry** from Home's "Pause Protection" card, which has no real
 * blocked app to describe. The production block doesn't come through here at all — the service draws
 * it over the offending app with `BlockUiState.forApp(realLabel)`.
 */
object SampleData {
    val blockState: BlockUiState = BlockUiState.sample()
}
