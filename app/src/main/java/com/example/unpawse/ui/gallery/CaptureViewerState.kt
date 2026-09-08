package com.example.unpawse.ui.gallery

import kotlin.math.max

/**
 * Fit-to-screen: the scale a page opens at and returns to. Also the value the pager's own horizontal
 * swipe is gated on — see [CaptureViewerScreen].
 */
internal const val MIN_VIEWER_SCALE = 1f

/** Ceiling on pinch zoom. Past this a phone-sized JPEG is only showing its own compression. */
internal const val MAX_VIEWER_SCALE = 5f

/** Where a double-tap lands when the page is fitted. */
internal const val VIEWER_DOUBLE_TAP_SCALE = 2.5f

/**
 * Float slack for "is this fitted?". A pinch that ends a rounding error above 1f must still count as
 * fitted, or the pager would stay locked with nothing on screen to explain why.
 */
private const val SCALE_EPSILON = 0.01f

/**
 * Immutable UI state for the full-screen viewer.
 *
 * [captures] is the Gallery's *currently visible* list — filter chip and search already applied, in
 * the same order the grid shows — flattened out of its day sections. Deliberately not the whole
 * library: paging out of what the grid was showing would contradict the chips the user set.
 *
 * [openAtId] is the tile that was tapped. It is an id rather than an index because the list can be
 * re-derived (a delete, a purge, a filter change) between the tap and the first frame.
 */
data class CaptureViewerUiState(
    val captures: List<CaptureItem>,
    val openAtId: String,
) {
    companion object {
        /** Nothing to show. Reachable only as a seed; the screen exits rather than render it. */
        fun empty() = CaptureViewerUiState(captures = emptyList(), openAtId = "")

        /** Preview-only, like every other `sample()` in this package. */
        fun sample(): CaptureViewerUiState {
            val captures = GalleryUiState.sample().sections.toViewerCaptures()
            return CaptureViewerUiState(captures = captures, openAtId = captures.first().id)
        }
    }
}

/**
 * The viewer's page list: the grid's sections in grid order, day headings dropped. Sections are
 * already newest-first and so are the items inside them, so flattening preserves what the eye saw.
 */
internal fun List<GallerySection>.toViewerCaptures(): List<CaptureItem> = flatMap { it.items }

/**
 * Page to open on. Falls back to the first capture when the tapped id is not in [ids] — the row was
 * purged, or the process was killed and restored into a viewer whose filter no longer holds it.
 */
internal fun viewerInitialPage(ids: List<String>, openAtId: String): Int =
    ids.indexOf(openAtId).coerceAtLeast(0)

/**
 * Where the pager belongs after the capture list changed under it, or null when nothing is left and
 * the viewer must close.
 *
 * The list can shrink (a delete, the retention purge) or be replaced wholesale (the filter chip or
 * the search query changing while the viewer is open), and an index alone survives neither: page 3
 * of the old list is a different photo in the new one. So the *identity* on screen is what carries
 * over — and when that photo is the one that just went, the page that took its place is the nearest
 * thing to "stay where you were", which is why the search runs forwards before backwards.
 */
internal fun viewerPageAfterChange(
    previousIds: List<String>,
    currentIds: List<String>,
    previousPage: Int,
): Int? {
    if (currentIds.isEmpty()) return null
    if (previousIds.isEmpty()) return 0

    val anchor = previousPage.coerceIn(0, previousIds.lastIndex)
    val kept = currentIds.indexOf(previousIds[anchor])
    if (kept >= 0) return kept

    for (i in anchor + 1 until previousIds.size) {
        val moved = currentIds.indexOf(previousIds[i])
        if (moved >= 0) return moved
    }
    for (i in anchor - 1 downTo 0) {
        val moved = currentIds.indexOf(previousIds[i])
        if (moved >= 0) return moved
    }
    // Nothing in common: a filter or search change swapped the whole list out. Start at the top.
    return 0
}

/** Clamps a page onto a list of [size] photos; -1 when there are none. */
internal fun clampViewerPage(page: Int, size: Int): Int =
    if (size <= 0) -1 else page.coerceIn(0, size - 1)

