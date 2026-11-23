package vn.uit.healthos.core.data.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun staysUnlockedBeforeTimeoutExpires() {
        assertFalse(
            shouldLockOnResume(
                lockEnabled = true,
                isSignedIn = true,
                backgroundedAtMs = 10_000L,
                nowMs = 69_999L,
            ),
        )
    }

    @Test
    fun locksAtAndAfterSixtySecondThreshold() {
        assertTrue(
            shouldLockOnResume(
                lockEnabled = true,
                isSignedIn = true,
                backgroundedAtMs = 10_000L,
                nowMs = 70_000L,
            ),
        )
    }

    @Test
    fun neverLocksWhenDisabledOrSignedOut() {
        assertFalse(shouldLockOnResume(lockEnabled = false, isSignedIn = true, backgroundedAtMs = 0L, nowMs = 999_999L))
        assertFalse(shouldLockOnResume(lockEnabled = true, isSignedIn = false, backgroundedAtMs = 0L, nowMs = 999_999L))
    }
}
