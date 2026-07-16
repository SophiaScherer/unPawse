package com.example.unpawse.service

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.unpawse.ui.block.BlockOverlayScreen
import com.example.unpawse.ui.block.BlockUiState
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * One instance of the block overlay: a [ComposeView] in a `TYPE_APPLICATION_OVERLAY` window drawn
 * over whatever app the user is in.
 *
 * Compose normally gets its lifecycle/saved-state/ViewModel owners from an Activity. There isn't
 * one here — the window belongs to a Service — so this class *is* those owners. Instances are
 * single-use: a [LifecycleRegistry] can't return from `DESTROYED`, so [BlockOverlayController]
 * builds a fresh host per show rather than recycling one.
 */
private class BlockOverlayHost(
    private val context: Context,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null

    fun show(state: BlockUiState, onOpenCamera: () -> Unit, onExit: () -> Unit) {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@BlockOverlayHost)
            setViewTreeViewModelStoreOwner(this@BlockOverlayHost)
            setViewTreeSavedStateRegistryOwner(this@BlockOverlayHost)
            setContent {
                UnPawseTheme {
                    BlockOverlayScreen(state = state, onOpenCamera = onOpenCamera, onExit = onExit)
                }
            }
        }

        windowManager.addView(composeView, layoutParams())
        view = composeView
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun dismiss() {
        view?.let { attached ->
            runCatching { windowManager.removeView(attached) }
                .onFailure { Log.w(TAG, "Overlay already detached", it) }
        }
        view = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    /**
     * Full-screen and deliberately **touchable and focusable** (no `FLAG_NOT_TOUCHABLE`): the whole
     * point is to swallow input to the app underneath, otherwise the user could keep scrolling
     * behind the block.
     */
    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    private companion object {
        const val TAG = "BlockOverlayHost"
    }
}

/**
 * Shows/hides the block overlay. Owned as a singleton by the AppContainer so the service can raise
 * it and the reward loop (Phase 6) can dismiss it.
 *
 * **Main thread only** — `WindowManager.addView` requires it.
 */
class BlockOverlayController(private val context: Context) {

    private var host: BlockOverlayHost? = null

    /** The package currently blocked, or null if no overlay is up. */
    var blockedPackage: String? = null
        private set

    val isShowing: Boolean get() = host != null

    /**
     * Raises the overlay for [packageName]. No-ops if one is already up (the tracker only signals
     * once per breach, but a re-signal while showing shouldn't stack windows) or if the overlay
     * permission is missing — in which case we'd have no way to draw and shouldn't pretend.
     */
    fun show(
        packageName: String,
        state: BlockUiState,
        onOpenCamera: () -> Unit,
        onExit: () -> Unit,
    ) {
        if (host != null || !OverlayPermission.isGranted(context)) return

        host = BlockOverlayHost(context).apply { show(state, onOpenCamera, onExit) }
        blockedPackage = packageName
    }

    fun hide() {
        host?.dismiss()
        host = null
        blockedPackage = null
    }
}
