package com.example.unpawse.ui.block

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.CapturePhoto
import com.example.unpawse.ui.components.StatPill
import com.example.unpawse.ui.theme.UnPawseTheme
import com.example.unpawse.ui.theme.unPawseColors

/** Copy for the "Time for a Break" overlay. [showCamera] is false for a focus hard-block. */
data class BlockUiState(
    val appName: String = "this app",
    val headline: String = "Time for a Break 🐱",
    val subtitle: String = "You've reached today's limit for this app.",
    val body: String = "To continue using this app, go find your cat and take a picture.",
    val footer: String = "Healthy habits happen one break at a time.",
    val showCamera: Boolean = true,
    /**
     * What a cat is worth here and how much of today's allowance is left. Null for the escape-less
     * blocks and the debug route, which have no reward to describe.
     */
    val reward: RewardTerms? = null,
    /**
     * The user's most recent cat, drawn as the hero. Orthogonal to every other field — the same
     * photo whatever the block's reason — so the service attaches it once rather than each factory
     * taking it. Null falls back to the stand-in gradient.
     */
    val photoPath: String? = null,
) {
    companion object {
        fun sample() = BlockUiState()

        /**
         * The real thing: names the app whose limit was actually hit, and states what a cat buys.
         *
         * [reward] defaults to null so the debug route and the tests that only care about
         * [showCamera] stay unchanged; the service always passes real terms.
         */
        fun forApp(appName: String, reward: RewardTerms? = null) = BlockUiState(
            appName = appName,
            subtitle = "You've reached today's limit for $appName.",
            body = "To keep using $appName, go find your cat and take a picture.",
            reward = reward,
        )

        /**
         * The limit is reached *and* today's bonus allowance for this app is spent, so the camera
         * genuinely cannot help until tomorrow. Hiding the button is the honest thing to do —
         * offering an escape that would refuse the photo is worse than offering none.
         */
        fun forAppOutOfRewards(appName: String) = BlockUiState(
            appName = appName,
            subtitle = "You've used all of today's bonus time for $appName.",
            body = "Even a very good cat can't buy more today. Come back tomorrow.",
            showCamera = false,
        )

        /**
         * A focus-session hard block: no camera escape (the "+15 min cat" path is hidden), the app
         * unlocks only when the timer ends. The user can still leave via "Exit App".
         */
        fun forFocus(appName: String) = BlockUiState(
            appName = appName,
            headline = "Focus mode 🎯",
            subtitle = "$appName is paused",
            body = "Stay focused — this app unlocks when your session ends.",
            footer = "You've got this.",
            showCamera = false,
        )

        /**
         * A schedule hard block: this app is outside its allowed hours. No camera escape, for the
         * same reason as [forFocus] — earned minutes raise a budget, and no amount of budget makes
         * it not be bedtime. [untilLabel] is the window's end time, e.g. "7:00 AM".
         */
        fun forSchedule(appName: String, untilLabel: String) = BlockUiState(
            appName = appName,
            headline = "Not right now 🌙",
            subtitle = "$appName is off limits",
            body = "You've set this time aside. $appName unlocks at $untilLabel.",
            footer = "Sleep well — the cats will still be here.",
            showCamera = false,
        )
    }
}

/**
 * Full-screen "limit reached" takeover, drawn by the monitor service over the blocked app. It is
 * also registered as an in-app nav destination for design review, though nothing navigates to it.
 */
