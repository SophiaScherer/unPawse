package com.example.unpawse.ui.gallery

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.capture.CaptureRetention
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d")

private val WEEK_MILLIS = TimeUnit.DAYS.toMillis(7)

// Portrait-leaning ratios reused to keep the staggered masonry look with real (cropped) photos.
private val ASPECT_RATIOS = floatArrayOf(0.8f, 0.85f, 1.0f, 1.1f, 1.15f, 1.25f)

/**
 * Keeps only captures taken at/after [cutoffMillis] — the Gallery's "last month" display window.
 * Pure so it's unit-testable; the purge worker eventually deletes the same rows, but filtering on
 * read makes the boundary exact even before the worker next runs.
 */
internal fun List<Capture>.retainedWithin(cutoffMillis: Long): List<Capture> =
    filter { it.capturedAt >= cutoffMillis }

/**
 * Applies the selected chip: [GalleryFilter.THIS_WEEK]/[GalleryFilter.ALL] are age windows keyed off
 * [nowMillis]; [GalleryFilter.FAVORITES] shows starred captures of any age (so favorites older than
 * the window surface only here). [GalleryFilter.ALL] hides what the purge worker will delete, so it
 * takes the same user-chosen [retentionDays]. Pure and parameterized for deterministic tests.
 */
internal fun List<Capture>.matchingFilter(
    selected: GalleryFilter,
    nowMillis: Long,
    retentionDays: Int = CaptureRetention.DEFAULT_WINDOW_DAYS,
): List<Capture> =
    when (selected) {
        GalleryFilter.THIS_WEEK -> retainedWithin(nowMillis - WEEK_MILLIS)
        GalleryFilter.ALL -> retainedWithin(CaptureRetention.cutoff(nowMillis, retentionDays))
        GalleryFilter.FAVORITES -> filter { it.isFavorite }
    }

/**
 * Filters by a free-text [query] matched (case-insensitive) against each capture's date/time text —
 * its day label ("Today"/"Yesterday"/"Jul 12"), clock time ("2:30 PM"), and month-day. Blank query
 * keeps everything. Pure/parameterized on [today]/[zone] to mirror how the cards are formatted.
 */
internal fun List<Capture>.matchingSearch(
    query: String,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<Capture> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { it.matchesDateTime(trimmed, today, zone) }
}

private fun Capture.matchesDateTime(query: String, today: LocalDate, zone: ZoneId): Boolean {
    val zoned = Instant.ofEpochMilli(capturedAt).atZone(zone)
    val date = zoned.toLocalDate()
    val haystack = "${sectionTitle(date, today)} ${zoned.format(TIME_FORMAT)} ${date.format(DATE_FORMAT)}"
    return haystack.contains(query, ignoreCase = true)
}

/**
 * What to show in place of an empty grid, or null when there is one.
 *
 * An empty grid means four different things and the sections list can't tell them apart: nothing
 * photographed yet, a search that matched nothing, a chip that filtered everything out, or a library
 * whose every photo has aged past the retention window. Offering the same "no photos yet" line for
 * all four tells a user with a full library that they have none. Ranked most specific first, and
 * pure so every branch is unit-tested.
 */
internal fun galleryEmptyState(
    hasSections: Boolean,
    query: String,
    filter: GalleryFilter,
    libraryIsEmpty: Boolean,
): GalleryEmpty? = when {
    hasSections -> null

    libraryIsEmpty -> GalleryEmpty(
        title = "No cat photos yet",
        body = "Every cat you photograph lands here, grouped by the day you took it — the ones " +
            "that bought you time back and the ones you just felt like taking. They stay on this " +
            "device.",
    )

    query.isNotBlank() -> GalleryEmpty(
        title = "No photos match that search",
        body = "Search looks at when a photo was taken — try \"Today\", \"Yesterday\", a month " +
            "and day like \"Jul 12\", or a time like \"2:30 PM\".",
    )

    filter == GalleryFilter.FAVORITES -> GalleryEmpty(
        title = "No favourites yet",
        body = "Tap a photo and star it to keep it here. Favourites are also exempt from the " +
            "storage window, so they aren't cleared out with the rest.",
    )

    filter == GalleryFilter.THIS_WEEK -> GalleryEmpty(
        title = "No photos this week",
        body = "Nothing in the last seven days. Switch to All to see everything still stored.",
    )

    // Photos exist, but every one has aged past the window Settings keeps them for.
    else -> GalleryEmpty(
        title = "No photos left in the window",
        body = "Your photos are cleared after the period set in Settings → Photo storage. " +
            "Favourites survive it — check that chip.",
    )
}

/**
 * Groups captures (already newest-first from the DAO) into day sections for the Gallery. Pure and
 * parameterized on [today]/[zone] so it's unit-testable without touching the real clock.
 */
internal fun List<Capture>.toGallerySections(
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<GallerySection> =
    groupBy { Instant.ofEpochMilli(it.capturedAt).atZone(zone).toLocalDate() }
        .toSortedMap(reverseOrder())
        .map { (date, captures) ->
            GallerySection(
                title = sectionTitle(date, today),
                items = captures.map { it.toCaptureItem(zone) },
            )
        }

private fun sectionTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DATE_FORMAT)
}

private fun Capture.toCaptureItem(zone: ZoneId): CaptureItem {
    val time = Instant.ofEpochMilli(capturedAt).atZone(zone)
    return CaptureItem(
        id = id,
        timeLabel = time.format(TIME_FORMAT),
        // Shown for bonuses too: a milestone capture is still an ML-verified cat, and blanking the
        // badge would hide a number the app really computed.
        aiConfidence = confidence * 100f,
        earnedLabel = if (isBonus) "Bonus" else "Verified",
        caption = if (isBonus) "Daily streak bonus!" else "Verification successful",
        aspectRatio = ASPECT_RATIOS[id.hashCode().absoluteValue % ASPECT_RATIOS.size],
        isBonus = isBonus,
        imagePath = filePath,
        isFavorite = isFavorite,
    )
}
