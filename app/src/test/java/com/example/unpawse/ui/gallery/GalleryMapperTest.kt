package com.example.unpawse.ui.gallery

import com.example.unpawse.data.capture.Capture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class GalleryMapperTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 15)

    private fun millis(daysAgo: Long, hour: Int = 10): Long =
        ZonedDateTime.of(today.minusDays(daysAgo).atTime(hour, 0), zone).toInstant().toEpochMilli()

    private fun capture(id: String, daysAgo: Long, favorite: Boolean = false) = Capture(
        id = id,
        filePath = "/tmp/$id.jpg",
        capturedAt = millis(daysAgo),
        confidence = 0.9f,
        isBonus = false,
        isFavorite = favorite,
    )

    @Test
    fun `retainedWithin keeps captures at or after the cutoff and drops older`() {
        val captures = listOf(
            capture("stale", daysAgo = 40),
            capture("edge", daysAgo = 30),
            capture("fresh", daysAgo = 1),
        )
        val cutoff = millis(daysAgo = 30)

        val kept = captures.retainedWithin(cutoff).map { it.id }

        assertEquals(listOf("edge", "fresh"), kept.sorted())
    }

    @Test
    fun `retainedWithin does not special-case favorites`() {
        // The default-view cutoff is age-only; the Favorites filter (Phase 2) layers on top.
        val oldFavorite = capture("oldFav", daysAgo = 40, favorite = true)

        val kept = listOf(oldFavorite).retainedWithin(millis(daysAgo = 30))

        assertEquals(emptyList<String>(), kept.map { it.id })
    }

    @Test
    fun `matchingFilter THIS_WEEK keeps only the last seven days`() {
        val captures = listOf(
            capture("today", daysAgo = 0),
            capture("weekEdge", daysAgo = 6),
            capture("lastMonth", daysAgo = 20),
        )
        val now = millis(daysAgo = 0)

        val kept = captures.matchingFilter(GalleryFilter.THIS_WEEK, now).map { it.id }

        assertEquals(listOf("today", "weekEdge"), kept.sorted())
    }

    @Test
    fun `matchingFilter ALL keeps the last month but not older`() {
        val captures = listOf(
            capture("recent", daysAgo = 10),
            capture("stale", daysAgo = 40),
        )
        val now = millis(daysAgo = 0)

        val kept = captures.matchingFilter(GalleryFilter.ALL, now).map { it.id }

        assertEquals(listOf("recent"), kept)
    }

    @Test
    fun `matchingFilter FAVORITES keeps favorites of any age and drops non-favorites`() {
        val captures = listOf(
            capture("oldFav", daysAgo = 90, favorite = true),
            capture("recentPlain", daysAgo = 1, favorite = false),
        )
        val now = millis(daysAgo = 0)

        val kept = captures.matchingFilter(GalleryFilter.FAVORITES, now).map { it.id }

        assertEquals(listOf("oldFav"), kept)
    }

    @Test
    fun `matchingSearch matches day labels and clock time, blank keeps all`() {
        val captures = listOf(
            capture("t", daysAgo = 0),   // "Today"
            capture("y", daysAgo = 1),   // "Yesterday"
            capture("d", daysAgo = 4),   // "Jul 11"
        )

        assertEquals(listOf("t"), captures.matchingSearch("today", today, zone).map { it.id })
        assertEquals(listOf("y"), captures.matchingSearch("yester", today, zone).map { it.id })
        assertEquals(listOf("d"), captures.matchingSearch("jul 11", today, zone).map { it.id })
        // Every capture()'s time is 10:00 AM, so "AM" matches all three.
        assertEquals(3, captures.matchingSearch("am", today, zone).size)
        assertEquals(3, captures.matchingSearch("   ", today, zone).size)
    }

    @Test
    fun `toGallerySections groups by day with Today and Yesterday labels`() {
        val captures = listOf(
            capture("t1", daysAgo = 0),
            capture("t2", daysAgo = 0),
            capture("y1", daysAgo = 1),
            capture("older", daysAgo = 3),
        )

        val sections = captures.toGallerySections(today = today, zone = zone)

        assertEquals(listOf("Today", "Yesterday", "Jul 12"), sections.map { it.title })
        assertEquals(2, sections.first { it.title == "Today" }.items.size)
    }
}
