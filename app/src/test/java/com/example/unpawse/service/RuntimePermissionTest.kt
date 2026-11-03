package com.example.unpawse.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Getting [canAskSystemAgain] wrong is user-visible either way: too strict throws the user into
 * system Settings when a dialog would have done, too loose leaves a button that silently does
 * nothing. The `Context`-bound half is verified on device.
 */
class RuntimePermissionTest {

    private fun canAsk(
        dialogExists: Boolean = true,
        platformGranted: Boolean = false,
        askedOnce: Boolean = false,
        showRationale: Boolean = false,
    ) = canAskSystemAgain(dialogExists, platformGranted, askedOnce, showRationale)

    @Test
    fun `before the first ask the system can always be asked`() {
        assertTrue(canAsk(askedOnce = false, showRationale = false))
    }

    @Test
    fun `a rationale offer before asking still means the system can be asked`() {
        assertTrue(canAsk(askedOnce = false, showRationale = true))
    }

    @Test
    fun `after one denial the rationale offer means the dialog will come back`() {
        assertTrue(canAsk(askedOnce = true, showRationale = true))
    }

    @Test
    fun `asked and no rationale offered is the permanent denial`() {
        assertFalse(canAsk(askedOnce = true, showRationale = false))
    }

    /** Notifications below API 33: there is nothing to launch, only settings. */
    @Test
    fun `no dialog on this platform means settings`() {
        assertFalse(canAsk(dialogExists = false))
    }

    /** Granted but switched off in system settings: a dialog would return at once and change nothing. */
    @Test
    fun `a permission the platform already granted cannot be asked for again`() {
        assertFalse(canAsk(platformGranted = true))
    }
}
