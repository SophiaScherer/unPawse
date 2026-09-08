package com.example.unpawse.ui.gallery

import com.example.unpawse.data.capture.Capture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The viewer's pure half: which page it opens on, where it lands after the list changes under it,
 * and how far a zoomed photo may be dragged. Everything that decides whether the wrong photo is on
 * screen — or whether one can be dragged off it — is here rather than in a gesture on a device.
 */
class CaptureViewerStateTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 15)

    private fun millis(daysAgo: Long, hour: Int = 10): Long =
        ZonedDateTime.of(today.minusDays(daysAgo).atTime(hour, 0), zone).toInstant().toEpochMilli()

    private fun capture(id: String, daysAgo: Long, hour: Int = 10, widthPx: Int = 0, heightPx: Int = 0) =
        Capture(
            id = id,
            filePath = "/tmp/$id.jpg",
            capturedAt = millis(daysAgo, hour),
            confidence = 0.9f,
            widthPx = widthPx,
            heightPx = heightPx,
        )

    // ---- the page list -----------------------------------------------------------------------

    @Test
    fun `the viewer pages over the grid's own order, day headings flattened away`() {
        val sections = listOf(
            capture("a", daysAgo = 0, hour = 14),
            capture("b", daysAgo = 0, hour = 9),
            capture("c", daysAgo = 1),
        ).toGallerySections(today, zone)

        assertEquals(listOf("a", "b", "c"), sections.toViewerCaptures().map { it.id })
    }

    @Test
    fun `each page carries the day it was taken, which the grid reads off its heading instead`() {
        val sections = listOf(capture("today", daysAgo = 0), capture("older", daysAgo = 3))
            .toGallerySections(today, zone)
        val captures = sections.toViewerCaptures()

        assertEquals("Today", captures.first { it.id == "today" }.dateLabel)
        // Compared against the heading rather than a literal: the month name is locale-formatted,
        // and what must hold is that the page and the grid say the same thing about the same photo.
        sections.forEach { section ->
            section.items.forEach { assertEquals(section.title, it.dateLabel) }
        }
    }

    @Test
    fun `the viewer opens on the tile that was tapped`() {
        assertEquals(2, viewerInitialPage(listOf("a", "b", "c", "d"), "c"))
    }

    @Test
    fun `a tapped photo that is already gone opens the list rather than nothing`() {
        assertEquals(0, viewerInitialPage(listOf("a", "b"), "vanished"))
        assertEquals(0, viewerInitialPage(emptyList(), "vanished"))
    }

    // ---- surviving a list that changes underneath ----------------------------------------------

    @Test
    fun `deleting the photo on screen lands on the one that took its place`() {
        val before = listOf("a", "b", "c", "d")
        val after = listOf("a", "c", "d")

        // Page 1 was "b"; "c" now occupies index 1, which is what the user was looking towards.
        assertEquals(1, viewerPageAfterChange(before, after, previousPage = 1))
    }

    @Test
    fun `deleting the last photo steps back rather than off the end`() {
        val before = listOf("a", "b", "c")
        val after = listOf("a", "b")

        assertEquals(1, viewerPageAfterChange(before, after, previousPage = 2))
    }

    @Test
    fun `deleting a photo that is not the one on screen keeps the same photo, not the same index`() {
        val before = listOf("a", "b", "c", "d")
        // "a" removed by the retention purge while the user sits on "c".
        val after = listOf("b", "c", "d")

        assertEquals(1, viewerPageAfterChange(before, after, previousPage = 2))
    }

    @Test
    fun `deleting the only photo closes the viewer`() {
        assertNull(viewerPageAfterChange(listOf("only"), emptyList(), previousPage = 0))
    }

    @Test
    fun `a filter change that keeps the photo keeps the photo`() {
        // Favourites: the list shrinks to two, and the starred photo the user was on is one of them.
        assertEquals(1, viewerPageAfterChange(listOf("a", "b", "c", "d"), listOf("b", "d"), previousPage = 3))
    }

    @Test
    fun `a filter change that shares nothing with the old list starts at the top`() {
        assertEquals(0, viewerPageAfterChange(listOf("a", "b"), listOf("x", "y", "z"), previousPage = 1))
    }

    @Test
    fun `a page index outside the old list is still resolved instead of throwing`() {
        assertEquals(1, viewerPageAfterChange(listOf("a", "b"), listOf("a", "b"), previousPage = 99))
        assertEquals(0, viewerPageAfterChange(emptyList(), listOf("a"), previousPage = 4))
    }

    @Test
    fun `clamping a page reports the empty list rather than inventing page zero`() {
        assertEquals(2, clampViewerPage(5, size = 3))
        assertEquals(0, clampViewerPage(-1, size = 3))
        assertEquals(-1, clampViewerPage(0, size = 0))
    }

    // ---- zoom and pan bounds -------------------------------------------------------------------

    @Test
    fun `a fitted photo cannot be panned at all`() {
        val limits = viewerPanLimits(1080f, 1920f, aspectRatio = 0.75f, scale = 1f)

        assertEquals(0f, limits.x, 0.001f)
        assertEquals(0f, limits.y, 0.001f)
    }

    @Test
    fun `pan is bounded by the photo's own edges, per axis`() {
        // 0.75 in a 1080x1920 box fits to 1080x1440 — width-limited, so 480px of letterbox.
        val limits = viewerPanLimits(1080f, 1920f, aspectRatio = 0.75f, scale = 2f)

        assertEquals((1080f * 2f - 1080f) / 2f, limits.x, 0.001f)
        assertEquals((1440f * 2f - 1920f) / 2f, limits.y, 0.001f)
    }

    /** A v6-or-older imported row records no dimensions; nothing here may divide by that. */
    @Test
    fun `an unknown aspect ratio falls back instead of dividing by zero`() {
        val zero = viewerPanLimits(1080f, 1920f, aspectRatio = 0f, scale = 3f)
        val fallback = viewerPanLimits(1080f, 1920f, DEFAULT_CAPTURE_ASPECT_RATIO, scale = 3f)

        assertEquals(fallback, zero)
        assertTrue(zero.x.isFinite() && zero.y.isFinite())
        assertEquals(fallback, viewerPanLimits(1080f, 1920f, Float.NaN, scale = 3f))
        assertEquals(fallback, viewerPanLimits(1080f, 1920f, Float.POSITIVE_INFINITY, scale = 3f))
    }

    @Test
    fun `a container that has not been measured yet has no pan room`() {
        assertEquals(ViewerPanLimits(0f, 0f), viewerPanLimits(0f, 0f, 0.75f, scale = 4f))
    }

    @Test
    fun `zoom is clamped to the fit-to-five-times window`() {
        val fitted = ViewerTransform()
        val out = viewerTransform(fitted, 0.2f, 0f, 0f, 540f, 960f, 1080f, 1920f, 0.75f)
        assertEquals(MIN_VIEWER_SCALE, out.scale, 0.001f)

        val far = viewerTransform(fitted, 40f, 0f, 0f, 540f, 960f, 1080f, 1920f, 0.75f)
        assertEquals(MAX_VIEWER_SCALE, far.scale, 0.001f)
    }

    @Test
    fun `a pan that would drag the photo off screen is clamped to its edge`() {
        val zoomed = viewerTransform(ViewerTransform(), 2f, 0f, 0f, 540f, 960f, 1080f, 1920f, 0.75f)
        val shoved = viewerTransform(zoomed, 1f, 100_000f, 100_000f, 540f, 960f, 1080f, 1920f, 0.75f)
        val limits = viewerPanLimits(1080f, 1920f, 0.75f, shoved.scale)

        assertEquals(limits.x, shoved.offsetX, 0.001f)
        assertEquals(limits.y, shoved.offsetY, 0.001f)
    }

    @Test
    fun `zooming out returns the photo to centre, so a fitted page is never off-centre`() {
        val zoomed = viewerTransform(ViewerTransform(), 3f, 0f, 0f, 100f, 100f, 1080f, 1920f, 0.75f)
        assertTrue(isViewerZoomed(zoomed.scale))

        val fitted = viewerTransform(zoomed, 1f / 3f, 0f, 0f, 100f, 100f, 1080f, 1920f, 0.75f)
        assertEquals(MIN_VIEWER_SCALE, fitted.scale, 0.001f)
        assertEquals(0f, fitted.offsetX, 0.001f)
        assertEquals(0f, fitted.offsetY, 0.001f)
    }

    @Test
    fun `pinching about a point keeps that point under the fingers`() {
        // A pixel a quarter across the screen must not slide to the middle when it is zoomed into.
        val out = viewerTransform(ViewerTransform(), 2f, 0f, 0f, 270f, 960f, 1080f, 1920f, 1f)

        // Centroid is 270px left of centre; at 2x the content under it moves 270px further left.
        assertEquals(270f, out.offsetX, 0.001f)
    }

    @Test
    fun `double tap toggles between fit and zoomed`() {
        val zoomedIn = viewerDoubleTapTransform(ViewerTransform(), 540f, 960f, 1080f, 1920f, 0.75f)
        assertEquals(VIEWER_DOUBLE_TAP_SCALE, zoomedIn.scale, 0.001f)
        assertTrue(isViewerZoomed(zoomedIn.scale))

        val backToFit = viewerDoubleTapTransform(zoomedIn, 540f, 960f, 1080f, 1920f, 0.75f)
        assertEquals(MIN_VIEWER_SCALE, backToFit.scale, 0.001f)
        assertEquals(0f, backToFit.offsetX, 0.001f)
        assertEquals(0f, backToFit.offsetY, 0.001f)
        assertFalse(isViewerZoomed(backToFit.scale))
    }

    /** The pager is gated on this, so a rounding error above 1f must not lock the swipe. */
    @Test
    fun `a hair over fit still counts as fitted`() {
        assertFalse(isViewerZoomed(1.001f))
        assertTrue(isViewerZoomed(1.2f))
    }
}
