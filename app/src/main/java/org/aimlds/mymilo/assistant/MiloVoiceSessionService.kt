package org.aimlds.mymilo.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class MiloVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        AssistantDiag.record(this, "session created")
        return MiloVoiceSession(this)
    }
}
