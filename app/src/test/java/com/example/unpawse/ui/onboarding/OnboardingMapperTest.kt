package com.example.unpawse.ui.onboarding

import com.example.unpawse.data.settings.CatAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingMapperTest {

    private fun state(
        step: OnboardingStep = OnboardingStep.WELCOME,
        userName: String = "",
        avatarId: Int = 0,
        grants: PermissionGrants = PermissionGrants(),
        cameraCanAskSystem: Boolean = true,
        notificationsCanAskSystem: Boolean = true,
    ) = toOnboardingUiState(
        step = step,
        answers = OnboardingAnswers(userName = userName, avatarId = avatarId, grants = grants),
        cameraCanAskSystem = cameraCanAskSystem,
        notificationsCanAskSystem = notificationsCanAskSystem,
    )

    /** With an answer in place Skip and Continue would differ only in a way the user can't see. */
    @Test
    fun `an answered name or avatar step drops its skip`() {
        assertNotNull(state(OnboardingStep.NAME).copy.secondaryLabel)
        assertNull(state(OnboardingStep.NAME, userName = "Mia").copy.secondaryLabel)
        assertEquals("Continue", state(OnboardingStep.NAME).copy.primaryLabel)

        assertNotNull(state(OnboardingStep.AVATAR).copy.secondaryLabel)
        assertNull(state(OnboardingStep.AVATAR, avatarId = CatAvatar.SMOKE.id).copy.secondaryLabel)
    }

    /** "We'll send you there" under a green tick reads as though the trip still has to happen. */
    @Test
    fun `a granted permission step confirms instead of repeating its pitch`() {
        PERMISSION_STEPS.forEach { step ->
            val asking = onboardingCopyFor(step, satisfied = false)
            val granted = onboardingCopyFor(step, satisfied = true)
            assertNotEquals(step.name, asking.body, granted.body)
            assertTrue(step.name, granted.body.contains(" on"))
        }
    }

    @Test
    fun `the first step offers no back affordance`() {
        assertFalse(state(OnboardingStep.WELCOME).canGoBack)
        assertTrue(state(OnboardingStep.HOW_IT_WORKS).canGoBack)
    }

    @Test
    fun `the state carries the answers through untouched`() {
        val result = state(userName = "Sophia", avatarId = CatAvatar.TUXEDO.id)
        assertEquals("Sophia", result.answers.userName)
        assertEquals(CatAvatar.TUXEDO.id, result.answers.avatarId)
    }

    @Test
    fun `an ungranted permission step pitches, a granted one confirms`() {
        val asking = state(OnboardingStep.USAGE_ACCESS)
        assertFalse(asking.satisfied)
        assertEquals("Open Settings", asking.copy.primaryLabel)
        assertNotNull(asking.copy.secondaryLabel)

        val granted = state(
            OnboardingStep.USAGE_ACCESS,
            grants = PermissionGrants(usageAccess = true),
        )
        assertTrue(granted.satisfied)
        assertEquals("Continue", granted.copy.primaryLabel)
        // Nothing left to skip once it has been granted.
        assertNull(granted.copy.secondaryLabel)
    }

    @Test
    fun `every step that can be skipped says so, and only those`() {
        OnboardingStep.entries.forEach { step ->
            val result = state(step)
            val skippable = step !in listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.HOW_IT_WORKS,
                OnboardingStep.DONE,
            )
            assertEquals(step.name, skippable, result.copy.secondaryLabel != null)
        }
    }

    @Test
    fun `a permanently denied camera sends the button to app settings instead`() {
        val dead = state(OnboardingStep.CAMERA, cameraCanAskSystem = false)
        assertEquals("Open app settings", dead.copy.primaryLabel)
        assertFalse(dead.cameraCanAskSystem)

        val askable = state(OnboardingStep.CAMERA, cameraCanAskSystem = true)
        assertEquals("Allow camera", askable.copy.primaryLabel)
    }

    @Test
    fun `silenced notifications send the button to notification settings`() {
        val dead = state(OnboardingStep.NOTIFICATIONS, notificationsCanAskSystem = false)
        assertEquals("Open notification settings", dead.copy.primaryLabel)

        val askable = state(OnboardingStep.NOTIFICATIONS)
        assertEquals("Allow notifications", askable.copy.primaryLabel)

        // Each step reads its own prompt: a dead camera says nothing about notifications.
        val other = state(OnboardingStep.NOTIFICATIONS, cameraCanAskSystem = false)
        assertEquals("Allow notifications", other.copy.primaryLabel)
    }

    @Test
    fun `a granted camera ignores the permanent-denial fallback`() {
        val granted = state(
            OnboardingStep.CAMERA,
            grants = PermissionGrants(camera = true),
            cameraCanAskSystem = false,
        )
        assertEquals("Continue", granted.copy.primaryLabel)
    }

    @Test
    fun `the closing step lists whatever was skipped`() {
        val skippedEverything = state(OnboardingStep.DONE)
        assertEquals(PERMISSION_STEPS, skippedEverything.missingPermissions)

        val grantedEverything = state(
            OnboardingStep.DONE,
            grants = PermissionGrants(
                usageAccess = true,
                overlayAccess = true,
                camera = true,
                notifications = true,
            ),
        )
        assertTrue(grantedEverything.missingPermissions.isEmpty())
    }

    @Test
    fun `every step has copy worth showing`() {
        OnboardingStep.entries.forEach { step ->
            val result = state(step)
            assertTrue(step.name, result.copy.title.isNotBlank())
            assertTrue(step.name, result.copy.body.isNotBlank())
            assertTrue(step.name, result.copy.primaryLabel.isNotBlank())
        }
    }

    @Test
    fun `the tutorial names all three beats of the loop`() {
        assertEquals(3, LOOP_BEATS.size)
        LOOP_BEATS.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.body.isNotBlank())
        }
    }

    @Test
    fun `progress and position track the step`() {
        val result = state(OnboardingStep.CAMERA)
        assertEquals(stepNumber(OnboardingStep.CAMERA), result.stepNumber)
        assertEquals(ONBOARDING_STEP_COUNT, result.stepCount)
        assertEquals(stepProgress(OnboardingStep.CAMERA), result.progress, 0.0001f)
    }

    @Test
    fun `the default state matches the first step`() {
        val default = OnboardingUiState()
        assertEquals(OnboardingStep.WELCOME, default.step)
        assertEquals(state(OnboardingStep.WELCOME).copy, default.copy)
        assertFalse(default.canGoBack)
    }
}