@Composable
fun BlockOverlayScreen(
    state: BlockUiState,
    modifier: Modifier = Modifier,
    onOpenCamera: () -> Unit = {},
    onExit: () -> Unit = {},
    /** Only the real overlay window sets this; see the guard below. */
    interceptBack: Boolean = false,
) {
    // Guarded with `if` rather than BackHandler(enabled = …): BackHandler resolves and null-checks
    // LocalOnBackPressedDispatcherOwner regardless of `enabled`, and neither the in-app debug route
    // nor the @Preview has one. Swallowing back there would also trap the user with no way out.
    if (interceptBack) BackHandler { /* back is exactly what this window exists to refuse */ }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Wider than tall (landscape, over a fullscreen video) leaves too little height to stack the
        // copy above the buttons, so they go side by side instead.
        val twoPane = maxWidth > maxHeight
        val compact = maxHeight < COMPACT_HEIGHT
        val heroSize = blockHeroSize(maxHeight, LocalDensity.current.fontScale)
        // Keyed on the window size so a rotation reopens at the headline rather than mid-sentence.
        val scroll = remember(maxWidth, maxHeight) { ScrollState(0) }
        Box(contentAlignment = Alignment.TopCenter) {
            CatEars()
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.unPawseColors.cardSurface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .widthIn(max = MAX_CARD_WIDTH),
            ) {
                if (twoPane) {
                    TwoPaneCard(state, scroll, onOpenCamera, onExit)
                } else {
                    StackedCard(state, scroll, heroSize, compact, onOpenCamera, onExit)
                }
            }
        }
    }
}

/**
 * Portrait: only the copy scrolls, and the buttons are measured first so they can never be pushed
 * off, because back is swallowed and they are the only way out. On a short screen the footer joins
 * the scrolling copy so the viewport keeps its height.
 */
@Composable
private fun StackedCard(
    state: BlockUiState,
    scroll: ScrollState,
    heroSize: Dp,
    compact: Boolean,
    onOpenCamera: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag(BLOCK_COPY_TAG)
                .fadingEdges(scroll)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MeowChipRow()
            CatIllustration(state.photoPath, Modifier.size(heroSize))
            Spacer(Modifier.height(20.dp))
            BlockCopy(state)
            if (compact) {
                Spacer(Modifier.height(16.dp))
                Footer(state.footer)
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        BlockActions(state.showCamera, onOpenCamera, onExit)
        if (!compact) {
            Spacer(Modifier.height(16.dp))
            Footer(state.footer)
        }
    }
}

/**
 * Landscape: the copy scrolls on the left starting at the headline, so why the app is blocked reads
 * without scrolling; the photo shrinks to whatever the buttons leave on the right.
 */
@Composable
private fun TwoPaneCard(
    state: BlockUiState,
    scroll: ScrollState,
    onOpenCamera: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag(BLOCK_COPY_TAG)
                .fadingEdges(scroll)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BlockCopy(state)
            Spacer(Modifier.height(16.dp))
            Footer(state.footer)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                val hero = minOf(maxWidth, maxHeight - HERO_GAP, MAX_HERO_SIZE)
                // A photo squeezed below this reads as a stray dot, not the user's cat.
                if (hero >= MIN_TWO_PANE_HERO_SIZE) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CatIllustration(state.photoPath, Modifier.size(hero))
                        Spacer(Modifier.height(HERO_GAP))
                    }
                }
            }
            BlockActions(state.showCamera, onOpenCamera, onExit)
        }
    }
}

@Composable
private fun BlockCopy(state: BlockUiState) {
    Text(
        state.headline,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        state.subtitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        state.body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    state.reward?.let { RewardTermsPanel(it) }
}

@Composable
private fun BlockActions(showCamera: Boolean, onOpenCamera: () -> Unit, onExit: () -> Unit) {
    if (showCamera) {
        Button(
            onClick = onOpenCamera,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Open Camera", style = MaterialTheme.typography.labelLarge)
        }
    }
    TextButton(onClick = onExit, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text("Exit App", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Footer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
    )
}

/** Tags the scrolling copy so tests can measure the viewport it is left with. */
internal const val BLOCK_COPY_TAG = "blockOverlayCopy"

/** Below this the footer scrolls with the copy rather than taking height from it. */
private val COMPACT_HEIGHT = 640.dp

private val HERO_GAP = 12.dp
private val MIN_TWO_PANE_HERO_SIZE = 64.dp

/** Keeps landscape lines on a wide phone or tablet to a readable length. */
private val MAX_CARD_WIDTH = 720.dp

/**
 * The hero gets whatever height the rest of the card leaves, so a short screen or a large font
 * shrinks the photo before it makes the copy scroll. The reserve is the reward card's other
 * content at 100% font, split into what grows with the font and what doesn't.
 */
internal fun blockHeroSize(availableHeight: Dp, fontScale: Float): Dp =
    (availableHeight - FIXED_RESERVE - TEXT_RESERVE * fontScale).coerceIn(MIN_HERO_SIZE, MAX_HERO_SIZE)

private val FIXED_RESERVE = 270.dp
private val TEXT_RESERVE = 250.dp
internal val MIN_HERO_SIZE = 96.dp
internal val MAX_HERO_SIZE = 180.dp

/**
 * Fades the content out at an edge it can still scroll past. Without it a cut that lands in a gap
 * between items looks like the end of the card, and the reward terms are never found.
 */
private fun Modifier.fadingEdges(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // A quarter of the viewport at most, so the two fades never meet and erase what they cue.
        val fade = minOf(FADE_HEIGHT.toPx(), size.height / 4)
        if (scroll.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), endY = fade),
                blendMode = BlendMode.DstOut,
            )
        }
        if (scroll.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstOut,
            )
        }
    }

