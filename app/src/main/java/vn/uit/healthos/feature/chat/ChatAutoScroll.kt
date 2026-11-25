package vn.uit.healthos.feature.chat

import java.util.Locale

/**
 * When the message list jumps to its end. Older history loaded at the top never moves the reader; a message the user
 * just sent always shows; anything else (a new reply, streaming text growing) is followed only while the reader is
 * already at the bottom.
 */
object ChatAutoScroll {

    /** What the list looked like at one point, enough to tell an append from a prepend or a streaming update. */
    data class Snapshot(
        val count: Int,
        val lastId: String?,
        val lastIsUser: Boolean,
        val lastContentLength: Int,
    )

    fun snapshotOf(messages: List<ChatMessageUi>): Snapshot {
        val last = messages.lastOrNull()
        return Snapshot(
            count = messages.size,
            lastId = last?.id,
            lastIsUser = last?.isUser == true,
            lastContentLength = last?.content?.length ?: 0,
        )
    }

    /** True when the list should scroll to its end after changing from [previous] to [next]. */
    fun shouldScrollToEnd(previous: Snapshot?, next: Snapshot, followingBottom: Boolean): Boolean {
        if (next.count == 0) return false
        if (previous == null || previous.count == 0) return true
        val lastChanged = previous.lastId != next.lastId
        return when {
            lastChanged && next.lastIsUser -> true
            lastChanged -> followingBottom
            next.lastContentLength != previous.lastContentLength -> followingBottom
            else -> false
        }
    }
}

/** The reply language asked of the AI doctor: Vietnamese on a Vietnamese device, English otherwise. */
object ChatLocale {
    fun of(locale: Locale): String = if (locale.language.equals("vi", ignoreCase = true)) "vi" else "en"
}