/** How far a zoomed page may be dragged from centre before its own edge would leave the screen. */
internal data class ViewerPanLimits(val x: Float, val y: Float)

/**
 * Pan bounds for a photo of [aspectRatio] fitted into a [containerWidth] x [containerHeight] box and
 * then scaled by [scale]. Zero on an axis the photo doesn't overflow, so a fitted page cannot be
 * dragged at all and a zoomed one always keeps its content over the screen.
 *
 * A ratio of zero, negative or non-finite falls back to [DEFAULT_CAPTURE_ASPECT_RATIO] rather than
 * dividing by it: `widthPx`/`heightPx` are 0 on any row imported from a v6-or-older document, and
 * [captureAspectRatio] already answers that case the same way for the grid.
 */
internal fun viewerPanLimits(
    containerWidth: Float,
    containerHeight: Float,
    aspectRatio: Float,
    scale: Float,
): ViewerPanLimits {
    if (containerWidth <= 0f || containerHeight <= 0f) return ViewerPanLimits(0f, 0f)
    val ratio = if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio else DEFAULT_CAPTURE_ASPECT_RATIO
    val fitWidth: Float
    val fitHeight: Float
    if (containerWidth / containerHeight > ratio) {
        fitHeight = containerHeight
        fitWidth = containerHeight * ratio
    } else {
        fitWidth = containerWidth
        fitHeight = containerWidth / ratio
    }
    val bounded = scale.coerceIn(MIN_VIEWER_SCALE, MAX_VIEWER_SCALE)
    return ViewerPanLimits(
        x = max(0f, (fitWidth * bounded - containerWidth) / 2f),
        y = max(0f, (fitHeight * bounded - containerHeight) / 2f),
    )
}

/** The zoom/pan a page is currently drawn with. Offsets are pixels from centre. */
internal data class ViewerTransform(
    val scale: Float = MIN_VIEWER_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/** Fitted pages are the ones the pager is allowed to swipe; see [CaptureViewerScreen]. */
internal fun isViewerZoomed(scale: Float): Boolean = scale > MIN_VIEWER_SCALE + SCALE_EPSILON

/**
 * One pinch/pan step, resolved about the gesture centroid so the pixel under the fingers stays under
 * them, then clamped to [viewerPanLimits]. Pure so the arithmetic that decides whether a photo can
 * be dragged off-screen is unit-tested rather than eyeballed on a device.
 *
 * Centroid coordinates are relative to the container's top-left; the returned offsets are relative
 * to its centre, which is what `graphicsLayer` translation wants.
 */
internal fun viewerTransform(
    current: ViewerTransform,
    zoomChange: Float,
    panX: Float,
    panY: Float,
    centroidX: Float,
    centroidY: Float,
    containerWidth: Float,
    containerHeight: Float,
    aspectRatio: Float,
): ViewerTransform {
    val scale = (current.scale * zoomChange).coerceIn(MIN_VIEWER_SCALE, MAX_VIEWER_SCALE)
    val growth = scale / current.scale
    val centredX = centroidX - containerWidth / 2f
    val centredY = centroidY - containerHeight / 2f
    val offsetX = centredX - (centredX - current.offsetX) * growth + panX
    val offsetY = centredY - (centredY - current.offsetY) * growth + panY
    val limits = viewerPanLimits(containerWidth, containerHeight, aspectRatio, scale)
    return ViewerTransform(
        scale = scale,
        offsetX = offsetX.coerceIn(-limits.x, limits.x),
        offsetY = offsetY.coerceIn(-limits.y, limits.y),
    )
}

/** Double-tap toggles fit/zoomed, zooming in on the point that was tapped. */
internal fun viewerDoubleTapTransform(
    current: ViewerTransform,
    tapX: Float,
    tapY: Float,
    containerWidth: Float,
    containerHeight: Float,
    aspectRatio: Float,
): ViewerTransform =
    if (isViewerZoomed(current.scale)) {
        ViewerTransform()
    } else {
        viewerTransform(
            current = current,
            zoomChange = VIEWER_DOUBLE_TAP_SCALE / current.scale,
            panX = 0f,
            panY = 0f,
            centroidX = tapX,
            centroidY = tapY,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            aspectRatio = aspectRatio,
        )
    }
