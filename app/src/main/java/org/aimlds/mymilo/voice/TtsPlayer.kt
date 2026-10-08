package org.aimlds.mymilo.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Phone-native text-to-speech — the device's own TTS engine reads
 * Milo's replies aloud. No AI-generated audio, per Nrupal's rule.
 */
class TtsPlayer(context: Context) {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        val clean = text
            .replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
            .replace(Regex("""[*#`_>]+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (clean.isEmpty()) return
        tts?.speak(clean.take(1500), TextToSpeech.QUEUE_FLUSH, null, "milo-reply")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // best-effort
        }
        tts = null
        ready = false
    }
}
