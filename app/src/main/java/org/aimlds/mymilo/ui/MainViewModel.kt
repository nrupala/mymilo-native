package org.aimlds.mymilo.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.aimlds.mymilo.MiloApp
import org.aimlds.mymilo.data.MessageEntity
import org.aimlds.mymilo.data.SessionEntity
import org.aimlds.mymilo.network.ChatMessageDto
import org.aimlds.mymilo.network.ChatRequest
import org.aimlds.mymilo.skills.SkillRepository
import org.aimlds.mymilo.tools.LocalTools
import java.util.UUID

/**
 * The phone-side brain. Routing order for every message:
 *   1. Local tools (instant, offline, no model)
 *   2. Local skill match (decided on-device)
 *   3. Server escalation (MyMilo on Aetheris → its local/cloud
 *      models or search tools) — only when online
 *   4. Offline → queued locally, sent when connectivity returns
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val milo = app as MiloApp
    private val db = milo.db
    private val api = milo.api
    private val skills = SkillRepository(db.skills(), api)

    val sessions = db.sessions().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _skillCount = MutableStateFlow(0)
    val skillCount: StateFlow<Int> = _skillCount

    init {
        viewModelScope.launch {
            _connected.value = api.token().isNotBlank()
            skills.loadFromDb()
            _skillCount.value = skills.count()
            if (_connected.value) {
                refreshFromServer()
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Pull server state: skills bundle + session sync. Best-effort. */
    fun refreshFromServer() {
        viewModelScope.launch {
            try {
                val n = skills.syncFromServer()
                if (n > 0) _skillCount.value = n
            } catch (e: Exception) {
                // Offline or server down — local copies keep working.
            }
            try {
                val since = db.settings().get("last_sync")?.toDoubleOrNull() ?: 0.0
                val resp = api.service().syncSessions(since)
                resp.sessions.orEmpty().forEach { s ->
                    val existing = db.sessions().get(s.id)
                    if (existing == null) {
                        db.sessions().upsert(
                            SessionEntity(
                                id = s.id,
                                title = s.title ?: "Chat",
                                createdAt = ((s.updated_at ?: 0.0) * 1000).toLong(),
                                updatedAt = ((s.updated_at ?: 0.0) * 1000).toLong(),
                            )
                        )
                    }
                }
                resp.server_time?.let {
                    db.settings().put(
                        org.aimlds.mymilo.data.SettingEntity("last_sync", it.toString())
                    )
                }
            } catch (e: Exception) {
                // Best-effort sync.
            }
        }
    }

    fun connect(serverUrl: String, token: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                api.setServerAndToken(serverUrl.trim().trimEnd('/'), token.trim())
                api.service().clientConfig() // validates the token
                _connected.value = true
                onResult(true, "Connected")
                refreshFromServer()
            } catch (e: Exception) {
                onResult(false, "Could not connect: ${e.message}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            api.clearToken()
            _connected.value = false
        }
    }

    fun newSession() {
        _currentSessionId.value = null
        _messages.value = emptyList()
    }

    fun openSession(id: String) {
        _currentSessionId.value = id
        viewModelScope.launch {
            _messages.value = db.messages().forSession(id)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // Ensure a session exists (created locally, works offline)
            var sid = _currentSessionId.value
            if (sid == null) {
                sid = UUID.randomUUID().toString().replace("-", "").take(12)
                db.sessions().upsert(
                    SessionEntity(
                        id = sid,
                        title = trimmed.take(60),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        dirty = true,
                    )
                )
                _currentSessionId.value = sid
            }

            val userMsg = MessageEntity(
                sessionId = sid,
                role = "user",
                content = trimmed,
                createdAt = System.currentTimeMillis(),
                origin = "local",
                dirty = true,
            )
            db.messages().insert(userMsg)
            _messages.value = db.messages().forSession(sid)

            // ── Tier 1: local tools (instant, offline) ──
            val toolReply = LocalTools.tryHandle(trimmed)
            if (toolReply != null) {
                addAssistant(sid, toolReply, "local-tool")
                return@launch
            }

            // ── Tier 2: skill match happens on-device ──
            val skill = skills.match(trimmed)
            if (skill != null) _status.value = "Skill: ${skill.name}"

            // ── Tier 3: server escalation (needs network) ──
            if (!isOnline()) {
                addAssistant(
                    sid,
                    "You're offline. This is queued — I'll ask MyMilo " +
                        "(server models/search) when you're back online.",
                    "queued",
                )
                return@launch
            }

            _status.value = "Asking MyMilo…"
            try {
                val history = db.messages().forSession(sid)
                    .filter { it.role == "user" || it.role == "assistant" }
                    .takeLast(20)
                    .map { ChatMessageDto(it.role, it.content) }
                val resp = api.service().chat(
                    ChatRequest(messages = history, session_id = sid)
                )
                val reply = resp.choices?.firstOrNull()?.message?.content
                    ?: "(no reply)"
                addAssistant(sid, reply, "server")
                resp.active_skill?.let { _status.value = "Skill: $it" }
            } catch (e: Exception) {
                addAssistant(
                    sid,
                    "Couldn't reach MyMilo: ${e.message}. Message kept locally.",
                    "error",
                )
            }
        }
    }

    private suspend fun addAssistant(sid: String, content: String, origin: String) {
        db.messages().insert(
            MessageEntity(
                sessionId = sid,
                role = "assistant",
                content = content,
                createdAt = System.currentTimeMillis(),
                origin = origin,
            )
        )
        db.sessions().upsert(
            (db.sessions().get(sid) ?: return).copy(updatedAt = System.currentTimeMillis())
        )
        if (_currentSessionId.value == sid) {
            _messages.value = db.messages().forSession(sid)
        }
    }
}
