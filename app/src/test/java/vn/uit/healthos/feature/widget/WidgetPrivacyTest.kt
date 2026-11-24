package vn.uit.healthos.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPrivacyTest {

    @Test
    fun signedOutHidesDataWhateverTheLock() {
        assertEquals(WidgetAccess.SIGNED_OUT, WidgetPrivacy.decide(signedIn = false, appLockEnabled = false))
        assertEquals(WidgetAccess.SIGNED_OUT, WidgetPrivacy.decide(signedIn = false, appLockEnabled = true))
    }

    @Test
    fun appLockHidesDataWhenSignedIn() {
        assertEquals(WidgetAccess.LOCKED, WidgetPrivacy.decide(signedIn = true, appLockEnabled = true))
    }

    @Test
    fun dataShowsOnlyWhenSignedInAndUnlocked() {
        assertEquals(WidgetAccess.VISIBLE, WidgetPrivacy.decide(signedIn = true, appLockEnabled = false))
    }

    @Test
    fun gateNeverReadsTheValueWhenHidden() {
        var reads = 0
        val locked = WidgetPrivacy.gate(WidgetAccess.LOCKED) { reads++; 42 }
        val signedOut = WidgetPrivacy.gate(WidgetAccess.SIGNED_OUT) { reads++; 42 }

        assertEquals(WidgetContent.Locked, locked)
        assertEquals(WidgetContent.SignedOut, signedOut)
        assertEquals(0, reads)
    }

    @Test
    fun gateCarriesTheValueWhenVisible() {
        assertEquals(WidgetContent.Visible(42), WidgetPrivacy.gate(WidgetAccess.VISIBLE) { 42 })
    }
}
