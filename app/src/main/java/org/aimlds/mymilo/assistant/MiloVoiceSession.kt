package org.aimlds.mymilo.assistant

import android.content.Context
import android.content.Intent
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.aimlds.mymilo.MainActivity
import org.aimlds.mymilo.MiloApp
import org.aimlds.mymilo.network.ChatMessageDto
import org.aimlds.mymilo.network.ChatRequest
import org.aimlds.mymilo.tools.LocalTools
import org.aimlds.mymilo.ui.MiloTypography
import org.aimlds.mymilo.ui.Skin
import org.aimlds.mymilo.voice.SpeechInput
import org.aimlds.mymilo.voice.TtsPlayer

/** Lifecycle plumbing so Compose can run inside a voice session window. */
private class SessionLifecycleOwner : LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry
    override val viewModelStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun event(e: Lifecycle.Event) = registry.handleLifecycleEvent(e)
}

private class SessionState {
    var status by mutableStateOf("Listening…")
    var heard by mutableStateOf("")
    var reply by mutableStateOf("")
    var busy by mutableStateOf(false)
}

/**
 * The assistant overlay: invoked by the system assistant gesture,
 * listens immediately, answers (local tools instantly; otherwise
 * the MyMilo server), speaks the reply, and offers Open app / Done.
 */
class MiloVoiceSession(context: Context) : VoiceInteractionSession(context) {

    private val owner = SessionLifecycleOwner()
    private val state = SessionState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speech: SpeechInput? = null
    private var tts: TtsPlayer? = null

    override fun onCreate() {
        super.onCreate()
        owner.onCreate()
        AssistantDiag.record(context, "session onCreate")
    }

    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        AssistantDiag.record(context, "assistant invoked (session shown)")
        try {
            setContentView(buildPanel())
        } catch (e: Exception) {
            AssistantDiag.record(
                context,
                "panel failed: ${e.javaClass.simpleName}: ${e.message}",
            )
            try {
                setContentView(fallbackView(e))
            } catch (ignored: Exception) {
                // Even the fallback failed; the diag line is the evidence.
            }
            return
        }
        owner.event(Lifecycle.Event.ON_START)
        owner.event(Lifecycle.Event.ON_RESUME)
        val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            state.status = "Microphone permission needed: open the app, " +
                "tap the mic in the chat box, and allow microphone access. " +
                "Then invoke me again."
            AssistantDiag.record(context, "mic permission NOT granted")
            return
        }
        try {
            startListening()
        } catch (e: Exception) {
            state.status = "Voice failed to start: ${e.message ?: "unknown error"}"
            AssistantDiag.record(
                context,
                "listen failed: ${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    /** The Compose panel, isolated so a failure here can fall back. */
    private fun buildPanel(): ComposeView = ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent {
            MaterialTheme(
                colorScheme = Skin.MIDNIGHT.scheme(),
                typography = MiloTypography,
            ) {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    SessionPanel(state,
                        onOpenApp = { openApp() },
                        onDone = { finish() })
                }
            }
        }
    }

    /** Plain-View emergency screen: a session must never show nothing. */
    private fun fallbackView(error: Exception): View {
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.rgb(18, 18, 24))
        }
        val title = android.widget.TextView(context).apply {
            text = "mymilo assistant"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
        }
        val detail = android.widget.TextView(context).apply {
            text = "The assistant screen hit a problem: " +
                "${error.javaClass.simpleName}: ${error.message ?: ""}\n\n" +
                "Open the app drawer — the “Last assistant activity” " +
                "line has the details."
            setTextColor(android.graphics.Color.rgb(220, 220, 230))
        }
        val open = android.widget.Button(context).apply {
            text = "Open app"
            setOnClickListener { openApp() }
        }
        layout.addView(title)
        layout.addView(detail)
        layout.addView(open)
        return layout
    }

    override fun onHide() {
        AssistantDiag.record(context, "session hidden")
        speech?.destroy()
        speech = null
        tts?.stop()
        owner.event(Lifecycle.Event.ON_PAUSE)
        owner.event(Lifecycle.Event.ON_STOP)
        super.onHide()
    }

    override fun onDestroy() {
        owner.event(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun startListening() {
        state.status = "Listening…"
        state.heard = ""
        state.reply = ""
        AssistantDiag.record(context, "listening started")
        speech = SpeechInput(
            context = context,
            onPartial = { state.heard = it },
            onFinal = { text ->
                state.heard = text
                AssistantDiag.record(context, "heard: ${text.take(60)}")
                answer(text)
            },
            onError = { msg ->
                state.status = msg
                AssistantDiag.record(context, "recognizer: $msg")
            },
            onEnd = {},
        )
        speech?.start()
    }

    private fun answer(text: String) {
        // Local tools answer instantly, offline.
        LocalTools.tryHandle(text)?.let { local ->
            deliver(local)
            return
        }
        val app = context.applicationContext as MiloApp
        state.busy = true
        state.status = "Asking MyMilo…"
        scope.launch {
            try {
                if (app.api.token().isBlank()) {
                    deliver("Open the MyMilo app and connect this device first.")
                    return@launch
                }
                val resp = app.api.service().chat(
                    ChatRequest(
                        messages = listOf(ChatMessageDto("user", text)),
                    )
                )
                val reply = resp.choices?.firstOrNull()?.message?.content
                    ?: "(no reply)"
                deliver(reply)
            } catch (e: Exception) {
                deliver("Couldn't reach MyMilo. ${e.message ?: ""}".trim())
            } finally {
                state.busy = false
            }
        }
    }

    private fun deliver(text: String) {
        state.reply = text
        state.status = ""
        AssistantDiag.record(context, "reply delivered: ${text.take(60)}")
        val app = context.applicationContext as MiloApp
        scope.launch {
            val enabled = app.db.settings().get("tts_enabled") != "false"
            if (enabled) {
                if (tts == null) tts = TtsPlayer(context)
                tts?.speak(text)
            }
        }
    }

    private fun openApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        finish()
    }
}

@Composable
private fun SessionPanel(
    state: SessionState,
    onOpenApp: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text("mymilo", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        if (state.status.isNotEmpty()) {
            Text(state.status, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.heard.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "You: ${state.heard}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (state.reply.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(state.reply, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Button(onClick = onOpenApp) { Text("Open app") }
            Spacer(Modifier.padding(6.dp))
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}
