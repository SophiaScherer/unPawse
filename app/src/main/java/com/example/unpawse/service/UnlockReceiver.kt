package com.example.unpawse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.unpawse.data.unlocks.UnlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Counts device unlocks into [UnlockRepository], for the Stats "Unlocks" card.
 *
 * **Context-registered, never declared in the manifest.** `ACTION_USER_PRESENT` is an implicit
 * broadcast and is not on the Android 8 exemption list, so a manifest receiver would simply never
 * fire on any level this app supports. It is registered by [UsageMonitorService], which is already
 * awake for the whole time monitoring is on — so this costs no extra process lifetime, and the
 * metric is honestly scoped to "while monitoring was running", which the card states.
 *
 * `UsageEvents.Event.KEYGUARD_HIDDEN` / `SCREEN_INTERACTIVE` would let the existing `queryEvents`
 * poll carry this instead, and would also cover time the service was down — but both constants
 * arrived in **API 28** and minSdk is 26, so they are deliberately not used.
 *
 * Registered **`RECEIVER_EXPORTED`**, which is not the obvious choice and is load-bearing. The flag
 * gates which *sender* UIDs may reach the receiver, and `ACTION_USER_PRESENT` is sent by the system,
 * not by us — so `RECEIVER_NOT_EXPORTED` registers perfectly (it appears in
 * `dumpsys activity broadcasts` under the right action, in the delivery list) and then simply never
 * calls [onReceive]. That failure is completely silent; it cost a full round of on-device debugging.
 * Exported costs nothing here: `USER_PRESENT` is a protected broadcast, so no third-party app can
 * forge one.
 *
 * The write is dispatched onto the service's [scope] rather than a `goAsync()` PendingResult: the
 * receiver lives exactly as long as the scope does, so there is no window where the coroutine
 * outlives its host, and a single tiny transaction doesn't need the 10-second grace period.
 */
class UnlockReceiver(
    private val scope: CoroutineScope,
    private val unlockRepository: UnlockRepository,
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // Re-checked rather than trusted: a receiver can be handed anything the registrant's filter
        // matched, and this one is registered by hand.
        if (intent?.action != Intent.ACTION_USER_PRESENT) return

        scope.launch {
            runCatching { unlockRepository.recordUnlock() }
                .onFailure { Log.w(TAG, "Could not record an unlock", it) }
        }
    }

    companion object {
        private const val TAG = "UnlockReceiver"
    }
}
