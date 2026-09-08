package com.example.unpawse.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val step = MutableStateFlow(OnboardingStep.WELCOME)

    /**
     * None of the four can be observed — two are system-Settings switches with no callback at all,
     * and the other two only report through their launcher — so they are re-read on demand; see
     * [refreshPermissions].
     */
    private val permissions = MutableStateFlow(readPermissions())

    /**
     * What the user has typed on the name step but not yet committed. Null means "hasn't typed",
     * which is what lets the stored name show through and what Skip goes back to; committing only
     * on Continue keeps a per-keystroke DataStore write off the disk.
     */
    private val nameDraft = MutableStateFlow<String?>(null)

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
            step = step.value,
            answers = OnboardingAnswers(grants = permissions.value),
            cameraCanAskSystem = true,
        ),
    )

    /** Moves on, committing anything the current step was holding. */
    fun next() {
        commitDraftFor(step.value)
        step.value = nextStep(step.value)
    }

    /**
     * Moves on without keeping what the step collected — a typed-then-skipped name is discarded
     * rather than quietly saved. Every step can take this, permissions included.
     */
    fun skip() {
        if (step.value == OnboardingStep.NAME) nameDraft.value = null
        step.value = nextStep(step.value)
    }

    /** Back within the tour; on the first step there is nothing above us and this is a no-op. */
    fun back() {
        previousStep(step.value)?.let { step.value = it }
    }

    fun setNameDraft(value: String) {
        nameDraft.value = value
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
        if (before == after) return
        permissions.value = after
        step.value = stepAfterGrant(step.value, before, after)
    }

    /**
     * Marks the tour done and hands off. The flag is written before [onFinished] runs, so the
     * navigation that leaves onboarding can't outrun the write that stops it coming back.
     */
    fun complete(onFinished: () -> Unit) = viewModelScope.launch {
        commitDraftFor(step.value)
        settings.setOnboardingComplete(true)
        onFinished()
    }

    private fun commitDraftFor(current: OnboardingStep) {
        if (current != OnboardingStep.NAME) return
        val draft = nameDraft.value ?: return
        // Trimmed like the Settings dialog, so trailing spaces can't make a blank name "set".
        viewModelScope.launch { settings.setUserName(draft.trim()) }
    }

    private fun readPermissions() = PermissionGrants(
        usageAccess = usageAccessGranted(),
        overlayAccess = overlayAccessGranted(),
        camera = cameraGranted(),
        notifications = notificationsGranted(),
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                OnboardingViewModel(
                    settings = context.appContainer().settingsRepository,
                    usageAccessGranted = { UsageAccess.isGranted(appContext) },
                    overlayAccessGranted = { OverlayPermission.isGranted(appContext) },
                    cameraGranted = { CameraAccess.isGranted(appContext) },
                    notificationsGranted = { Notifications.canPost(appContext) },
                )
            }
        }
    }
}
