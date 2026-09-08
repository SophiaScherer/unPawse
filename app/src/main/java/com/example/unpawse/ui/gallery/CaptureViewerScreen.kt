package com.example.unpawse.ui.gallery

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.AiBadge
import com.example.unpawse.ui.components.CapturePhoto
import com.example.unpawse.ui.components.EarnedChip
import com.example.unpawse.ui.theme.UnPawseTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far a fitted page has to be dragged before letting go closes the viewer. */
private val DISMISS_THRESHOLD = 120.dp

/** How much the page shrinks at the moment it would be released, as a fraction of the drag. */
private const val DISMISS_SHRINK = 0.25f

/** Which of the three things a single drag can mean, decided once per gesture. */
private enum class ViewerDrag { UNDECIDED, TRANSFORM, DISMISS, PAGER }

private val ViewerTransformSaver = listSaver<ViewerTransform, Float>(
    save = { listOf(it.scale, it.offsetX, it.offsetY) },
    restore = { ViewerTransform(it[0], it[1], it[2]) },
)

/**
 * Full-screen photo viewer: a pager over exactly what the Gallery grid was showing, with pinch zoom,
 * bounded pan, double-tap, drag-to-dismiss and the same favourite / share / delete actions as the
 * long-press sheet.
 *
 * **The zoom-versus-swipe conflict is resolved by scale, not by consumption.** A zoomed page owns
 * every drag and the pager's own scrolling is switched off (`userScrollEnabled`); at fit the page
 * absorbs only vertical drags (dismiss) and pinches, leaving horizontal ones unconsumed for the
 * pager underneath. Handing the swipe back at the *pan edge* was tried and rejected: Compose's
 * scroll gesture treats already-consumed moves as a cancelled gesture, so a mid-drag handover is
 * unreliable in a way the user would read as a dropped swipe. Fit-or-nothing is the honest rule, and
 * double-tap makes leaving zoom one gesture.
 *
 * Stateless in the house sense — [state] and callbacks come from [CaptureViewerRoute]; what it does
 * own is pager position and the current page's transform, both of which are UI facts about a
 * gesture in progress and both `rememberSaveable` so a rotation doesn't throw them away.
 */
