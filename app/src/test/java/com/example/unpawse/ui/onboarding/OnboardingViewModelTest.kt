package com.example.unpawse.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import com.example.unpawse.data.settings.CatAvatar
import com.example.unpawse.data.settings.FakePreferencesDataStore
import com.example.unpawse.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val settings = SettingsRepository(FakePreferencesDataStore())
    private var grants = PermissionGrants()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = OnboardingViewModel(
        settings = settings,
        usageAccessGranted = { grants.usageAccess },
        overlayAccessGranted = { grants.overlayAccess },
        cameraGranted = { grants.camera },
        notificationsGranted = { grants.notifications },
        savedState = savedState,
    )

    /** `uiState` is `WhileSubscribed`, so a test has to hold a subscriber for `.value` to move. */
    private fun TestScope.observe(vm: OnboardingViewModel): () -> OnboardingUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return { vm.uiState.value }
    }

    /** What a process restore hands the next ViewModel: the same keys, in a fresh handle. */
    private fun restored(from: SavedStateHandle) =
        SavedStateHandle(from.keys().associateWith { from.get<Any?>(it) })

    private fun OnboardingViewModel.walkTo(target: OnboardingStep) {
        repeat(target.ordinal) { skip() }
    }

    @Test
    fun `next and back walk the tour, and back on the first step does nothing`() = runTest {
        val vm = viewModel()
        val state = observe(vm)

        vm.back()
        assertEquals(OnboardingStep.WELCOME, state().step)

        vm.next()
        vm.next()
        assertEquals(OnboardingStep.NAME, state().step)

        vm.back()
        assertEquals(OnboardingStep.HOW_IT_WORKS, state().step)
    }

    @Test
    fun `skip moves past a permission step without it being granted`() = runTest {
        val vm = viewModel()
        val state = observe(vm)
        vm.walkTo(OnboardingStep.USAGE_ACCESS)

        vm.skip()

        assertEquals(OnboardingStep.OVERLAY_ACCESS, state().step)
        assertTrue(OnboardingStep.USAGE_ACCESS in state().missingPermissions)
    }

    @Test
    fun `a permission granted while away advances its own step on refresh`() = runTest {
        val vm = viewModel()
        val state = observe(vm)
        vm.walkTo(OnboardingStep.USAGE_ACCESS)

        grants = grants.copy(usageAccess = true)
        vm.refreshPermissions()

        assertEquals(OnboardingStep.OVERLAY_ACCESS, state().step)
    }

    @Test
    fun `a grant for some other step updates the state but stays put`() = runTest {
        val vm = viewModel()
        val state = observe(vm)
        vm.walkTo(OnboardingStep.USAGE_ACCESS)

        grants = grants.copy(notifications = true)
        vm.refreshPermissions()

        assertEquals(OnboardingStep.USAGE_ACCESS, state().step)
        assertFalse(OnboardingStep.NOTIFICATIONS in state().missingPermissions)
    }

    @Test
    fun `a typed name shows immediately but is only stored on continue, trimmed`() = runTest {
        val vm = viewModel()
        val state = observe(vm)
        vm.walkTo(OnboardingStep.NAME)

        vm.setNameDraft("Mia  ")
        assertEquals("Mia  ", state().answers.userName)
        assertEquals("", settings.userName.first())

        vm.next()
        assertEquals("Mia", settings.userName.first())
    }

    @Test
    fun `skipping a typed name leaves the stored one, and both exits drop the draft`() = runTest {
        settings.setUserName("Sophia")
        val vm = viewModel()
        val state = observe(vm)
        vm.walkTo(OnboardingStep.NAME)

        vm.setNameDraft("Mia")
        vm.skip()
        vm.back()

        assertEquals("Sophia", settings.userName.first())
        assertEquals("Sophia", state().answers.userName)
    }

    /** Continue's draft is gone once written, so returning shows the trimmed, stored name. */
    @Test
    fun `continuing then coming back shows the stored name, not the raw draft`() = runTest {
        val vm = viewModel()
        val state = observe(vm)
        vm.walkTo(OnboardingStep.NAME)

        vm.setNameDraft("  Mia ")
        vm.next()
        vm.back()

        assertEquals("Mia", state().answers.userName)
    }

    @Test
    fun `complete stores the draft and the flag before handing off`() = runTest {
        val vm = viewModel()
        vm.walkTo(OnboardingStep.DONE)
        var flagAtHandoff: Boolean? = null

        vm.complete { flagAtHandoff = runBlocking { settings.onboardingComplete.first() } }

        assertEquals(true, flagAtHandoff)
        assertTrue(settings.onboardingComplete.first())
    }

    /** Back is navigation, not an answer: the typed text waits, unsaved, for Continue or Skip. */
    @Test
    fun `back from the name step keeps the draft without storing it`() = runTest {
        val vm = viewModel()
        val state = observe(vm)
        vm.walkTo(OnboardingStep.NAME)
        vm.setNameDraft("Mia")

        vm.back()
        vm.next()

        assertEquals("Mia", state().answers.userName)
        assertEquals("", settings.userName.first())
    }

    @Test
    fun `skip intro finishes the tour from any step without saving a typed name`() = runTest {
        val vm = viewModel()
        vm.walkTo(OnboardingStep.NAME)
        vm.setNameDraft("Mia")
        var handedOff = false

        vm.skipIntro { handedOff = true }

        assertTrue(handedOff)
        assertTrue(settings.onboardingComplete.first())
        assertEquals("", settings.userName.first())
    }

    @Test
    fun `a double tap on the last button hands off once`() = runTest {
        val vm = viewModel()
        vm.walkTo(OnboardingStep.DONE)
        var handoffs = 0

        vm.complete { handoffs++ }
        vm.complete { handoffs++ }

        assertEquals(1, handoffs)
    }

    @Test
    fun `picking a cat is stored on the tap`() = runTest {
        val vm = viewModel()
        vm.walkTo(OnboardingStep.AVATAR)

        vm.setAvatarId(CatAvatar.TABBY.id)

        assertEquals(CatAvatar.TABBY.id, settings.avatarId.first())
    }

    @Test
    fun `the step and the name draft survive process death`() = runTest {
        val saved = SavedStateHandle()
        val before = viewModel(saved)
        before.walkTo(OnboardingStep.NAME)
        before.setNameDraft("Mia")
        before.back()
        before.next()

        val after = viewModel(restored(saved))
        val state = observe(after)

        assertEquals(OnboardingStep.NAME, state().step)
        assertEquals("Mia", state().answers.userName)
    }

    @Test
    fun `a permission step survives process death mid trip to Settings`() = runTest {
        val saved = SavedStateHandle()
        val before = viewModel(saved)
        before.walkTo(OnboardingStep.OVERLAY_ACCESS)
        before.refreshPermissions()

        val after = viewModel(restored(saved))
        val state = observe(after)
        after.refreshPermissions()

        assertEquals(OnboardingStep.OVERLAY_ACCESS, state().step)
    }

    /** The case the restore exists for: the switch was flipped while the process was dead. */
    @Test
    fun `a grant made while the process was dead still advances on the first resume`() = runTest {
        val saved = SavedStateHandle()
        val before = viewModel(saved)
        before.walkTo(OnboardingStep.USAGE_ACCESS)
        before.refreshPermissions()

        grants = grants.copy(usageAccess = true)
        val after = viewModel(restored(saved))
        val state = observe(after)
        after.refreshPermissions()

        assertEquals(OnboardingStep.OVERLAY_ACCESS, state().step)
    }

    @Test
    fun `an unknown saved step restarts the tour rather than crashing`() {
        assertEquals(OnboardingStep.WELCOME, onboardingStepNamed("RETIRED_STEP"))
        assertEquals(OnboardingStep.WELCOME, onboardingStepNamed(null))
        assertEquals(OnboardingStep.CAMERA, onboardingStepNamed("CAMERA"))
    }
}
