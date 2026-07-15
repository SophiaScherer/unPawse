package com.example.unpawse.ui.gallery

import com.example.unpawse.data.capture.Capture
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d")

// Portrait-leaning ratios reused to keep the staggered masonry look with real (cropped) photos.
private val ASPECT_RATIOS = floatArrayOf(0.8f, 0.85f, 1.0f, 1.1f, 1.15f, 1.25f)

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
        aiConfidence = if (isBonus) null else confidence * 100f,
        earnedLabel = if (isBonus) "Bonus" else "Verified",
        caption = if (isBonus) "Daily streak bonus!" else "Verification successful",
        aspectRatio = ASPECT_RATIOS[id.hashCode().absoluteValue % ASPECT_RATIOS.size],
        isBonus = isBonus,
        imagePath = filePath,
    )
}
