package vn.uit.healthos.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetIntentsTest {

    @Test
    fun destinationsRoundTripThroughTheirWireNames() {
        WidgetIntents.Destination.entries.forEach { destination ->
            assertEquals(destination, WidgetIntents.Destination.fromWire(destination.wire))
        }
        assertNull(WidgetIntents.Destination.fromWire("settings"))
        assertNull(WidgetIntents.Destination.fromWire(null))
    }

    @Test
    fun promptIsOnlyReadForTheChat() {
        assertEquals("Hôm nay ăn gì?", WidgetIntents.chatPrompt("chat", "Hôm nay ăn gì?"))
        assertNull(WidgetIntents.chatPrompt("home", "Hôm nay ăn gì?"))
        assertNull(WidgetIntents.chatPrompt(null, "Hôm nay ăn gì?"))
    }

    @Test
    fun blankPromptsAreDropped() {
        assertNull(WidgetIntents.sanitizePrompt(null))
        assertNull(WidgetIntents.sanitizePrompt(""))
        assertNull(WidgetIntents.sanitizePrompt("   \n "))
        assertNull(WidgetIntents.chatPrompt("chat", "  "))
    }

    @Test
    fun promptIsTrimmedAndStrippedOfControlCharacters() {
        assertEquals(
            "Nhịp tim\nbình thường?",
            WidgetIntents.sanitizePrompt("  Nhịp\u0000 tim\nbình\u0007 thường? \t"),
        )
    }

    @Test
    fun longPromptsAreCapped() {
        val prompt = WidgetIntents.sanitizePrompt("a".repeat(WidgetIntents.MAX_PROMPT_LENGTH + 50))

        assertEquals(WidgetIntents.MAX_PROMPT_LENGTH, prompt!!.length)
    }
}
