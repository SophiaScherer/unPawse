package com.example.unpawse.ui.onboarding

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.service.CameraAccess
import com.example.unpawse.service.Notifications
import com.example.unpawse.service.OverlayPermission
import com.example.unpawse.service.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the first-run tour. Sequencing, wording and "is this step satisfied?" all live in the pure
 * helpers beside it ([OnboardingStep], [onboardingCopyFor], [toOnboardingUiState]); this class only
 * holds the current step, mirrors the four permissions, and writes the two answers that persist.
 *
 * The permissions are read through lambdas rather than a `Context`, the same seam
 * `SettingsViewModel` uses, so everything above stays testable on the JVM.
 */
class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val usageAccessGranted: () -> Boolean,
    private val overlayAccessGranted: () -> Boolean,
    private val cameraGranted: () -> Boolean,
    private val notificationsGranted: () -> Boolean,
    // The permission steps send the user to system Settings, which is exactly when Android may kill
    // the process; without this the tour would restart at the welcome step on the way back.
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private var currentStep: OnboardingStep
        get() = onboardingStepNamed(savedState[KEY_STEP])
        set(value) {
            savedState[KEY_STEP] = value.name
        }

    private val step = savedState.getStateFlow(KEY_STEP, OnboardingStep.WELCOME.name)
        .map(::onboardingStepNamed)

    /**
     * None of the four can be observed — two are system-Settings switches with no callback at all,
     * and the other two only report through their launcher — so they are re-read on demand; see
     * [refreshPermissions]. Seeded from the saved copy when there is one, so a grant made while the
     * process was dead still reads as "arrived while away" and advances the step.
     */
    private val permissions = MutableStateFlow(
        savedState.get<BooleanArray>(KEY_GRANTS)?.let(::grantsFrom) ?: readPermissions(),
    )

    /**
     * What the user has typed on the name step but not yet committed. Null means "hasn't typed",
     * which is what lets the stored name show through and what Skip goes back to; committing only
     * on Continue keeps a per-keystroke DataStore write off the disk.
     */
    private val nameDraft = savedState.getStateFlow<String?>(KEY_NAME_DRAFT, null)

    /** Mirrored in from the camera permission handle, which is the only thing that knows. */
    private val cameraCanAskSystem = MutableStateFlow(true)

    private val effectiveName = combine(settings.userName, nameDraft) { stored, draft ->
        draft ?: stored
    }

    val uiState: StateFlow<OnboardingUiState> = combine(
        step,
        permissions,
        effectiveName,
        settings.avatarId,
        cameraCanAskSystem,
    ) { currentStep, grants, name, avatarId, canAsk ->
        toOnboardingUiState(
            step = currentStep,
            answers = OnboardingAnswers(userName = name, avatarId = avatarId, grants = grants),
            cameraCanAskSystem = canAsk,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        // The permissions are read synchronously above, so seed them rather than letting the first
        // frame claim nothing is granted — a replayed tour would otherwise flash its whole pitch at
        // someone who has already granted everything.
        initialValue = toOnboardingUiState(
            step = currentStep,
            answers = OnboardingAnswers(grants = permissions.value),
            cameraCanAskSystem = true,
        ),
    )

    /** Moves on, committing anything the current step was holding. */
    fun next() {
        commitDraftFor(currentStep)
        currentStep = nextStep(currentStep)
    }

    /**
     * Moves on leaving what is stored untouched — a typed-then-skipped name is discarded rather than
     * quietly saved. Every step can take this, permissions included.
     */
    fun skip() {
        if (currentStep == OnboardingStep.NAME) savedState[KEY_NAME_DRAFT] = null
        currentStep = nextStep(currentStep)
    }

    /** Back within the tour; on the first step there is nothing above us and this is a no-op. */
    fun back() {
        previousStep(currentStep)?.let { currentStep = it }
    }

    fun setNameDraft(value: String) {
        savedState[KEY_NAME_DRAFT] = value
    }

    /** Persisted on the tap: a discrete choice, and seeing it stick immediately is the point. */
    fun setAvatarId(id: Int) = viewModelScope.launch { settings.setAvatarId(id) }

    fun setCameraCanAskSystem(canAsk: Boolean) {
        cameraCanAskSystem.value = canAsk
    }

    /**
     * Re-reads all four permissions and advances if the current step's one just arrived.
     *
     * Call this on every resume. Usage access and the overlay are granted in system Settings, which
     * tells the app nothing on the way back: without this the user flips the switch, returns, and
     * finds the same screen still asking — as though the trip had done nothing at all. The two
     * runtime permissions go through the same path so the dialog's result lands the same way.
     */
    fun refreshPermissions() {
        val before = permissions.value
        val after = readPermissions()
        savedState[KEY_GRANTS] = after.toArray()
        if (before == after) return
        permissions.value = after
        currentStep = stepAfterGrant(currentStep, before, after)
    }

    /**
     * Marks the tour done and hands off. The flag is written before [onFinished] runs, so the
     * navigation that leaves onboarding can't outrun the write that stops it coming back.
     */
    fun complete(onFinished: () -> Unit) {
        // A double tap on the last button would otherwise navigate twice.
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            commitDraftFor(currentStep)
            settings.setOnboardingComplete(true)
            onFinished()
        }
    }

    private var finishing = false

    /**
     * Continue's half of the name step. Like [skip] it leaves no draft behind — the difference is
     * only whether the draft was written first — so coming back to the step shows what is stored.
     */
    private fun commitDraftFor(current: OnboardingStep) {
        if (current != OnboardingStep.NAME) return
        val draft = nameDraft.value ?: return
        viewModelScope.launch {
            // Trimmed like the Settings dialog, so trailing spaces can't make a blank name "set".
            settings.setUserName(draft.trim())
            // Cleared only after the write lands, so the field never flashes the old stored name.
            savedState[KEY_NAME_DRAFT] = null
        }
    }

    private fun readPermissions() = PermissionGrants(
        usageAccess = usageAccessGranted(),
        overlayAccess = overlayAccessGranted(),
        camera = cameraGranted(),
        notifications = notificationsGranted(),
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val KEY_STEP = "step"
        private const val KEY_NAME_DRAFT = "name_draft"
        private const val KEY_GRANTS = "grants"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                OnboardingViewModel(
                    settings = context.appContainer().settingsRepository,
                    usageAccessGranted = { UsageAccess.isGranted(appContext) },
                    overlayAccessGranted = { OverlayPermission.isGranted(appContext) },
                    cameraGranted = { CameraAccess.isGranted(appContext) },
                    notificationsGranted = { Notifications.canPost(appContext) },
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}

/** Tolerant of a name this build doesn't know, so stale saved state restarts the tour, not the app. */
internal fun onboardingStepNamed(name: String?): OnboardingStep =
    OnboardingStep.entries.firstOrNull { it.name == name } ?: OnboardingStep.WELCOME

private fun PermissionGrants.toArray() =
    booleanArrayOf(usageAccess, overlayAccess, camera, notifications)

private fun grantsFrom(saved: BooleanArray): PermissionGrants? =
    if (saved.size != 4) null else PermissionGrants(saved[0], saved[1], saved[2], saved[3])
