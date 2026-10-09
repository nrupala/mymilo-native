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

    private val _skin = MutableStateFlow(Skin.MIDNIGHT)
    val skin: StateFlow<Skin> = _skin

    fun setSkin(s: Skin) {
        _skin.value = s
        viewModelScope.launch {
            db.settings().put(org.aimlds.mymilo.data.SettingEntity("skin", s.name))
        }
    }

    // ── Voice (v0.4.0): phone-native TTS out + speech in ──────
    private val tts = org.aimlds.mymilo.voice.TtsPlayer(app)

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText

    private var speech: org.aimlds.mymilo.voice.SpeechInput? = null

    fun setTtsEnabled(on: Boolean) {
        _ttsEnabled.value = on
        if (!on) tts.stop()
        viewModelScope.launch {
            db.settings().put(
                org.aimlds.mymilo.data.SettingEntity(
                    "tts_enabled", if (on) "true" else "false"
                )
            )
        }
    }

    fun startDictation() {
        if (_listening.value) return
        tts.stop()
        _listening.value = true
        _partialText.value = ""
        speech = org.aimlds.mymilo.voice.SpeechInput(
            context = getApplication(),
            onPartial = { _partialText.value = it },
            onFinal = { text -> send(text) },
            onError = { msg -> _status.value = msg },
            onEnd = {
                _listening.value = false
                _partialText.value = ""
            },
        )
        speech?.start()
    }

    fun stopDictation() {
        speech?.destroy()
        speech = null
        _listening.value = false
        _partialText.value = ""
    }

    // ── Auto-update (v0.5.0) ──────────────────────────────────
    private val _updateInfo =
        MutableStateFlow<org.aimlds.mymilo.update.UpdateInfo?>(null)
    val updateInfo: StateFlow<org.aimlds.mymilo.update.UpdateInfo?> = _updateInfo

    private val _updateStatus = MutableStateFlow("")
    val updateStatus: StateFlow<String> = _updateStatus

    // A downloaded-but-not-yet-installed update (build 10): persists
    // across restarts until installed or superseded, so the Install
    // action is always reachable from the drawer's Updates section.
    private val _downloadedBuild = MutableStateFlow<Int?>(null)
    val downloadedBuild: StateFlow<Int?> = _downloadedBuild

    private val _voiceAutoStart = MutableStateFlow(false)
    val voiceAutoStart: StateFlow<Boolean> = _voiceAutoStart

    fun requestVoiceAutoStart() {
        _voiceAutoStart.value = true
    }

    fun consumeVoiceAutoStart() {
        _voiceAutoStart.value = false
    }

    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) _updateStatus.value = "Checking for updates…"
            val info = try {
                org.aimlds.mymilo.update.UpdateChecker.check()
            } catch (e: Exception) {
                null
            }
            _updateInfo.value = info
            _updateStatus.value = when {
                info != null -> "Build ${info.releaseNumber} is available"
                manual -> "You're on the latest build"
                else -> _updateStatus.value
            }
            db.settings().put(
                org.aimlds.mymilo.data.SettingEntity(
                    "last_update_check",
                    System.currentTimeMillis().toString(),
                )
            )
        }
    }

    fun applyUpdate(context: android.content.Context) {
        val info = _updateInfo.value ?: return
        // Already downloaded? Install it directly.
        if (_downloadedBuild.value == info.releaseNumber) {
            installDownloaded(context)
            return
        }
        viewModelScope.launch {
            _updateStatus.value = "Downloading build ${info.releaseNumber}…"
            val file = org.aimlds.mymilo.update.UpdateChecker.download(context, info)
            if (file != null) {
                markDownloaded(info.releaseNumber, file)
                org.aimlds.mymilo.update.UpdateChecker.install(context, file)
            } else {
                _updateStatus.value = "Download failed — try again."
            }
        }
    }

    fun downloadUpdate(context: android.content.Context) {
        val info = _updateInfo.value ?: return
        viewModelScope.launch {
            _updateStatus.value = "Downloading build ${info.releaseNumber}…"
            val file = org.aimlds.mymilo.update.UpdateChecker.download(context, info)
            if (file != null) {
                markDownloaded(info.releaseNumber, file)
                _updateStatus.value =
                    "Build ${info.releaseNumber} downloaded — " +
                        "tap Install whenever you're ready."
            } else {
                _updateStatus.value = "Download failed — try again."
            }
        }
    }

    fun installDownloaded(context: android.content.Context) {
        val n = _downloadedBuild.value ?: return
        val file = org.aimlds.mymilo.update.UpdateChecker
            .downloadedFile(context, n)
        if (file != null) {
            _updateStatus.value = ""
            org.aimlds.mymilo.update.UpdateChecker.install(context, file)
        } else {
            clearDownloaded()
            _updateStatus.value =
                "The downloaded file is gone — download it again."
        }
    }

    private fun markDownloaded(n: Int, file: java.io.File) {
        _downloadedBuild.value = n
        viewModelScope.launch {
            db.settings().put(
                org.aimlds.mymilo.data.SettingEntity(
                    "update_downloaded_build", n.toString()
                )
            )
            db.settings().put(
                org.aimlds.mymilo.data.SettingEntity(
                    "update_apk_path", file.absolutePath
                )
            )
        }
    }

    private fun clearDownloaded() {
        _downloadedBuild.value = null
        viewModelScope.launch {
            db.settings().put(
                org.aimlds.mymilo.data.SettingEntity(
                    "update_downloaded_build", ""
                )
            )
            db.settings().put(
                org.aimlds.mymilo.data.SettingEntity("update_apk_path", "")
            )
        }
    }

    override fun onCleared() {
        speech?.destroy()
        tts.shutdown()
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            _skin.value = Skin.fromName(db.settings().get("skin"))
            _ttsEnabled.value = db.settings().get("tts_enabled") != "false"
            // v0.5.0: check for a newer build at most once a day.
            val lastCheck = db.settings().get("last_update_check")
                ?.toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - lastCheck > 24 * 3600 * 1000L) {
                checkForUpdate()
            }
            // Reconcile a downloaded update from a previous run.
            val dlBuild = db.settings().get("update_downloaded_build")
                ?.toIntOrNull()
            val dlPath = db.settings().get("update_apk_path")
            when {
                dlBuild == null -> {}
                dlBuild <= org.aimlds.mymilo.update.UpdateChecker
                    .currentReleaseNumber() -> clearDownloaded()

                dlPath != null && java.io.File(dlPath).exists() -> {
                    _downloadedBuild.value = dlBuild
                    _updateStatus.value =
                        "Build $dlBuild downloaded — ready to install."
                }

                else -> clearDownloaded()
            }
            val hasToken = api.token().isNotBlank()
            _connected.value = hasToken
            skills.loadFromDb()
            _skillCount.value = skills.count()
            if (hasToken) {
                // v0.3.0: re-validate the saved token instead of
                // pretending to be connected (build-1 trap). A 401
                // means the token is dead -> back to setup. A network
                // error keeps offline mode available.
                try {
                    api.service().clientConfig()
                    refreshFromServer()
                } catch (e: Exception) {
                    if ((e as? retrofit2.HttpException)?.code() == 401) {
                        api.clearToken()
                        _connected.value = false
                    }
                }
            }
        }
    }

    /** Plain-English version of a network failure. */
    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("malformed JSON", ignoreCase = true) ||
                msg.contains("JsonReader", ignoreCase = true) ->
                "That address returned a web page instead of API data. " +
                    "Use the API address: https://mymilo-api.aimlds.org"
            (e as? retrofit2.HttpException)?.code() == 401 ||
                msg.contains("401") ->
                "Token not accepted. Register this device again on " +
                    "the server's Devices page and paste the new token."
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Can't reach that address — check the URL and your connection."
            msg.contains("timeout", ignoreCase = true) ->
                "The server took too long to answer. Try again in a moment."
            else -> msg.ifBlank { "Unknown error" }
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
                onResult(false, "Could not connect: " + friendlyError(e))
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
        tts.stop() // a new message interrupts any read-aloud
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
                _status.value = resp.active_skill?.let { "Skill: $it" } ?: ""
            } catch (e: Exception) {
                addAssistant(
                    sid,
                    "Couldn't reach MyMilo: ${friendlyError(e)} " +
                        "Message kept locally.",
                    "error",
                )
                _status.value = ""
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
        // v0.4.0: read real replies aloud with the phone's own TTS.
        if (_ttsEnabled.value &&
            (origin == "server" || origin == "local-tool" || origin == "local-model")
        ) {
            tts.speak(content)
        }
        db.sessions().upsert(
            (db.sessions().get(sid) ?: return).copy(updatedAt = System.currentTimeMillis())
        )
        if (_currentSessionId.value == sid) {
            _messages.value = db.messages().forSession(sid)
        }
    }
}
