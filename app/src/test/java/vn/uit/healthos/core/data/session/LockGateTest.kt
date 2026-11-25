package vn.uit.healthos.core.data.session

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.uit.healthos.LockGate
import vn.uit.healthos.resolveLockGate

/** The app content must never be shown before the app lock decision is known. */
class LockGateTest {

    @Test
    fun unknownUntilPreferencesAndSessionLoad() {
        assertEquals(LockGate.Unknown, gate(prefsLoaded = false, signedIn = true))
        assertEquals(LockGate.Unknown, gate(prefsLoaded = true, signedIn = null))
    }

    @Test
    fun coldStartWithLockOnStaysLockedUntilTheManagerLocks() {
        // The lock manager has not applied its cold-start lock yet: still treated as locked.
        assertEquals(LockGate.Locked, gate(lockEnabled = true, signedIn = true, isLocked = false, settled = false))
        assertEquals(LockGate.Locked, gate(lockEnabled = true, signedIn = true, isLocked = true, settled = false))
    }

    @Test
    fun openAfterUnlock() {
        assertEquals(LockGate.Open, gate(lockEnabled = true, signedIn = true, isLocked = false, settled = true))
    }

    @Test
    fun lockedWheneverTheManagerSaysSo() {
        assertEquals(LockGate.Locked, gate(lockEnabled = true, signedIn = true, isLocked = true, settled = true))
    }

    @Test
    fun openWithoutLockOrSession() {
        assertEquals(LockGate.Open, gate(lockEnabled = false, signedIn = true))
        assertEquals(LockGate.Open, gate(lockEnabled = true, signedIn = false))
    }

    private fun gate(
        prefsLoaded: Boolean = true,
        lockEnabled: Boolean = false,
        signedIn: Boolean? = true,
        isLocked: Boolean = false,
        settled: Boolean = false,
    ) = resolveLockGate(prefsLoaded, lockEnabled, signedIn, isLocked, settled)
}
