package vn.uit.healthos.feature.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Vietnamese Text-to-Speech controller for reading AI doctor answers aloud. */
class VoiceOutput(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val vi = Locale.forLanguageTag("vi-VN")
                val res = tts?.setLanguage(vi)
                ready = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        val cleaned = text.replace("**", "").replace("*", "").replace("#", "")
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "healthos_ai_tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
    }
}