@Composable
fun CaptureViewerScreen(
    state: CaptureViewerUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onToggleFavorite: (CaptureItem) -> Unit = {},
    onShare: (CaptureItem) -> Unit = {},
    onDelete: (CaptureItem) -> Unit = {},
) {
    val captures = state.captures
    val ids = remember(captures) { captures.map { it.id } }

    val pagerState = rememberPagerState(
        initialPage = viewerInitialPage(ids, state.openAtId),
        pageCount = { captures.size },
    )

    // False only in the frame before the repository has answered — a process death restored straight
    // into the viewer composes it against an empty Gallery. Latched, so that frame doesn't read as
    // "the library is empty, close" and so the opening page is resolved against the real list.
    var opened by rememberSaveable { mutableStateOf(captures.isNotEmpty()) }
    var trackedIds by remember { mutableStateOf(ids) }

    LaunchedEffect(ids) {
        when {
            ids.isEmpty() -> if (opened) onBack()

            !opened -> {
                opened = true
                trackedIds = ids
                pagerState.scrollToPage(viewerInitialPage(ids, state.openAtId))
            }

            ids != trackedIds -> {
                val target = viewerPageAfterChange(trackedIds, ids, pagerState.currentPage)
                trackedIds = ids
                if (target == null) onBack() else if (target != pagerState.currentPage) {
                    pagerState.scrollToPage(target)
                }
            }
        }
    }

    // The transform belongs to a *photo*, not to a page index: deleting the one on screen slides the
    // next into the same index, and inheriting the old zoom there would be a jump with no gesture
    // behind it. Keyed on the settled page so a half-finished swipe doesn't reset mid-drag, and
    // saved so the zoom survives a rotation.
    var transform by rememberSaveable(stateSaver = ViewerTransformSaver) {
        mutableStateOf(ViewerTransform())
    }
    var zoomOwnerId by rememberSaveable { mutableStateOf(state.openAtId) }
    val settledId = captures.getOrNull(pagerState.settledPage)?.id
    LaunchedEffect(settledId) {
        if (settledId != null && settledId != zoomOwnerId) {
            zoomOwnerId = settledId
            transform = ViewerTransform()
        }
    }

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Plain state rather than an Animatable: the gesture loop writes it on every move event, and a
    // suspending snapTo inside awaitPointerEventScope would put a mutex between the finger and the
    // next event. The Animatable only ever ran the release animation, which `animate` does anyway.
    var dismissY by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { DISMISS_THRESHOLD.toPx() }
    val scope = rememberCoroutineScope()

    val zoomed = isViewerZoomed(transform.scale)
    val current = captures.getOrNull(clampViewerPage(pagerState.currentPage, captures.size))

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (captures.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dismissY
                        val travelled = (abs(dismissY) / dismissThresholdPx).coerceIn(0f, 1f)
                        val shrink = 1f - travelled * DISMISS_SHRINK
                        scaleX = shrink
                        scaleY = shrink
                    },
            ) { page ->
                val capture = captures.getOrNull(page)
                if (capture != null) {
                    ViewerPage(
                        capture = capture,
                        // Adjacent pages stay untransformed, so swiping away from a zoomed photo
                        // doesn't drag a magnified neighbour in behind it.
                        transformed = capture.id == zoomOwnerId,
                        transform = transform,
                        onTransform = { transform = it },
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        onDismissDrag = { dismissY += it },
                        onDismissRelease = {
                            if (abs(dismissY) > dismissThresholdPx) {
                                onBack()
                            } else {
                                scope.launch { animate(dismissY, 0f) { value, _ -> dismissY = value } }
                            }
                        },
                    )
                }
            }
        }

        // Hidden mid-drag: the chrome doesn't travel with the photo, so leaving it up would look
        // like the page had come unstuck from its own controls.
        if (current != null && chromeVisible && dismissY == 0f) {
            ViewerTopBar(
                capture = current,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            ViewerActionBar(
                capture = current,
                onToggleFavorite = onToggleFavorite,
                onShare = onShare,
                onDeleteRequest = { confirmDelete = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (confirmDelete && current != null) {
        DeleteCaptureDialog(
            onConfirm = {
                confirmDelete = false
                onDelete(current)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ViewerPage(
    capture: CaptureItem,
    transformed: Boolean,
    transform: ViewerTransform,
    onTransform: (ViewerTransform) -> Unit,
    onToggleChrome: () -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissRelease: () -> Unit,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val width = containerSize.width.toFloat()
    val height = containerSize.height.toFloat()

    // `pointerInput` keeps running the closure it was started with, so a plain parameter read inside
    // the gesture loop would still be the transform this page had when the gesture began — every
    // pinch step would compound onto 1x and the zoom would never accumulate.
    val currentTransform by rememberUpdatedState(transform)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(capture.id, containerSize) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = { tap ->
                        onTransform(
                            viewerDoubleTapTransform(
                                current = currentTransform,
                                tapX = tap.x,
                                tapY = tap.y,
                                containerWidth = width,
                                containerHeight = height,
                                aspectRatio = capture.aspectRatio,
                            ),
                        )
                    },
                )
            }
            .pointerInput(capture.id, containerSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mode = ViewerDrag.UNDECIDED
                    var slop = 0f
                    var event: PointerEvent

                    do {
                        event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        val pan = event.calculatePan()

                        if (mode == ViewerDrag.UNDECIDED) {
                            mode = when {
                                // Two fingers is always a pinch, and a zoomed page always owns its
                                // own drags — the pager is switched off underneath it either way.
                                pressed > 1 || isViewerZoomed(currentTransform.scale) -> ViewerDrag.TRANSFORM
                                else -> {
                                    slop += pan.getDistance()
                                    when {
                                        slop <= viewConfiguration.touchSlop -> ViewerDrag.UNDECIDED
                                        abs(pan.y) > abs(pan.x) -> ViewerDrag.DISMISS
                                        // Left unconsumed on purpose: this is the pager's swipe.
                                        else -> ViewerDrag.PAGER
                                    }
                                }
                            }
                        }

                        when (mode) {
                            ViewerDrag.TRANSFORM -> {
                                val centroid = event.calculateCentroid(useCurrent = true)
                                onTransform(
                                    viewerTransform(
                                        current = currentTransform,
                                        zoomChange = event.calculateZoom(),
                                        panX = pan.x,
                                        panY = pan.y,
                                        centroidX = centroid.x,
                                        centroidY = centroid.y,
                                        containerWidth = width,
                                        containerHeight = height,
                                        aspectRatio = capture.aspectRatio,
                                    ),
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            ViewerDrag.DISMISS -> {
                                onDismissDrag(pan.y)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            else -> Unit
                        }
                    } while (mode != ViewerDrag.PAGER && event.changes.any { it.pressed })

                    if (mode == ViewerDrag.DISMISS) onDismissRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CapturePhoto(
            imagePath = capture.imagePath,
            seed = capture.id.hashCode(),
            // Fit, not the grid's Crop: a viewer that cropped would be hiding the part of the photo
            // the user opened it to see. A row whose JPEG is gone keeps CapturePhoto's named
            // "Photo file missing" slate rather than a black void.
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (transformed) {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                    }
                },
        )
    }
}

@Composable
private fun ViewerTopBar(capture: CaptureItem, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
            )
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = capture.dateLabel.ifBlank { capture.caption },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = capture.timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        capture.aiConfidence?.let { confidence ->
            AiBadge(confidenceText = "%.1f%% AI".format(confidence))
        }
    }
}

@Composable
private fun ViewerActionBar(
    capture: CaptureItem,
    onToggleFavorite: (CaptureItem) -> Unit,
    onShare: (CaptureItem) -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
            )
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Only when the cat actually bought time back; see CaptureItem.earnedTimeLabel.
        capture.earnedTimeLabel?.let { earned ->
            EarnedChip(earned, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { onToggleFavorite(capture) }) {
                Icon(
                    imageVector = if (capture.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (capture.isFavorite) {
                        "Remove from favourites"
                    } else {
                        "Add to favourites"
                    },
                    tint = if (capture.isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            IconButton(onClick = { onShare(capture) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
            }
            IconButton(onClick = onDeleteRequest) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CaptureViewerPreview() {
    UnPawseTheme {
        CaptureViewerScreen(state = CaptureViewerUiState.sample())
    }
}

@Preview(name = "Viewer · missing file", showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CaptureViewerMissingFilePreview() {
    UnPawseTheme {
        CaptureViewerScreen(
            state = CaptureViewerUiState.sample().let { sample ->
                // A row whose JPEG is gone — what a partial cloud restore leaves behind.
                sample.copy(captures = sample.captures.map { it.copy(imagePath = "/gone/${it.id}.jpg") })
            },
        )
    }
}
