package com.example.unpawse.data.schedule

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store's contract: ids, ordering, scoping and deletion. No clock is involved. */
class ScheduleRepositoryTest {

    private val repo = ScheduleRepository(FakeScheduleDao())

    private fun window(
        label: String,
        packageName: String? = null,
        start: Int = 22 * 60,
        end: Int = 7 * 60,
    ) = ScheduleWindow(
        id = ScheduleRepository.NEW_WINDOW_ID,
        label = label,
        packageName = packageName,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        daysMask = EVERY_DAY_MASK,
        enabled = true,
    )

    @Test
    fun `saving a new window assigns it an id`() = runBlocking {
        val id = repo.save(window("Bedtime"))

        assertTrue(id > ScheduleRepository.NEW_WINDOW_ID)
        assertEquals("Bedtime", repo.window(id)?.label)
    }

    @Test
    fun `saving an existing window updates it in place`() = runBlocking {
        val id = repo.save(window("Bedtime"))
        val saved = repo.window(id)!!

        repo.save(saved.copy(label = "Sleep"))

        assertEquals(1, repo.allWindows().size)
        assertEquals("Sleep", repo.window(id)?.label)
    }

    @Test
    fun `windows come back earliest start first`() = runBlocking {
        repo.save(window("Evening", start = 20 * 60, end = 22 * 60))
        repo.save(window("Morning", start = 7 * 60, end = 9 * 60))

        assertEquals(listOf("Morning", "Evening"), repo.allWindows().map { it.label })
    }

    @Test
    fun `an app sees its own windows and every global one`() = runBlocking {
        repo.save(window("Bedtime", packageName = null, start = 22 * 60))
        repo.save(window("No Instagram", packageName = "com.ig", start = 9 * 60))
        repo.save(window("No TikTok", packageName = "com.tiktok", start = 10 * 60))

        val forInstagram = repo.observeWindowsFor("com.ig").first().map { it.label }

        assertEquals(listOf("No Instagram", "Bedtime"), forInstagram)
    }

    @Test
    fun `toggling a window leaves the rest of it alone`() = runBlocking {
        val id = repo.save(window("Bedtime"))

        repo.setEnabled(id, enabled = false)

        val saved = repo.window(id)!!
        assertFalse(saved.enabled)
        assertEquals("Bedtime", saved.label)
    }

    @Test
    fun `deleting removes only the named window`() = runBlocking {
        val bedtime = repo.save(window("Bedtime"))
        repo.save(window("School", start = 9 * 60, end = 15 * 60))

        repo.delete(bedtime)

        assertNull(repo.window(bedtime))
        assertEquals(listOf("School"), repo.allWindows().map { it.label })
    }

    @Test
    fun `dropping an app's windows spares the global ones`() = runBlocking {
        repo.save(window("Bedtime", packageName = null))
        repo.save(window("No Instagram", packageName = "com.ig", start = 9 * 60))

        repo.deleteWindowsFor("com.ig")

        assertEquals(listOf("Bedtime"), repo.allWindows().map { it.label })
    }

    @Test
    fun `clearing empties the store`() = runBlocking {
        repo.save(window("Bedtime"))
        repo.save(window("School", start = 9 * 60))

        repo.clearAll()

        assertTrue(repo.allWindows().isEmpty())
    }
}
