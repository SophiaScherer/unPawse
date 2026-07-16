package com.example.unpawse.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bonus time a single verified cat buys back. Matches the mockup's "+15m" gallery chip. */
const val BONUS_MINUTES_PER_CAT = 15

/**
 * The app the user is currently trying to earn time back for — the thread connecting the block to
 * the camera.
 *
 * It deliberately outlives the overlay: "Open Camera" takes the overlay down (it would otherwise
 * cover the viewfinder) but the debt is still owed, so the session stays armed until a cat is
 * captured or the user walks away via "Exit App". Held as an AppContainer singleton because the
 * service arms it and the camera ViewModel settles it.
 */
class BlockSession {

    private val _blockedPackage = MutableStateFlow<String?>(null)

    /** The blocked package, or null when nothing is owed. */
    val blockedPackage: StateFlow<String?> = _blockedPackage.asStateFlow()

    fun start(packageName: String) {
        _blockedPackage.value = packageName
    }

    fun clear() {
        _blockedPackage.value = null
    }
}
