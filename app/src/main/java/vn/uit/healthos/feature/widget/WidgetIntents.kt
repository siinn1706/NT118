package vn.uit.healthos.feature.widget

import android.content.Context
import android.content.Intent
import vn.uit.healthos.MainActivity
import vn.uit.healthos.navigation.ChatRoute

/**
 * Intents that widgets use to open the app on a given screen. `MainActivity` reads them back with [destinationFrom]
 * and [chatRouteFrom]; the app's normal session and app-lock checks still run before the screen is shown.
 */
object WidgetIntents {
    const val EXTRA_DESTINATION = "vn.uit.healthos.extra.WIDGET_DESTINATION"
    const val EXTRA_PROMPT = "vn.uit.healthos.extra.CHAT_PROMPT"

    /** Longest prompt accepted from an intent; the chat composer is for a question, not a document. */
    const val MAX_PROMPT_LENGTH = 500

    enum class Destination(val wire: String) {
        HOME("home"),
        MEDS("meds"),
        MEAL_SNAP("meal_snap"),
        CHAT("chat"),
        ;

        companion object {
            fun fromWire(wire: String?): Destination? = entries.firstOrNull { it.wire == wire }
        }
    }

    /** Opens `MainActivity` on [destination]; for [Destination.CHAT], [prompt] prefills the composer (never sent). */
    fun open(context: Context, destination: Destination, prompt: String? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            // A distinct action per target keeps PendingIntents for different chips from replacing each other.
            action = "vn.uit.healthos.action.WIDGET_OPEN." + destination.wire + (prompt?.let { "." + it.hashCode() } ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_DESTINATION, destination.wire)
            sanitizePrompt(prompt)?.let { putExtra(EXTRA_PROMPT, it) }
        }

    fun destinationFrom(intent: Intent?): Destination? =
        Destination.fromWire(intent?.getStringExtra(EXTRA_DESTINATION))

    /** The prompt to prefill, only when the intent targets the chat. */
    fun promptFrom(intent: Intent?): String? = chatPrompt(
        destinationWire = intent?.getStringExtra(EXTRA_DESTINATION),
        rawPrompt = intent?.getStringExtra(EXTRA_PROMPT),
    )

    /** The chat route to navigate to, or null when the intent does not target the chat. */
    fun chatRouteFrom(intent: Intent?): ChatRoute? =
        if (destinationFrom(intent) == Destination.CHAT) ChatRoute(prompt = promptFrom(intent)) else null

    /** Removes the extras once handled, so a configuration change or `onNewIntent` replay does not reopen the screen. */
    fun consume(intent: Intent?) {
        intent?.removeExtra(EXTRA_DESTINATION)
        intent?.removeExtra(EXTRA_PROMPT)
    }

    fun chatPrompt(destinationWire: String?, rawPrompt: String?): String? =
        if (Destination.fromWire(destinationWire) == Destination.CHAT) sanitizePrompt(rawPrompt) else null

    /** Trims, drops control characters and caps the length; blank prompts become null. */
    fun sanitizePrompt(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.filter { it == '\n' || !it.isISOControl() }.trim()
        return cleaned.take(MAX_PROMPT_LENGTH).trimEnd().ifEmpty { null }
    }
}
