package com.example.unpawse.ui.onboarding

import com.example.unpawse.data.settings.AVATAR_NONE
import com.example.unpawse.data.settings.CatAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepTest {

    private val allGranted = PermissionGrants(
        usageAccess = true,
        overlayAccess = true,
        camera = true,
        notifications = true,
    )

    @Test
    fun `the tour starts with the welcome and ends with the handoff`() {
        assertEquals(OnboardingStep.WELCOME, OnboardingStep.entries.first())
        assertEquals(OnboardingStep.DONE, OnboardingStep.entries.last())
    }

    @Test
    fun `the two permissions the app cannot work without come first`() {
        assertEquals(
            listOf(
                OnboardingStep.USAGE_ACCESS,
                OnboardingStep.OVERLAY_ACCESS,
                OnboardingStep.CAMERA,
                OnboardingStep.NOTIFICATIONS,
            ),
            PERMISSION_STEPS,
        )
    }

    @Test
    fun `permission steps are exactly the four that ask the system for something`() {
        OnboardingStep.entries.forEach { step ->
            assertEquals(step.name, step in PERMISSION_STEPS, isPermissionStep(step))
        }
    }

    @Test
    fun `next walks the whole tour in order`() {
        var step = OnboardingStep.WELCOME
        val walked = mutableListOf(step)
        repeat(ONBOARDING_STEP_COUNT - 1) {
            step = nextStep(step)
            walked += step
        }
        assertEquals(OnboardingStep.entries.toList(), walked)
    }

    @Test
    fun `the last step is its own successor so next cannot run off the end`() {
        assertEquals(OnboardingStep.DONE, nextStep(OnboardingStep.DONE))
    }

    @Test
    fun `back walks the steps and stops at the first one`() {
        assertEquals(OnboardingStep.NAME, previousStep(OnboardingStep.AVATAR))
        assertNull(previousStep(OnboardingStep.WELCOME))
    }

    @Test
    fun `the explanatory steps and the handoff are always satisfied`() {
        val nothing = OnboardingAnswers()
        assertTrue(isSatisfied(OnboardingStep.WELCOME, nothing))
        assertTrue(isSatisfied(OnboardingStep.HOW_IT_WORKS, nothing))
        assertTrue(isSatisfied(OnboardingStep.DONE, nothing))
    }

    @Test
    fun `a skipped name leaves the name step unsatisfied`() {
        assertFalse(isSatisfied(OnboardingStep.NAME, OnboardingAnswers()))
        assertFalse(isSatisfied(OnboardingStep.NAME, OnboardingAnswers(userName = "   ")))
        assertTrue(isSatisfied(OnboardingStep.NAME, OnboardingAnswers(userName = "Sophia")))
    }

    @Test
    fun `a skipped avatar leaves the avatar step unsatisfied`() {
        assertFalse(isSatisfied(OnboardingStep.AVATAR, OnboardingAnswers(avatarId = AVATAR_NONE)))
        assertTrue(
            isSatisfied(OnboardingStep.AVATAR, OnboardingAnswers(avatarId = CatAvatar.GINGER.id)),
        )
    }

    @Test
    fun `each permission step reads only its own grant`() {
        val onlyOverlay = PermissionGrants(overlayAccess = true)
        assertFalse(isSatisfied(OnboardingStep.USAGE_ACCESS, OnboardingAnswers(grants = onlyOverlay)))
        assertTrue(isSatisfied(OnboardingStep.OVERLAY_ACCESS, OnboardingAnswers(grants = onlyOverlay)))
        assertFalse(isSatisfied(OnboardingStep.CAMERA, OnboardingAnswers(grants = onlyOverlay)))
        assertFalse(
            isSatisfied(OnboardingStep.NOTIFICATIONS, OnboardingAnswers(grants = onlyOverlay)),
        )
    }

    @Test
    fun `grantFor is null for every step that asks the system for nothing`() {
        listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.HOW_IT_WORKS,
            OnboardingStep.NAME,
            OnboardingStep.AVATAR,
            OnboardingStep.DONE,
        ).forEach { assertNull(it.name, grantFor(it, allGranted)) }
    }

    // --- the resume path ---------------------------------------------------------------------
    //
    // Usage access and the overlay are granted in system Settings, which never reports back. These
    // are the rules that turn our own onResume into a step advance.

    @Test
    fun `granting usage access while away advances off the usage step`() {
        val before = PermissionGrants()
        val after = PermissionGrants(usageAccess = true)
        assertEquals(
            OnboardingStep.OVERLAY_ACCESS,
            stepAfterGrant(OnboardingStep.USAGE_ACCESS, before, after),
        )
    }

    @Test
    fun `granting the overlay while away advances off the overlay step`() {
        val before = PermissionGrants(usageAccess = true)
        val after = before.copy(overlayAccess = true)
        assertEquals(
            OnboardingStep.CAMERA,
            stepAfterGrant(OnboardingStep.OVERLAY_ACCESS, before, after),
        )
    }

    @Test
    fun `coming back without granting leaves the user on the same step`() {
        val nothing = PermissionGrants()
        assertEquals(
            OnboardingStep.USAGE_ACCESS,
            stepAfterGrant(OnboardingStep.USAGE_ACCESS, nothing, nothing),
        )
    }

    @Test
    fun `a grant for some other step does not advance the one on screen`() {
        val before = PermissionGrants()
        val after = PermissionGrants(notifications = true)
        assertEquals(
            OnboardingStep.USAGE_ACCESS,
            stepAfterGrant(OnboardingStep.USAGE_ACCESS, before, after),
        )
    }

    @Test
    fun `a permission revoked while away never advances the step`() {
        val before = PermissionGrants(camera = true)
        val after = PermissionGrants()
        assertEquals(OnboardingStep.CAMERA, stepAfterGrant(OnboardingStep.CAMERA, before, after))
    }

    @Test
    fun `a resume on a non-permission step is inert`() {
        assertEquals(
            OnboardingStep.NAME,
            stepAfterGrant(OnboardingStep.NAME, PermissionGrants(), allGranted),
        )
    }

    @Test
    fun `the last permission step advances to the handoff`() {
        assertEquals(
            OnboardingStep.DONE,
            stepAfterGrant(
                OnboardingStep.NOTIFICATIONS,
                PermissionGrants(),
                PermissionGrants(notifications = true),
            ),
        )
    }

    // --- skip handling -----------------------------------------------------------------------

    @Test
    fun `skipping every permission leaves all four to be picked up later`() {
        assertEquals(PERMISSION_STEPS, missingPermissionSteps(PermissionGrants()))
    }

    @Test
    fun `granting everything leaves nothing to report at the end`() {
        assertTrue(missingPermissionSteps(allGranted).isEmpty())
    }

    @Test
    fun `the closing list keeps tour order and names only what is missing`() {
        val partial = PermissionGrants(usageAccess = true, camera = true)
        assertEquals(
            listOf(OnboardingStep.OVERLAY_ACCESS, OnboardingStep.NOTIFICATIONS),
            missingPermissionSteps(partial),
        )
    }

    @Test
    fun `every reportable permission has a line of its own`() {
        PERMISSION_STEPS.forEach { assertTrue(it.name, missingPermissionLabel(it).isNotBlank()) }
    }

    // --- progress ----------------------------------------------------------------------------

    @Test
    fun `step numbers are one-based and end at the step count`() {
        assertEquals(1, stepNumber(OnboardingStep.WELCOME))
        assertEquals(ONBOARDING_STEP_COUNT, stepNumber(OnboardingStep.DONE))
    }

    @Test
    fun `progress rises across the tour and finishes full`() {
        val progress = OnboardingStep.entries.map(::stepProgress)
        assertEquals(progress.sorted(), progress)
        assertEquals(1f, progress.last(), 0.0001f)
        assertTrue(progress.first() > 0f)
    }
}
