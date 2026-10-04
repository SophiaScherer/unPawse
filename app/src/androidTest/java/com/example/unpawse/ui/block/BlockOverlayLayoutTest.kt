package com.example.unpawse.ui.block

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unpawse.ui.theme.UnPawseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back is swallowed on the overlay, so its two buttons are the only way out, and the copy is the
 * only place it says why. On a 360x640dp phone at 200% font the buttons used to be crushed or laid
 * off-screen (audit UX-06), and in landscape the copy's viewport collapsed to nothing.
 */
@RunWith(AndroidJUnit4::class)
class BlockOverlayLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val rewardState = BlockUiState.forApp(
        appName = "Chrome",
        reward = RewardTerms(
            grantValue = "+15m",
            grantLabel = "Per cat",
            allowanceValue = "45m",
            allowanceLabel = "Bonus left today",
            cooldownNote = "Chrome can earn again in 6 minutes.",
        ),
    )

    /**
     * Renders into a [width] x [height] dp window at [fontScale]. The sizes are the safe area, so the
     * real window's insets are consumed rather than taken off a second time.
     */
    private fun render(state: BlockUiState, width: Dp, height: Dp, fontScale: Float) {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width, height)) then
                    DeviceConfigurationOverride.FontScale(fontScale),
            ) {
                UnPawseTheme {
                    Box(
                        Modifier
                            .size(width = width, height = height)
                            .consumeWindowInsets(WindowInsets.safeDrawing),
                    ) {
                        BlockOverlayScreen(state = state)
                    }
                }
            }
        }
    }

    private fun assertButtonWhole(label: String, boxHeight: Dp) {
        val node = compose.onNodeWithText(label)
        node.assertIsDisplayed()
        // A button's own minimum height; the crushed "Open Camera" measured about 6dp.
        node.assertHeightIsAtLeast(40.dp)
        // Displayed alone would pass for a button half off the bottom of the box.
        val bounds = node.getBoundsInRoot()
        assertTrue("$label ends at ${bounds.bottom}", bounds.bottom <= boxHeight)
    }

    /** The copy can always be read: a usable viewport, the headline unscrolled, the terms reachable. */
    private fun assertCopyReadable(state: BlockUiState) {
        compose.onNodeWithTag(BLOCK_COPY_TAG).assertHeightIsAtLeast(MIN_COPY_VIEWPORT)
        compose.onNodeWithText(state.headline).assertIsDisplayed()
        state.reward?.let {
            compose.onNodeWithText(it.allowanceLabel).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText(state.footer).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun limitBlockKeepsBothButtonsAtTwiceTheFontOnASmallPhone() {
        render(rewardState, 360.dp, 592.dp, fontScale = 2f)
        assertButtonWhole("Open Camera", 592.dp)
        assertButtonWhole("Exit App", 592.dp)
        assertCopyReadable(rewardState)
    }

    @Test
    fun escapelessBlockKeepsExitAtTwiceTheFontOnASmallPhone() {
        val state = BlockUiState.forSchedule("Chrome", untilLabel = "7:00 AM")
        render(state, 360.dp, 592.dp, fontScale = 2f)
        assertButtonWhole("Exit App", 592.dp)
        assertCopyReadable(state)
    }

    @Test
    fun limitBlockIsReadableInLandscapeAtDefaultFont() {
        render(rewardState, 640.dp, 300.dp, fontScale = 1f)
        assertButtonWhole("Open Camera", 300.dp)
        assertButtonWhole("Exit App", 300.dp)
        assertCopyReadable(rewardState)
    }

    @Test
    fun limitBlockIsReadableInLandscapeAtTwiceTheFont() {
        render(rewardState, 640.dp, 300.dp, fontScale = 2f)
        assertButtonWhole("Open Camera", 300.dp)
        assertButtonWhole("Exit App", 300.dp)
        assertCopyReadable(rewardState)
    }

    private companion object {
        /** Enough for a couple of lines at 200% font; the landscape bug left 0dp. */
        val MIN_COPY_VIEWPORT = 120.dp
    }
}
