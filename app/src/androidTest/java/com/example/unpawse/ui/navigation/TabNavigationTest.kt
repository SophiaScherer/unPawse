package com.example.unpawse.ui.navigation

import android.Manifest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.unpawse.UnPawseScaffold
import com.example.unpawse.ui.theme.ThemeMode
import com.example.unpawse.ui.theme.UnPawseTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real bar and graph, so these replay the audit's UX-15 repros rather than a model of them. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28) // UiAutomation.grantRuntimePermission
class TabNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: TestNavHostController

    @Before
    fun setUp() {
        // Without it the camera tab shows a permission prompt and a system dialog instead of its X.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA,
        )
        compose.setContent {
            nav = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            UnPawseTheme(darkTheme = false) {
                UnPawseScaffold(
                    navController = nav,
                    startDestination = Routes.HOME,
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                )
            }
        }
    }

    @Test
    fun editLimitsOpensAppPickerInsideSettings() {
        clickScrolledTo("Edit Limits")

        assertRoute(Routes.APP_PICKER)
        compose.onNode(tab("Settings")).assertIsSelected()
    }

    /** The bug Sophia named: after Edit Limits, the camera's X restored App Picker instead of Home. */
    @Test
    fun cameraCloseLandsOnHomeAfterEditLimits() {
        clickScrolledTo("Edit Limits")
        compose.onNode(tab("Camera")).performClick()
        assertRoute(Routes.CAMERA)

        compose.onNodeWithContentDescription("Close camera").performClick()

        assertRoute(Routes.HOME)
        compose.onNode(tab("Home")).assertIsSelected()
    }

    @Test
    fun homeTabReturnsHomeAfterEditLimits() {
        clickScrolledTo("Edit Limits")
        compose.onNode(tab("Stats")).performClick()
        compose.onNode(tab("Home")).performClick()

        assertRoute(Routes.HOME)
    }

    @Test
    fun retappingTheCurrentTabReturnsToItsRoot() {
        compose.onNode(tab("Settings")).performClick()
        clickScrolledTo("Individual app limits")
        assertRoute(Routes.APP_PICKER)

        compose.onNode(tab("Settings")).performClick()

        assertRoute(Routes.SETTINGS)
    }

    @Test
    fun statsDetailsOpensTheOneAppPickerAndBackReturnsToSettings() {
        compose.onNode(tab("Settings")).performClick()
        clickScrolledTo("Individual app limits")
        compose.onNode(tab("Stats")).performClick()

        clickScrolledTo("Details")

        assertRoute(Routes.APP_PICKER)
        compose.onNode(tab("Settings")).assertIsSelected()
        assertEquals(1, nav.backStack.count { it.destination.route == Routes.APP_PICKER })

        Espresso.pressBack()
        compose.waitForIdle()
        assertRoute(Routes.SETTINGS)
    }

    // Bar items are the only selectable nodes labeled with a tab's name (a labeled item clears its
    // icon's description, so match the text).
    private fun tab(label: String) =
        hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    private fun clickScrolledTo(text: String) {
        // The screen's own list comes first; a nested rail (Stats' badges) would match too.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun assertRoute(route: String) {
        compose.waitForIdle()
        assertEquals(route, nav.currentDestination?.route)
    }
}