private val FADE_HEIGHT = 32.dp

/**
 * The reward rules, stated up front rather than discovered by being refused. Reuses [StatPill] —
 * the value-over-label chip Home's progress card already draws — so this adds copy, not components.
 */
@Composable
private fun RewardTermsPanel(terms: RewardTerms) {
    Spacer(Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatPill(value = terms.grantValue, label = terms.grantLabel, modifier = Modifier.weight(1f))
        StatPill(
            value = terms.allowanceValue,
            label = terms.allowanceLabel,
            modifier = Modifier.weight(1f),
        )
    }
    terms.cooldownNote?.let { note ->
        Spacer(Modifier.height(10.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Two soft circles peeking above the card to read as cat ears — the brand signature. */
@Composable
private fun CatEars() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(90.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun MeowChipRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            "Meow!",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CatIllustration(photoPath: String?, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative: a missing file falls back to the stand-in rather than making "Photo file
        // missing" the centrepiece of a screen the user cannot act on it from.
        CapturePhoto(
            imagePath = photoPath,
            seed = 2,
            decorative = true,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun BlockOverlayPreview() {
    UnPawseTheme {
        BlockOverlayScreen(state = BlockUiState.sample())
    }
}

/** The state the service actually raises: the terms are the tallest thing added to this card. */
@Preview(name = "Block · reward terms", showBackground = true, heightDp = 900)
@Composable
private fun BlockOverlayRewardPreview() {
    UnPawseTheme {
        BlockOverlayScreen(
            state = BlockUiState.forApp(
                appName = "Chrome",
                reward = RewardTerms(
                    grantValue = "+15m",
                    grantLabel = "Per cat",
                    allowanceValue = "45m",
                    allowanceLabel = "Bonus left today",
                    cooldownNote = "Chrome can earn again in 6 minutes.",
                ),
            ),
        )
    }
}

/** The worst case UX-06 was found at: the copy scrolls and both buttons stay whole. */
@Preview(name = "Block · 360x640, 200% font", showBackground = true, widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun BlockOverlaySmallLargeFontPreview() {
    UnPawseTheme {
        BlockOverlayScreen(
            state = BlockUiState.forApp(
                appName = "Chrome",
                reward = RewardTerms(
                    grantValue = "+15m",
                    grantLabel = "Per cat",
                    allowanceValue = "45m",
                    allowanceLabel = "Bonus left today",
                    cooldownNote = null,
                ),
            ),
        )
    }
}

/** Landscape on a small phone at 200% font: the copy starts at the headline beside the buttons. */
@Preview(name = "Block · 640x320, 200% font", showBackground = true, widthDp = 640, heightDp = 320, fontScale = 2f)
@Composable
private fun BlockOverlayLandscapePreview() {
    UnPawseTheme {
        BlockOverlayScreen(
            state = BlockUiState.forApp(
                appName = "Chrome",
                reward = RewardTerms(
                    grantValue = "+15m",
                    grantLabel = "Per cat",
                    allowanceValue = "45m",
                    allowanceLabel = "Bonus left today",
                    cooldownNote = null,
                ),
            ),
        )
    }
}
