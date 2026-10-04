package com.example.unpawse.data.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsRepositoryTest {

    private val settings = SettingsRepository(FakePreferencesDataStore())

    /** "Delete all data" returns the app to install day, and on install day the tour hadn't run. */
    @Test
    fun `clearing everything replays the tour and forgets the cat`() = runBlocking {
        settings.setOnboardingComplete(true)
        settings.setAvatarId(CatAvatar.CALICO.id)

        settings.clearAll()

        assertFalse(settings.onboardingComplete.first())
        assertEquals(AVATAR_NONE, settings.avatarId.first())
    }

    @Test
    fun `the profile reads the name and the avatar together`() = runBlocking {
        assertEquals(UserProfile("", AVATAR_NONE), settings.profile.first())

        settings.setUserName("Mia")
        settings.setAvatarId(CatAvatar.SIAMESE.id)

        assertEquals(UserProfile("Mia", CatAvatar.SIAMESE.id), settings.profile.first())
    }
}
