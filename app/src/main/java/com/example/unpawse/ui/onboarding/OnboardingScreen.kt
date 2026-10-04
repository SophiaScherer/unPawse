package com.example.unpawse.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.settings.AVATAR_NONE
import com.example.unpawse.data.settings.CatAvatar
import com.example.unpawse.ui.components.CatAvatarImage
import com.example.unpawse.ui.components.InitialsAvatar
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.components.ProfileAvatar
import com.example.unpawse.ui.format.avatarInitialFor
import com.example.unpawse.ui.format.capDisplayName
import com.example.unpawse.ui.format.displayNameOf
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.FieldShape
import com.example.unpawse.ui.theme.UnPawseTheme
import com.example.unpawse.ui.theme.unPawseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * The first-run tour: one full-screen destination that walks [OnboardingStep], with no bottom bar
 * (the hosting scaffold hides it, the way it already does for the block overlay).
 *
 * Stateless like every other screen here — it renders [state] and reports taps. Which button a step
 * shows, what it says, and whether it reads as satisfied are all decided upstream in the pure
 * helpers, so this file is layout.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    modifier: Modifier = Modifier,
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    onSkip: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onAvatarSelected: (Int) -> Unit = {},
    onGrant: (OnboardingStep) -> Unit = {},
    onFinish: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // The host already pads for the navigation bar; this adds only the part of the keyboard
            // above it, so the name step's buttons ride on top of the keyboard instead of under it.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .padding(horizontal = Dimens.ScreenHMargin),
    ) {
        OnboardingProgress(state = state, onBack = onBack)

        // A fresh scroll position per step, so a long step scrolled to its end doesn't open the next
        // one halfway down.
        val scrollState = remember(state.step) { ScrollState(initial = 0) }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    // At least the viewport tall, so a short step sits centered instead of clinging
                    // to the top over a screen-high gap; a long one still scrolls.
                    .heightIn(min = viewport),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.Gutter, Alignment.CenterVertically),
            ) {
                Spacer(Modifier.height(Dimens.Base))
                // On a short screen the hero gives way to the words; it is decoration, they are not.
                StepHero(state = state, size = if (viewport < COMPACT_HEIGHT) 72.dp else 104.dp)
                Text(
                    text = state.copy.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = state.copy.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                StepDetail(
                    state = state,
                    onNameChange = onNameChange,
                    onAvatarSelected = onAvatarSelected,
                    onNext = onNext,
                )
                Spacer(Modifier.height(Dimens.Base))
            }
            // Without it a long step reads as ending mid-sentence at the button, with no hint
            // that the rest is a scroll away.
            if (scrollState.canScrollForward) {
                val ground = MaterialTheme.colorScheme.background
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Brush.verticalGradient(listOf(ground.copy(alpha = 0f), ground))),
                )
            }
        }

        OnboardingActions(
            state = state,
            onNext = onNext,
            onSkip = onSkip,
            onGrant = onGrant,
            onFinish = onFinish,
        )
    }
}

/** Below this the tour is on a small phone or at a large font scale, and the hero shrinks. */
private val COMPACT_HEIGHT = 480.dp

