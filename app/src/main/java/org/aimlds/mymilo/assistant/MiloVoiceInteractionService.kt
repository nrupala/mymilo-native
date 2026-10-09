package org.aimlds.mymilo.assistant

import android.service.voice.VoiceInteractionService

/**
 * Makes MyMilo eligible for Android's default-assistant role.
 * When Nrupal picks MyMilo in Settings → Default apps → Digital
 * assistant, the assistant gestures invoke MiloVoiceSession.
 */
class MiloVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        AssistantDiag.record(this, "assistant service ready")
    }
}
