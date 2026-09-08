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
    // `remember`, not `rememberSaveable`: a saved `true` would restore ahead of the repository on a
    // process-death recreation and defeat the very latch this exists for. Rotation doesn't need the
    // save — the ViewModel survives it, so `captures` is already populated on the first frame.
    var opened by remember { mutableStateOf(captures.isNotEmpty()) }
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
    // Shared by a released-but-under-threshold drag and a cancelled one — both just want the page
    // back at rest, and neither should carry the finger's-still-down case anywhere near `onBack()`.
    val resetDismiss: () -> Unit = {
        scope.launch { animate(dismissY, 0f) { value, _ -> dismissY = value } }
    }

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
                            if (abs(dismissY) > dismissThresholdPx) onBack() else resetDismiss()
                        },
                        onDismissCancel = resetDismiss,
                    )
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
        } else {
            // Reachable outside the process-death frame the latch guards against: the repository can
            // legitimately answer empty while the viewer is open (the retention purge aged everything
            // out, or the list was emptied from another screen), and an empty→empty emission doesn't
            // re-fire the LaunchedEffect that would otherwise close the viewer.
            //
            // The back affordance is unconditional — `opened` can never flip once the list arrives
            // empty and stays empty, so gating the whole state on it strands the user on a black
            // screen with no way out. Only the message waits for `opened`, so it can't claim the
            // library is empty in the frame before the repository has answered.
            ViewerEmptyState(onBack = onBack, showMessage = opened)
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
    onDismissCancel: () -> Unit,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val width = containerSize.width.toFloat()
    val height = containerSize.height.toFloat()

    // `pointerInput` keeps running the closure it was started with, so a plain parameter read inside
    // the gesture loop would still be the transform this page had when the gesture began — every
    // pinch step would compound onto 1x and the zoom would never accumulate.
    val currentTransform by rememberUpdatedState(transform)

    // `viewerTransform` only clamps the offset against the limits live during a gesture. A rotation
    // changes `containerSize` with no gesture in progress, so nothing else re-clamps an offset that
    // was valid in the old box — without this, a photo panned to the landscape edge sits displaced
    // past its own edge in portrait until the next drag.
    //
    // Keyed on `containerSize` alone, not `transform.scale`: every other writer of `transform`
    // (the gesture loop, the double-tap toggle, the settled-page reset) already clamps before calling
    // `onTransform`, so a scale change alone never needs a re-clamp here. The only writer of an
    // *unclamped* transform is the `rememberSaveable` restore, and that always lands in the same
    // frame `containerSize` goes from `Zero` to the real size, so `containerSize` alone still catches
    // it. Keying on scale too meant this relaunched on every pinch step — tens of times a second per
    // page — for no behavior difference.
    LaunchedEffect(containerSize) {
        if (transformed && containerSize != IntSize.Zero) {
            val reclamped = transform.reclamped(width, height, capture.aspectRatio)
            if (reclamped != transform) onTransform(reclamped)
        }
    }

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
                    // Declared outside the try so the finally below can still read it: this page's
                    // `pointerInput` is keyed on `capture.id`, and a mid-drag id change (a retention
                    // purge or an external delete landing on this slot) cancels this coroutine at
                    // `awaitPointerEvent` without ever reaching the loop's own exit. Without the
                    // finally, `dismissY` would stay stuck non-zero and the chrome (gated on it being
                    // zero) would stay hidden until the next drag happened to reset it.
                    var mode = ViewerDrag.UNDECIDED
                    // Set right before the one call that may pop the back stack, so the finally below
                    // can tell "the loop exited normally into a release" from "this coroutine was
                    // cancelled". Cancellation reaches the finally with the finger still down — a list
                    // that emptied under a past-threshold drag, or a rotation mid-gesture — and must
                    // only ever reset `dismissY`, never call `onBack()` a second time (or on a
                    // `NavController` that's already being torn down). A plain flag, not
                    // `currentCoroutineContext().isActive`: that would suspend inside a `finally` during
                    // unwinding, which is its own hazard.
                    var released = false
                    try {
                        awaitFirstDown(requireUnconsumed = false)
                        // Accumulated since the down, not the latest frame's delta: a per-frame read
                        // of `pan` is just finger noise, and axis-ing off one jittery frame can pick
                        // DISMISS for what is actually a horizontal swipe.
                        var accumX = 0f
                        var accumY = 0f
                        var event: PointerEvent

                        do {
                            event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) break
                            val pan = event.calculatePan()

                            if (mode == ViewerDrag.UNDECIDED) {
                                accumX += pan.x
                                accumY += pan.y
                                mode = viewerDragMode(
                                    pressed = pressed,
                                    zoomed = isViewerZoomed(currentTransform.scale),
                                    accumX = accumX,
                                    accumY = accumY,
                                    touchSlop = viewConfiguration.touchSlop,
                                )
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

                        if (mode == ViewerDrag.DISMISS) {
                            released = true
                            onDismissRelease()
                        }
                    } finally {
                        if (mode == ViewerDrag.DISMISS && !released) onDismissCancel()
                    }
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

/** What the viewer draws instead of a pager when there is nothing left to page over. */
@Composable
private fun ViewerEmptyState(onBack: () -> Unit, showMessage: Boolean) {
    Box(Modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        if (showMessage) {
            Text(
                text = "No photos to show",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
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