/** Back arrow plus "Step n of m" and a thin progress bar; the arrow hides on the first step. */
@Composable
private fun OnboardingProgress(state: OnboardingUiState, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.Base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Base),
    ) {
        if (state.canGoBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            // Holds the row's height steady so the title doesn't jump between step one and two.
            Spacer(Modifier.size(48.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Step ${state.stepNumber} of ${state.stepCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Spacer(Modifier.size(48.dp))
    }
}

/** The step's illustration: the chosen cat where there is one, otherwise a tinted icon tile. */
@Composable
private fun StepHero(state: OnboardingUiState, size: Dp) {
    val granted = isPermissionStep(state.step) && state.satisfied
    if (state.step == OnboardingStep.DONE || state.step == OnboardingStep.AVATAR) {
        ProfileAvatar(
            avatarId = state.answers.avatarId,
            initial = avatarInitialFor(state.answers.userName),
            size = size,
        )
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (granted) {
                    MaterialTheme.unPawseColors.successContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else iconFor(state.step),
            contentDescription = null,
            tint = if (granted) {
                MaterialTheme.unPawseColors.success
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

private fun iconFor(step: OnboardingStep): ImageVector = when (step) {
    OnboardingStep.WELCOME -> Icons.Filled.Pets
    OnboardingStep.HOW_IT_WORKS -> Icons.Filled.AutoAwesome
    OnboardingStep.NAME -> Icons.Filled.Person
    OnboardingStep.AVATAR -> Icons.Filled.Face
    OnboardingStep.USAGE_ACCESS -> Icons.Filled.Shield
    OnboardingStep.OVERLAY_ACCESS -> Icons.Filled.Layers
    OnboardingStep.CAMERA -> Icons.Filled.PhotoCamera
    OnboardingStep.NOTIFICATIONS -> Icons.Filled.NotificationsActive
    OnboardingStep.DONE -> Icons.Filled.Celebration
}

/** Whatever a step needs beyond its title and body: the three beats, a field, a picker, a recap. */
@Composable
private fun StepDetail(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onAvatarSelected: (Int) -> Unit,
    onNext: () -> Unit,
) {
    when (state.step) {
        OnboardingStep.HOW_IT_WORKS -> LoopBeats()

        OnboardingStep.NAME -> NameField(
            value = state.answers.userName,
            onValueChange = { onNameChange(capDisplayName(it)) },
            onDone = onNext,
        )

        OnboardingStep.AVATAR -> AvatarPicker(
            selectedId = state.answers.avatarId,
            initial = avatarInitialFor(state.answers.userName),
            onAvatarSelected = onAvatarSelected,
        )


        OnboardingStep.CAMERA ->
            // The handle has stopped being able to raise the dialog, so say why the button now
            // leads somewhere else instead of letting it look broken.
            if (!state.satisfied && !state.cameraCanAskSystem) {
                PermissionNote(
                    "Android has stopped offering the camera prompt for unPawse. The button " +
                        "below opens unPawse's app settings, where Permissions → Camera can " +
                        "be switched on.",
                )
            }

        OnboardingStep.NOTIFICATIONS ->
            if (!state.satisfied && !state.notificationsCanAskSystem) {
                PermissionNote(
                    "Android won't show the notification prompt for unPawse any more, or " +
                        "notifications are switched off for it. The button below opens its " +
                        "notification settings instead.",
                )
            }

        OnboardingStep.DONE -> DoneRecap(state)

        else -> Unit
    }
}

/**
 * The name field. On a short screen it sits below the fold, and the scroll the text field does on
 * focus happens before the keyboard has finished shrinking the viewport — so it is brought into view
 * again each time the keyboard's height settles, or the user types without seeing the field.
 */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        snapshotFlow { imeInsets.getBottom(density) }.collectLatest {
            // Latest-only: mid-animation heights are superseded before this fires.
            delay(IME_SETTLE_MILLIS)
            requester.bringIntoView()
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        shape = FieldShape,
        label = { Text("Your name") },
        placeholder = { Text("Leave it blank and we'll say \"friend\"") },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        // Done is the same answer as the Continue button, so it moves on rather than just
        // dropping the keyboard and leaving the user to find the button.
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

private const val IME_SETTLE_MILLIS = 60L

/** The loop in three beats — the tutorial proper. */
@Composable
private fun LoopBeats() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
    ) {
        val icons = listOf(Icons.Filled.Timer, Icons.Filled.Block, Icons.Filled.Pets)
        LOOP_BEATS.forEachIndexed { index, beat ->
            PawCard(contentPadding = Dimens.Gutter) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icons[index],
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${index + 1}. ${beat.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = beat.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The preset cats plus a "no picture" tile that keeps the initials avatar. Laid out in rows of
 * three rather than a grid so a later custom-cat option can be appended without a re-layout.
 */
@Composable
private fun AvatarPicker(
    selectedId: Int,
    initial: Char,
    onAvatarSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
    ) {
        val options: List<CatAvatar?> = listOf<CatAvatar?>(null) + CatAvatar.entries
        options.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.StackGap),
            ) {
                row.forEach { avatar ->
                    val id = avatar?.id ?: AVATAR_NONE
                    AvatarOption(
                        selected = selectedId == id,
                        label = avatar?.label ?: "No picture",
                        onClick = { onAvatarSelected(id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (avatar == null) {
                            InitialsAvatar(initial = initial, size = 64.dp)
                        } else {
                            CatAvatarImage(avatar = avatar, size = 64.dp)
                        }
                    }
                }
                // Keeps the last row's tiles the same width as the full rows above it.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AvatarOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            // Selectable, not clickable, so TalkBack announces which cat is the current one.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Dimens.Base),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Closing recap: who we'll greet, and an honest list of whatever was skipped. */
@Composable
private fun DoneRecap(state: OnboardingUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
    ) {
        Text(
            text = "See you on Home, ${displayNameOf(state.answers.userName)}.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.missingPermissions.isNotEmpty()) {
            PawCard(contentPadding = Dimens.Gutter) {
                Text(
                    text = "Still off, and that's fine",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Dimens.Base))
                state.missingPermissions.forEach { step ->
                    Text(
                        text = "• ${missingPermissionLabel(step)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Dimens.Base))
                Text(
                    text = missingPermissionsFootnote(state.missingPermissions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionNote(text: String) {
    PawCard(contentPadding = Dimens.Gutter) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The pill primary plus the skip beneath it. The primary asks for the permission while one is
 * outstanding and otherwise moves on, so a granted step reads as a plain "Continue".
 */
@Composable
private fun OnboardingActions(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onGrant: (OnboardingStep) -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.Base, bottom = Dimens.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = {
                when {
                    state.step == OnboardingStep.DONE -> onFinish()
                    isPermissionStep(state.step) && !state.satisfied -> onGrant(state.step)
                    else -> onNext()
                }
            },
            // A floor, not a fixed height: at large font scales the label needs room to grow.
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = CircleShape,
        ) {
            Text(
                text = state.copy.primaryLabel,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        val skipLabel = state.copy.secondaryLabel
        if (skipLabel != null) {
            TextButton(onClick = onSkip) {
                Text(skipLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Reserves the skip row's height so the primary button doesn't shift between steps.
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Preview(name = "Onboarding · welcome", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 780)
@Composable
private fun OnboardingWelcomePreview() {
    UnPawseTheme { OnboardingScreen(state = OnboardingUiState.sample()) }
}

@Preview(name = "Onboarding · how it works", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 900)
@Composable
private fun OnboardingHowItWorksPreview() {
    UnPawseTheme {
        OnboardingScreen(state = OnboardingUiState.sample(OnboardingStep.HOW_IT_WORKS))
    }
}

@Preview(name = "Onboarding · avatar", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 900)
@Composable
private fun OnboardingAvatarPreview() {
    UnPawseTheme { OnboardingScreen(state = OnboardingUiState.sample(OnboardingStep.AVATAR)) }
}

@Preview(name = "Onboarding · camera", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 780)
@Composable
private fun OnboardingCameraPreview() {
    UnPawseTheme { OnboardingScreen(state = OnboardingUiState.sample(OnboardingStep.CAMERA)) }
}

@Preview(name = "Onboarding · done · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 900)
@Composable
private fun OnboardingDonePreview() {
    UnPawseTheme(darkTheme = true) {
        OnboardingScreen(state = OnboardingUiState.sample(OnboardingStep.DONE))
    }
}
