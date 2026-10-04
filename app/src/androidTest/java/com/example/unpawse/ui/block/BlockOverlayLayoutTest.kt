package com.example.unpawse.ui.block

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unpawse.ui.theme.UnPawseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back is swallowed on the overlay, so its two buttons are the only way out. On a 360x640dp phone
 * at 200% font they used to be crushed or laid off-screen entirely (audit UX-06).
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

    private fun renderSmallLargeFont(state: BlockUiState) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                UnPawseTheme {
                    Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                        BlockOverlayScreen(state = state)
                    }
                }
            }
        }
    }

    private fun assertButtonWhole(label: String) {
        val node = compose.onNodeWithText(label)
        node.assertIsDisplayed()
        // A button's own minimum height; the crushed "Open Camera" measured about 6dp.
        node.assertHeightIsAtLeast(40.dp)
        // Displayed alone would pass for a button half off the bottom of the 640dp box.
        val bounds = node.getBoundsInRoot()
        assertTrue("$label ends at ${bounds.bottom}", bounds.bottom <= 640.dp)
    }

    @Test
    fun limitBlockKeepsBothButtonsAtTwiceTheFontOnASmallPhone() {
        renderSmallLargeFont(rewardState)
        assertButtonWhole("Open Camera")
        assertButtonWhole("Exit App")
    }

    @Test
    fun escapelessBlockKeepsExitAtTwiceTheFontOnASmallPhone() {
        renderSmallLargeFont(BlockUiState.forSchedule("Chrome", untilLabel = "7:00 AM"))
        assertButtonWhole("Exit App")
    }
}
