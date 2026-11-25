package vn.uit.healthos.core.designsystem.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassOverlayHostTest {

    @Test
    fun `a dialog closed while overlays are suppressed leaves without coming back`() {
        val host = GlassOverlayHost()
        val open = GlassOverlayEntry {}
        val closing = GlassOverlayEntry {}
        host.show(open)
        host.show(closing)
        assertTrue(host.hasModal)

        host.suppressed = true
        host.hide(closing)

        assertEquals(listOf(open), host.entries.toList())
        assertTrue(open.visibility.targetState)
    }

    @Test
    fun `suppressing drops dialogs that were already closing`() {
        val host = GlassOverlayHost()
        val entry = GlassOverlayEntry {}
        host.show(entry)
        host.hide(entry)

        host.suppressed = true

        assertTrue(host.entries.isEmpty())
        assertFalse(host.hasModal)
    }
}
