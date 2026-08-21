package com.example.unpawse.ui.gallery

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.capture.CaptureRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class GalleryMapperTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 15)

    private fun millis(daysAgo: Long, hour: Int = 10): Long =
        ZonedDateTime.of(today.minusDays(daysAgo).atTime(hour, 0), zone).toInstant().toEpochMilli()

    private fun capture(
        id: String,
        daysAgo: Long,
        favorite: Boolean = false,
        bonus: Boolean = false,
        earnedMinutes: Int = 0,
        widthPx: Int = 0,
        heightPx: Int = 0,
    ) = Capture(
        id = id,
        filePath = "/tmp/$id.jpg",
        capturedAt = millis(daysAgo),
        confidence = 0.9f,
        isBonus = bonus,
        isFavorite = favorite,
        earnedMinutes = earnedMinutes,
        widthPx = widthPx,
        heightPx = heightPx,
    )

    private fun itemFor(capture: Capture) =
        listOf(capture).toGallerySections(today, zone).single().items.single()

    /** A bonus capture passed the detector like any other, so its confidence stays on the card. */
    @Test
    fun `a bonus capture keeps its AI badge and gains the streak caption`() {
        val bonus = listOf(capture("bonus", daysAgo = 0, bonus = true))
            .toGallerySections(today, zone).single().items.single()

        assertEquals(90f, bonus.aiConfidence)
        assertEquals("Bonus", bonus.earnedLabel)
        assertEquals("Daily streak bonus!", bonus.caption)
    }

    @Test
    fun `an ordinary capture reads as verified`() {
        val plain = listOf(capture("plain", daysAgo = 0))
            .toGallerySections(today, zone).single().items.single()

        assertEquals(90f, plain.aiConfidence)
        assertEquals("Verified", plain.earnedLabel)
        assertEquals("Verification successful", plain.caption)
    }

    /**
     * The card's two footer slots hold two different facts, and this is the one that used to be
     * missing: the chip said "Bonus" and the row beside the clock said "Bonus" again.
     */
    @Test
    fun `a capture that earned time reports what it bought`() {
        val item = itemFor(capture("earner", daysAgo = 0, earnedMinutes = 15))

        assertEquals("Verified", item.earnedLabel)
        assertEquals("+15m earned", item.earnedTimeLabel)
    }

    @Test
    fun `an hour of earned time is formatted, not printed in minutes`() {
        assertEquals("+1h earned", itemFor(capture("hour", daysAgo = 0, earnedMinutes = 60)).earnedTimeLabel)
    }

    /** Null rather than "+0m": most captures buy nothing, and the card must not claim otherwise. */
    @Test
    fun `a capture that earned nothing has no time label`() {
        assertNull(itemFor(capture("plain", daysAgo = 0)).earnedTimeLabel)
    }

    /** `isBonus` is a streak fact; it says nothing about whether the reward loop paid out. */
    @Test
    fun `a bonus that earned nothing keeps its chip and gains no time row`() {
        val item = itemFor(capture("bonus", daysAgo = 0, bonus = true))

        assertEquals("Bonus", item.earnedLabel)
        assertNull(item.earnedTimeLabel)
    }

    @Test
    fun `a bonus can also have earned time`() {
        val item = itemFor(capture("both", daysAgo = 0, bonus = true, earnedMinutes = 15))

        assertEquals("Bonus", item.earnedLabel)
        assertEquals("+15m earned", item.earnedTimeLabel)
    }

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
    fun `matchingFilter ALL follows the user's retention window`() {
        val captures = listOf(
            capture("thisWeek", daysAgo = 3),
            capture("lastMonth", daysAgo = 20),
        )
        val now = millis(daysAgo = 0)

        val kept = captures.matchingFilter(GalleryFilter.ALL, now, retentionDays = 7).map { it.id }

        assertEquals(listOf("thisWeek"), kept)
    }

    /** "Keep forever" must show everything, not silently hide old photos it will never delete. */
    @Test
    fun `matchingFilter ALL keeps every capture when retention is forever`() {
        val captures = listOf(
            capture("ancient", daysAgo = 900),
            capture("fresh", daysAgo = 1),
        )
        val now = millis(daysAgo = 0)

        val kept = captures
            .matchingFilter(GalleryFilter.ALL, now, retentionDays = CaptureRetention.KEEP_FOREVER)
            .map { it.id }

        assertEquals(listOf("ancient", "fresh"), kept.sorted())
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

    // --- Tile shape --------------------------------------------------------------------------

    @Test
    fun `a tile is shaped by the photo, portrait and landscape differing`() {
        val portrait = capture("p", daysAgo = 0, widthPx = 1200, heightPx = 1600)
        val landscape = capture("l", daysAgo = 0, widthPx = 1600, heightPx = 1200)

        val items = listOf(portrait, landscape).toGallerySections(today, zone).single().items
            .associateBy { it.id }

        assertEquals(0.75f, items.getValue("p").aspectRatio)
        assertEquals(1.3333334f, items.getValue("l").aspectRatio)
    }

    /**
     * The regression against the old `ASPECT_RATIOS[id.hashCode() % size]`: shape is a fact about
     * the photo, so two photos of the same size must be drawn the same whatever their ids are.
     */
    @Test
    fun `two captures of the same size get the same shape whatever their ids`() {
        val a = capture("aaaaaaaa-0000", daysAgo = 0, widthPx = 1200, heightPx = 1600)
        val b = capture("zzzzzzzz-9999", daysAgo = 0, widthPx = 1200, heightPx = 1600)

        val ratios = listOf(a, b).toGallerySections(today, zone).single().items
            .map { it.aspectRatio }.distinct()

        assertEquals(1, ratios.size)
    }

    /** A pre-v7 imported row records no shape; it gets the default rather than a made-up one. */
    @Test
    fun `unknown dimensions fall back to the default ratio`() {
        assertEquals(DEFAULT_CAPTURE_ASPECT_RATIO, captureAspectRatio(0, 0))
        assertEquals(DEFAULT_CAPTURE_ASPECT_RATIO, captureAspectRatio(1600, 0))
        assertEquals(DEFAULT_CAPTURE_ASPECT_RATIO, captureAspectRatio(-1600, 1200))
    }

    /** A layout bound, not a look: an extreme shot must not produce a sliver or a screen-filler. */
    @Test
    fun `an extreme shape is clamped at both ends`() {
        val panorama = captureAspectRatio(8000, 1000)
        val tower = captureAspectRatio(1000, 8000)

        assertTrue("panorama clamped: $panorama", panorama in 1f..1.6f)
        assertTrue("tower clamped: $tower", tower in 0.6f..1f)
    }

    // --- Empty states ------------------------------------------------------------------------

    private fun emptyFor(
        query: String = "",
        filter: GalleryFilter = GalleryFilter.ALL,
        libraryIsEmpty: Boolean = false,
    ) = galleryEmptyState(hasSections = false, query = query, filter = filter, libraryIsEmpty = libraryIsEmpty)

    @Test
    fun `a grid to show needs no empty state`() {
        assertNull(
            galleryEmptyState(
                hasSections = true,
                query = "nothing matches this",
                filter = GalleryFilter.FAVORITES,
                libraryIsEmpty = true,
            ),
        )
    }

    /** The whole point of the split: a full library must never be told it has no photos. */
    @Test
    fun `a search that matched nothing is not reported as an empty library`() {
        val noMatch = emptyFor(query = "december")!!
        val noLibrary = emptyFor(libraryIsEmpty = true)!!

        assertEquals("No photos match that search", noMatch.title)
        assertNotEquals(noLibrary.title, noMatch.title)
    }

    /** An empty library outranks the query: there is nothing to have searched through. */
    @Test
    fun `an empty library beats every filter and query`() {
        val expected = emptyFor(libraryIsEmpty = true)!!.title

        assertEquals(expected, emptyFor(query = "today", libraryIsEmpty = true)!!.title)
        assertEquals(
            expected,
            emptyFor(filter = GalleryFilter.FAVORITES, libraryIsEmpty = true)!!.title,
        )
    }

    @Test
    fun `each filter that hides everything says which one did it`() {
        assertEquals("No favourites yet", emptyFor(filter = GalleryFilter.FAVORITES)!!.title)
        assertEquals("No photos this week", emptyFor(filter = GalleryFilter.THIS_WEEK)!!.title)
        // Photos exist, none within the retention window ALL draws from.
        assertEquals("No photos left in the window", emptyFor(filter = GalleryFilter.ALL)!!.title)
    }

    @Test
    fun `a blank query is not a search`() {
        assertNotEquals("No photos match that search", emptyFor(query = "   ")!!.title)
    }
}
