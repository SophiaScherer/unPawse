package com.example.unpawse.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarChoiceTest {

    @Test
    fun `no choice resolves to no cat, so the initials avatar stands in`() {
        assertNull(catAvatarForId(AVATAR_NONE))
    }

    @Test
    fun `every preset round-trips through its stored id`() {
        CatAvatar.entries.forEach { assertEquals(it, catAvatarForId(it.id)) }
    }

    @Test
    fun `preset ids are unique and never collide with none`() {
        val ids = CatAvatar.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(ids.contains(AVATAR_NONE))
    }

    @Test
    fun `presets stay well clear of the range a custom cat will claim`() {
        CatAvatar.entries.forEach { assertFalse(it.label, isCustomAvatarId(it.id)) }
        assertTrue(isCustomAvatarId(FIRST_CUSTOM_AVATAR_ID))
    }

    @Test
    fun `an id this build cannot draw falls back rather than guessing`() {
        // A custom cat built by a later version, read back by this one.
        assertNull(catAvatarForId(FIRST_CUSTOM_AVATAR_ID))
        assertNull(catAvatarForId(-1))
        assertNull(catAvatarForId(Int.MAX_VALUE))
    }

    @Test
    fun `every preset is named for the picker`() {
        CatAvatar.entries.forEach { assertTrue(it.name, it.label.isNotBlank()) }
    }
}
