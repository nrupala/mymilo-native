package org.aimlds.mymilo.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Declared so the voice-interaction metadata has a recognition
 * service in-app (required for the assistant role). Actual
 * recognition uses the device's default service (Google/Samsung)
 * via SpeechRecognizer; this stub only satisfies the contract.
 */
class MiloRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
