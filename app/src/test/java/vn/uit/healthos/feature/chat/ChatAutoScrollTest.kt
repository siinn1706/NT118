package vn.uit.healthos.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ChatAutoScrollTest {

    private fun msg(id: String, isUser: Boolean = false, content: String = "x") =
        ChatMessageUi(id = id, isUser = isUser, content = content)

    private fun snap(vararg messages: ChatMessageUi) = ChatAutoScroll.snapshotOf(messages.toList())

    @Test
    fun firstLoadScrollsToEnd() {
        assertTrue(ChatAutoScroll.shouldScrollToEnd(null, snap(msg("a"), msg("b")), followingBottom = false))
    }

    @Test
    fun emptyListNeverScrolls() {
        assertFalse(ChatAutoScroll.shouldScrollToEnd(null, snap(), followingBottom = true))
    }

    @Test
    fun olderHistoryPrependedDoesNotScroll() {
        val before = snap(msg("c"), msg("d"))
        val after = snap(msg("a"), msg("b"), msg("c"), msg("d"))
        assertFalse(ChatAutoScroll.shouldScrollToEnd(before, after, followingBottom = true))
        assertFalse(ChatAutoScroll.shouldScrollToEnd(before, after, followingBottom = false))
    }

    @Test
    fun sentMessageAlwaysScrolls() {
        val before = snap(msg("a"))
        val after = snap(msg("a"), msg("u1", isUser = true))
        assertTrue(ChatAutoScroll.shouldScrollToEnd(before, after, followingBottom = false))
    }

    @Test
    fun newReplyFollowedOnlyAtBottom() {
        val before = snap(msg("u1", isUser = true))
        val after = snap(msg("u1", isUser = true), msg("r1"))
        assertTrue(ChatAutoScroll.shouldScrollToEnd(before, after, followingBottom = true))
        assertFalse(ChatAutoScroll.shouldScrollToEnd(before, after, followingBottom = false))
    }

    @Test
    fun streamingTextFollowedOnlyAtBottom() {
        val before = snap(msg("r1", content = "Hel"))
        val after = snap(msg("r1", content = "Hello"))
        assertTrue(ChatAutoScroll.shouldScrollToEnd(before, after, followingBottom = true))
        assertFalse(ChatAutoScroll.shouldScrollToEnd(before, after, followingBottom = false))
    }

    @Test
    fun unchangedListDoesNotScroll() {
        val s = snap(msg("a"))
        assertFalse(ChatAutoScroll.shouldScrollToEnd(s, s, followingBottom = true))
    }

    @Test
    fun localeFollowsDeviceLanguage() {
        assertEquals("vi", ChatLocale.of(Locale.forLanguageTag("vi-VN")))
        assertEquals("en", ChatLocale.of(Locale.US))
        assertEquals("en", ChatLocale.of(Locale.JAPAN))
    }
}
