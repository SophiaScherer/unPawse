package com.example.unpawse.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Getting [canAskSystemForCamera] wrong is user-visible either way: too strict strands the user on a
 * dead button, too loose throws them into system Settings when a dialog would have done. The rest of
 * `CameraAccess` is `Context`-bound and verified on device.
 */
class CameraAccessTest {

    @Test
    fun `before the first ask the system can always be asked`() {
        assertTrue(canAskSystemForCamera(askedOnce = false, showRationale = false))
    }

    @Test
    fun `a rationale offer before asking still means the system can be asked`() {
        assertTrue(canAskSystemForCamera(askedOnce = false, showRationale = true))
    }

    @Test
    fun `after one denial the rationale offer means the dialog will come back`() {
        assertTrue(canAskSystemForCamera(askedOnce = true, showRationale = true))
    }

    @Test
    fun `asked and no rationale offered is the permanent denial`() {
        assertFalse(canAskSystemForCamera(askedOnce = true, showRationale = false))
    }
}
