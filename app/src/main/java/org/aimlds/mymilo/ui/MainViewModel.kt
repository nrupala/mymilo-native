package org.aimlds.mymilo.ui

import android.Manifest
import android.app.Application
import android.content.Intent
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
import org.aimlds.mymilo.data.SkillEntity
import org.aimlds.mymilo.data.SourceEntity
import org.aimlds.mymilo.data.SourceTokenEntity
import org.aimlds.mymilo.network.ChatMessageDto
import org.aimlds.mymilo.network.ChatRequest
import org.aimlds.mymilo.skills.SkillRepository
import org.aimlds.mymilo.tools.LocalTools
import org.aimlds.mymilo.tools.PhoneActions
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
    private val vault = milo.vault
    private val skills = SkillRepository(db.skills(), api)

    val sessions = db.sessions().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Guide & About (v0.8.0) ───────────────────────────────────
    /** The full skills catalogue, live from the on-device cache. */
    val skillCatalog = db.skills().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Top-level screen: chat | skills | guide | about. */
    private val _screen = MutableStateFlow("chat")
    val screen: StateFlow<String> = _screen

    fun showScreen(name: String) {
        _screen.value = name
        if (name != "skills") _workspaceSkill.value = null
    }

    /** Skill Pair Program: the skill whose workspace is open
     *  (null = the catalogue). The workspace replaces the old
     *  tap-to-run dialog: a skill gets a room, not a pop-up. */
    private val _workspaceSkill = MutableStateFlow<SkillEntity?>(null)
    val workspaceSkill: StateFlow<SkillEntity?> = _workspaceSkill

    fun openWorkspace(skill: SkillEntity) {
        _workspaceSkill.value = skill
    }

    fun closeWorkspace() {
        _workspaceSkill.value = null
    }

    /** Favorite skills (starred in the catalogue), on-device only. */
    private val _pinnedSkills = MutableStateFlow<List<String>>(emptyList())
    val pinnedSkills: StateFlow<List<String>> = _pinnedSkills

    fun togglePin(skillName: String) {
        val now = _pinnedSkills.value.toMutableList()
        if (now.contains(skillName)) now.remove(skillName) else now.add(skillName)
        _pinnedSkills.value = now
        viewModelScope.launch {
            db.settings().put(
                org.aimlds.mymilo.data.SettingEntity(
                    "pinned_skills", now.joinToString(",")
                )
            )
        }
    }

    /** Support button destination, from the server's client
     *  config; blank hides the button (set server-side, no app
     *  release needed to change it). */
    private val _supportUrl = MutableStateFlow("")
    val supportUrl: StateFlow<String> = _supportUrl

    // ── Sources & Vault (v0.10.0) ─────────────────────────────
    /** Every source (Aetheris first, then the user's own). */
    val sourcesList = db.sources().observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Token rows for all sources (labels + active flags only —
     *  the secrets themselves never leave the vault). */
    val tokenRows = db.sources().observeTokens()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Whether Vault entry #1 (the Aetheris device token) exists. */
    private val _aetherisKeySaved = MutableStateFlow(false)
    val aetherisKeySaved: StateFlow<Boolean> = _aetherisKeySaved

    /** The brain answering the open chat: phone | aetheris |
     *  src:<sourceId>. Synced from the session row on open, and
     *  the starting choice for a new chat. */
    private val _currentBrain = MutableStateFlow("aetheris")
    val currentBrain: StateFlow<String> = _currentBrain

    fun brainName(brain: String): String = when {
        brain == "phone" -> "This phone"
        brain == "aetheris" -> "Aetheris"
        brain.startsWith("src:") ->
            sourcesList.value.firstOrNull {
                it.id == brain.removePrefix("src:")
            }?.name ?: "A source"
        else -> "Aetheris"
    }

    fun setBrain(brain: String, alsoDefault: Boolean = false) {
        _currentBrain.value = brain
        viewModelScope.launch {
            if (alsoDefault) {
                db.settings().put(
                    org.aimlds.mymilo.data.SettingEntity("default_brain", brain)
                )
            }
            val sid = _currentSessionId.value ?: return@launch
            val s = db.sessions().get(sid) ?: return@launch
            if (s.brain != brain) db.sessions().upsert(s.copy(brain = brain))
        }
    }

    fun addSource(name: String, kind: String, baseUrl: String, model: String) {
        viewModelScope.launch {
            db.sources().upsertSource(
                SourceEntity(
                    id = UUID.randomUUID().toString().replace("-", "").take(12),
                    name = name.trim().ifBlank { "Source" },
                    kind = kind,
                    baseUrl = baseUrl.trim().trimEnd('/'),
                    model = model.trim(),
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun saveSource(source: SourceEntity) {
        viewModelScope.launch { db.sources().upsertSource(source) }
    }

    fun deleteSource(source: SourceEntity) {
        viewModelScope.launch {
            db.sources().tokensFor(source.id).forEach {
                vault.remove(
                    org.aimlds.mymilo.vault.Vault.tokenSlot(it.id)
                )
            }
            db.sources().deleteTokensFor(source.id)
            db.sources().deleteSource(source.id)
            if (_currentBrain.value == "src:${source.id}") {
                setBrain("aetheris")
            }
        }
    }

    fun addToken(sourceId: String, label: String, secret: String) {
        viewModelScope.launch {
            val existing = db.sources().tokensFor(sourceId)
            val entry = SourceTokenEntity(
                id = UUID.randomUUID().toString().replace("-", "").take(12),
                sourceId = sourceId,
                label = label.trim().ifBlank { "Key" },
                active = existing.isEmpty(),
                createdAt = System.currentTimeMillis(),
            )
            if (vault.put(
                    org.aimlds.mymilo.vault.Vault.tokenSlot(entry.id), secret.trim()
                )
            ) {
                db.sources().upsertToken(entry)
            }
        }
    }

    fun replaceToken(entry: SourceTokenEntity, secret: String) {
        vault.put(org.aimlds.mymilo.vault.Vault.tokenSlot(entry.id), secret.trim())
    }

    fun setActiveToken(entry: SourceTokenEntity) {
        viewModelScope.launch {
            db.sources().tokensFor(entry.sourceId).forEach {
                if (it.active != (it.id == entry.id)) {
                    db.sources().upsertToken(it.copy(active = it.id == entry.id))
                }
            }
        }
    }

    fun deleteToken(entry: SourceTokenEntity) {
        viewModelScope.launch {
            vault.remove(org.aimlds.mymilo.vault.Vault.tokenSlot(entry.id))
            db.sources().deleteToken(entry.id)
            if (entry.active) {
                db.sources().tokensFor(entry.sourceId).firstOrNull()
                    ?.let { db.sources().upsertToken(it.copy(active = true)) }
            }
        }
    }

    /** The active key for a source, read from the vault at call
     *  time only. Null when the source has no usable key. */
    private suspend fun activeSecret(sourceId: String): String? {
        val tokens = db.sources().tokensFor(sourceId)
        val entry = tokens.firstOrNull { it.active } ?: tokens.firstOrNull()
            ?: return null
        return vault.get(org.aimlds.mymilo.vault.Vault.tokenSlot(entry.id))
    }

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
        // A recognizer that fails to start must land as a status
        // line, never a crash (build-15 hardening).
        try {
            speech?.start()
        } catch (e: Exception) {
            _listening.value = false
            _status.value = "Voice couldn't start on this phone."
        }
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

    // True while the server is working on a reply (build 12: the
    // in-conversation "Milo is thinking…" presence).
    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking

    /** Parse a message's stored sources JSON ("" when none). */
    fun parseSources(json: String): List<org.aimlds.mymilo.network.SourceDto> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object :
                com.google.gson.reflect.TypeToken<
                    List<org.aimlds.mymilo.network.SourceDto>
                    >() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun requestVoiceAutoStart() {
        _voiceAutoStart.value = true
    }

    // Text handed to the app by another app's Share sheet
    // (build 12: the manifest's ACTION_SEND, finally wired).
    private val _stagedText = MutableStateFlow("")
    val stagedText: StateFlow<String> = _stagedText

    fun stageSharedText(text: String) {
        _stagedText.value = text
    }

    fun consumeStagedText() {
        _stagedText.value = ""
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
                if (!org.aimlds.mymilo.update.UpdateChecker
                        .install(context, file)
                ) {
                    _updateStatus.value =
                        "Downloaded, but the installer couldn't open. " +
                            "Tap Install build ${info.releaseNumber} " +
                            "in the drawer to try again."
                }
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
            if (!org.aimlds.mymilo.update.UpdateChecker
                    .install(context, file)
            ) {
                _updateStatus.value =
                    "Couldn't open the installer — tap Install " +
                        "again to retry."
            }
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
            // Sources & Vault: the device token moves into the
            // Keystore vault (entry #1), and Aetheris takes its
            // place in the sources list like every other source.
            api.migrateTokenToVault()
            _aetherisKeySaved.value =
                vault.contains(org.aimlds.mymilo.vault.Vault.AETHERIS_SLOT)
            if (db.sources().source("aetheris") == null) {
                db.sources().upsertSource(
                    SourceEntity(
                        id = "aetheris",
                        name = "Aetheris",
                        kind = "aetheris",
                        baseUrl = api.serverUrl(),
                        model = "auto",
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            _currentBrain.value =
                db.settings().get("default_brain") ?: "aetheris"
            val hasToken = api.token().isNotBlank()
            _connected.value = hasToken
            skills.loadFromDb()
            _skillCount.value = skills.count()
            _pinnedSkills.value = db.settings().get("pinned_skills")
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
            if (hasToken) {
                // v0.3.0: re-validate the saved token instead of
                // pretending to be connected (build-1 trap). A 401
                // means the token is dead -> back to setup. A network
                // error keeps offline mode available.
                try {
                    val cfg = api.service().clientConfig()
                    _supportUrl.value = cfg.support_url ?: ""
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
                val cfg = api.service().clientConfig() // validates the token
                _supportUrl.value = cfg.support_url ?: ""
                _connected.value = true
                _aetherisKeySaved.value = true
                onResult(true, "Connected")
                refreshFromServer()
            } catch (e: Exception) {
                onResult(false, "Could not connect: " + friendlyError(e))
            }
        }
    }

    /** Sources screen: replace the Aetheris device key in the
     *  vault. Validated against the server before it's kept
     *  (connect() only marks success after the server answers). */
    fun replaceAetherisKey(token: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val url = try { api.serverUrl() } catch (e: Exception) { "" }
            connect(url, token, onResult)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            api.clearToken()
            _connected.value = false
            _aetherisKeySaved.value = false
        }
    }

    fun newSession() {
        _currentSessionId.value = null
        _messages.value = emptyList()
        viewModelScope.launch {
            _currentBrain.value =
                db.settings().get("default_brain") ?: "aetheris"
        }
    }

    fun openSession(id: String) {
        _currentSessionId.value = id
        viewModelScope.launch {
            _messages.value = db.messages().forSession(id)
            db.sessions().get(id)?.let { _currentBrain.value = it.brain }
        }
    }

    /** Skill Pair Program: a skill attached to the next message
     *  via the composer's + button. The next send runs with that
     *  skill forced, in the current chat; then it detaches. */
    private val _attachedSkill = MutableStateFlow<SkillEntity?>(null)
    val attachedSkill: StateFlow<SkillEntity?> = _attachedSkill

    fun attachSkill(skill: SkillEntity) {
        _attachedSkill.value = skill
    }

    fun clearAttachedSkill() {
        _attachedSkill.value = null
    }

    fun send(text: String) {
        if (text.isBlank()) return
        val attached = _attachedSkill.value
        _attachedSkill.value = null
        sendInternal(text, attached?.name, null)
    }

    /** v0.8.0: run a skill on purpose (catalogue tap-to-run). The
     *  turn starts a fresh chat titled with the skill's plain name
     *  and the server runs the named skill (v0.41.0). */
    fun runSkill(skillName: String, humanName: String, prompt: String) {
        _currentSessionId.value = null
        _messages.value = emptyList()
        _screen.value = "chat"
        sendInternal(prompt, skillName, humanName)
    }

    /** Sources & Vault: a chat whose brain is one of the user's
     *  own sources is answered by that provider directly, with
     *  the key read from the vault at call time. A chosen skill
     *  still leads — its instructions ride as the system prompt,
     *  since the skill text lives on this phone. */
    private suspend fun sendViaSource(
        sid: String,
        sourceId: String,
        forcedSkill: String?,
    ) {
        val source = db.sources().source(sourceId)
        val secret = if (source == null) null else activeSecret(sourceId)
        if (source == null || secret == null) {
            addAssistant(
                sid,
                "That source has no working key saved. Open " +
                    "Sources & keys from the menu and add one — " +
                    "your message is kept here.",
                "error",
            )
            return
        }
        if (!isOnline()) {
            addAssistant(
                sid,
                "You're offline, so ${source.name} can't be " +
                    "reached. Your message is kept here.",
                "queued",
            )
            return
        }
        _status.value = "Asking ${source.name}…"
        _thinking.value = true
        try {
            val msgs = mutableListOf<ChatMessageDto>()
            if (forcedSkill != null) {
                val skill = db.skills().all()
                    .firstOrNull { it.name == forcedSkill }
                if (skill != null && skill.content.isNotBlank()) {
                    msgs.add(ChatMessageDto("system", skill.content))
                    _status.value = "Skill: $forcedSkill"
                }
            }
            db.messages().forSession(sid)
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(20)
                .forEach { msgs.add(ChatMessageDto(it.role, it.content)) }
            val reply = org.aimlds.mymilo.network.DirectChat.chat(
                source.baseUrl, secret, source.model, msgs
            )
            val sourcesJson = com.google.gson.Gson().toJson(
                listOf(
                    org.aimlds.mymilo.network.SourceDto(
                        "model", "Your key · ${source.model}",
                        null, source.name,
                    )
                )
            )
            addAssistant(sid, reply, "source", sourcesJson)
            _status.value = ""
        } catch (e: Exception) {
            addAssistant(
                sid,
                "Couldn't get an answer from ${source.name}: " +
                    (e.message ?: "unknown error") +
                    " Your message is kept here.",
                "error",
            )
            _status.value = ""
        } finally {
            _thinking.value = false
        }
    }

    private fun sendInternal(
        text: String,
        forcedSkill: String?,
        sessionTitle: String?,
    ) {
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
                        title = sessionTitle ?: trimmed.take(60),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        dirty = true,
                        brain = _currentBrain.value,
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

            // ── Phone actions (v0.12.0): verbs for the phone
            // itself — call / text / open an app. They run on
            // every brain: "call Natasha" is a phone task, not a
            // model question. The confirmation card appears and
            // this message goes no further until the user
            // decides. A forced skill skips them: the user chose
            // the skill on purpose.
            if (forcedSkill == null) {
                val parsedAction = PhoneActions.parse(trimmed)
                if (parsedAction != null &&
                    beginAction(sid, parsedAction)
                ) {
                    return@launch
                }
            }

            // ── Sources & Vault: a non-Aetheris brain answers
            // on its own path. The choice is the user's, made in
            // the open — never a silent reroute. ──
            val brain = db.sessions().get(sid)?.brain ?: _currentBrain.value
            if (brain == "phone") {
                if (forcedSkill == null) {
                    val toolReply = LocalTools.tryHandle(trimmed)
                    if (toolReply != null) {
                        addAssistant(sid, toolReply, "local-tool")
                        return@launch
                    }
                }
                addAssistant(
                    sid,
                    "This chat is set to This phone only, so " +
                        "nothing was sent anywhere — and the " +
                        "on-device tools have no answer for that. " +
                        "Pick Aetheris or one of your sources " +
                        "above the message box to have a model " +
                        "answer.",
                    "phone",
                )
                return@launch
            }
            if (brain.startsWith("src:")) {
                sendViaSource(sid, brain.removePrefix("src:"), forcedSkill)
                return@launch
            }

            // ── Tier 1: local tools (instant, offline) ──
            // Skipped when a skill was chosen on purpose: the user
            // asked for the skill, not the calculator.
            if (forcedSkill == null) {
                val toolReply = LocalTools.tryHandle(trimmed)
                if (toolReply != null) {
                    addAssistant(sid, toolReply, "local-tool")
                    return@launch
                }
            }

            // ── Tier 2: skill match happens on-device ──
            if (forcedSkill != null) {
                _status.value = "Skill: $forcedSkill"
            }
            val skill = if (forcedSkill == null) skills.match(trimmed) else null
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
            _thinking.value = true
            try {
                val history = db.messages().forSession(sid)
                    .filter { it.role == "user" || it.role == "assistant" }
                    .takeLast(20)
                    .map { ChatMessageDto(it.role, it.content) }
                val resp = api.service().chat(
                    ChatRequest(
                        messages = history,
                        session_id = sid,
                        skill = forcedSkill,
                    )
                )
                val reply = resp.choices?.firstOrNull()?.message?.content
                    ?: "(no reply)"
                val sourcesJson = resp.sources
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { com.google.gson.Gson().toJson(it) }
                    ?: ""
                addAssistant(sid, reply, "server", sourcesJson)
                _status.value = resp.active_skill?.let { "Skill: $it" } ?: ""
            } catch (e: Exception) {
                addAssistant(
                    sid,
                    "Couldn't reach MyMilo: ${friendlyError(e)} " +
                        "Message kept locally.",
                    "error",
                )
                _status.value = ""
            } finally {
                _thinking.value = false
            }
        }
    }

    /** Build 12: delete a thread and all of its messages, for good. */
    fun deleteSession(id: String) {
        viewModelScope.launch {
            db.messages().deleteForSession(id)
            db.sessions().delete(id)
            if (_currentSessionId.value == id) {
                _currentSessionId.value = null
                _messages.value = emptyList()
            }
        }
    }

    /** Build 12: rename a thread. */
    fun renameSession(id: String, title: String) {
        val clean = title.trim().take(80)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val s = db.sessions().get(id) ?: return@launch
            db.sessions().upsert(s.copy(title = clean))
        }
    }

    /** Build 12: read any message aloud on demand (phone TTS). */
    fun readAloud(text: String) {
        tts.speak(text)
    }

    /** Build 12: a thread as shareable/saveable markdown. */
    suspend fun exportThreadMarkdown(id: String): String {
        val s = db.sessions().get(id)
        val msgs = db.messages().forSession(id)
        val sb = StringBuilder()
        sb.append("# ").append(s?.title ?: "MyMilo chat").append("\n\n")
        val fmt = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm", java.util.Locale.US
        )
        for (m in msgs) {
            val when_ = fmt.format(java.util.Date(m.createdAt))
            if (m.role == "user") {
                sb.append("**You** · ").append(when_).append("\n\n")
                sb.append(m.content).append("\n\n")
            } else if (m.role == "assistant") {
                sb.append("**Milo** · ").append(when_).append("\n\n")
                sb.append(m.content).append("\n\n")
                val srcs = parseSources(m.sourcesJson)
                if (srcs.isNotEmpty()) {
                    sb.append("Sources: ")
                    sb.append(srcs.joinToString(" · ") { it.title })
                    sb.append("\n\n")
                }
            }
        }
        return sb.toString()
    }

    // ── Phone actions (v0.12.0) ─────────────────────────────
    // "call Natasha", "text Sam saying …", "open WhatsApp":
    // Milo prepares, the user confirms on the card, the phone
    // acts. Resolution is on-device; nothing is uploaded.

    data class PendingAction(
        val kind: String, // "call" | "text" | "open"
        val target: String,
        val body: String = "",
        // contacts-permission | pick | confirm | notfound |
        // call-permission | sms-permission
        val stage: String,
        val needsPermission: String? = null,
        val resolvedName: String? = null,
        val resolvedNumber: String? = null,
        val appLabel: String? = null,
        val appPackage: String? = null,
        val candidates: List<PhoneActions.Candidate> = emptyList(),
        val sessionId: String = "",
    )

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction

    private fun beginAction(
        sid: String,
        parsed: PhoneActions.Parsed,
    ): Boolean {
        val ctx = getApplication<Application>()
        return when (parsed) {
            is PhoneActions.Parsed.Open -> {
                val found = PhoneActions.findApps(ctx, parsed.app)
                if (found.isEmpty()) {
                    false // not an app command after all — chat on
                } else {
                    _pendingAction.value = if (found.size == 1) {
                        PendingAction(
                            "open", parsed.app, stage = "confirm",
                            appLabel = found[0].label,
                            appPackage = found[0].pkg,
                            sessionId = sid,
                        )
                    } else {
                        PendingAction(
                            "open", parsed.app, stage = "pick",
                            candidates = found, sessionId = sid,
                        )
                    }
                    true
                }
            }
            is PhoneActions.Parsed.Call ->
                beginPersonAction(sid, "call", parsed.target, "")
            is PhoneActions.Parsed.Text ->
                beginPersonAction(sid, "text", parsed.target, parsed.body)
        }
    }

    private fun beginPersonAction(
        sid: String,
        kind: String,
        target: String,
        body: String,
    ): Boolean {
        val ctx = getApplication<Application>()
        if (PhoneActions.looksLikeNumber(target)) {
            _pendingAction.value = PendingAction(
                kind, target, body, stage = "confirm",
                resolvedNumber = PhoneActions.normalizeNumber(target),
                sessionId = sid,
            )
            return true
        }
        if (!PhoneActions.hasPermission(
                ctx, Manifest.permission.READ_CONTACTS,
            )
        ) {
            _pendingAction.value = PendingAction(
                kind, target, body, stage = "contacts-permission",
                needsPermission = Manifest.permission.READ_CONTACTS,
                sessionId = sid,
            )
            return true
        }
        resolvePersonTarget(sid, kind, target, body)
        return true
    }

    private fun resolvePersonTarget(
        sid: String,
        kind: String,
        target: String,
        body: String,
    ) {
        val ctx = getApplication<Application>()
        val found = PhoneActions.findContacts(ctx, target)
        _pendingAction.value = when {
            found.isEmpty() -> PendingAction(
                kind, target, body, stage = "notfound", sessionId = sid,
            )
            found.size == 1 -> PendingAction(
                kind, target, body, stage = "confirm",
                resolvedName = found[0].label,
                resolvedNumber = found[0].number,
                sessionId = sid,
            )
            else -> PendingAction(
                kind, target, body, stage = "pick",
                candidates = found, sessionId = sid,
            )
        }
    }

    fun actionPick(candidate: PhoneActions.Candidate) {
        val pa = _pendingAction.value ?: return
        _pendingAction.value = if (pa.kind == "open") {
            pa.copy(
                stage = "confirm",
                appLabel = candidate.label,
                appPackage = candidate.pkg,
                candidates = emptyList(),
            )
        } else {
            pa.copy(
                stage = "confirm",
                resolvedName = candidate.label,
                resolvedNumber = candidate.number,
                candidates = emptyList(),
            )
        }
    }

    fun actionDismiss() {
        _pendingAction.value = null
    }

    /** The confirm button: if the doing-permission is missing,
     *  move to its permission card (which offers a fallback);
     *  otherwise act now. */
    fun actionConfirm(bodyOverride: String?) {
        val pa = _pendingAction.value ?: return
        val ctx = getApplication<Application>()
        val action = if (bodyOverride != null) {
            pa.copy(body = bodyOverride)
        } else {
            pa
        }
        when (action.kind) {
            "call" -> {
                if (PhoneActions.hasPermission(
                        ctx, Manifest.permission.CALL_PHONE,
                    )
                ) {
                    executeCall(action, direct = true)
                } else {
                    _pendingAction.value = action.copy(
                        stage = "call-permission",
                        needsPermission = Manifest.permission.CALL_PHONE,
                    )
                }
            }
            "text" -> {
                if (PhoneActions.hasPermission(
                        ctx, Manifest.permission.SEND_SMS,
                    )
                ) {
                    executeText(action, direct = true)
                } else {
                    _pendingAction.value = action.copy(
                        stage = "sms-permission",
                        needsPermission = Manifest.permission.SEND_SMS,
                    )
                }
            }
            "open" -> executeOpen(action)
        }
    }

    fun actionPermissionResult(granted: Boolean) {
        val pa = _pendingAction.value ?: return
        when (pa.stage) {
            "contacts-permission" -> {
                if (granted) {
                    resolvePersonTarget(
                        pa.sessionId, pa.kind, pa.target, pa.body,
                    )
                } else {
                    _pendingAction.value = null
                    viewModelScope.launch {
                        addAssistant(
                            pa.sessionId,
                            "No problem. To call or text someone " +
                                "by name I need your contacts " +
                                "permission — names and numbers " +
                                "stay on this phone and are never " +
                                "uploaded. You can still say " +
                                "“call” followed by a number.",
                            "phone",
                        )
                    }
                }
            }
            "call-permission" -> executeCall(pa, direct = granted)
            "sms-permission" -> executeText(pa, direct = granted)
        }
    }

    /** The fallback button on a permission card (dialer /
     *  messaging app instead of direct action). */
    fun actionFallback() {
        val pa = _pendingAction.value ?: return
        when (pa.stage) {
            "call-permission" -> executeCall(pa, direct = false)
            "sms-permission" -> executeText(pa, direct = false)
            else -> _pendingAction.value = null
        }
    }

    private fun startPhoneIntent(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun finishAction(pa: PendingAction, note: String) {
        _pendingAction.value = null
        viewModelScope.launch { addAssistant(pa.sessionId, note, "phone") }
    }

    private fun executeCall(pa: PendingAction, direct: Boolean) {
        val number = pa.resolvedNumber ?: return
        val who = pa.resolvedName ?: number
        if (direct) {
            val ok = startPhoneIntent(PhoneActions.callIntent(number))
            finishAction(
                pa,
                if (ok) {
                    "Calling $who now."
                } else {
                    "Couldn't place that call — the phone refused it."
                },
            )
        } else {
            val ok = startPhoneIntent(PhoneActions.dialIntent(number))
            finishAction(
                pa,
                if (ok) {
                    "Your dialer is open with $who's number " +
                        "filled in — tap call there."
                } else {
                    "Couldn't open your dialer."
                },
            )
        }
    }

    private fun executeText(pa: PendingAction, direct: Boolean) {
        val number = pa.resolvedNumber ?: return
        val who = pa.resolvedName ?: number
        if (direct) {
            val ok = PhoneActions.sendSms(
                getApplication(), number, pa.body,
            )
            finishAction(
                pa,
                if (ok) {
                    "Text sent to $who."
                } else {
                    "Couldn't send that text — the number or " +
                        "your carrier refused it. Your message " +
                        "was: “${pa.body}”"
                },
            )
        } else {
            val ok = startPhoneIntent(
                PhoneActions.smsComposerIntent(number, pa.body),
            )
            finishAction(
                pa,
                if (ok) {
                    "Your messaging app is open with the text " +
                        "ready for $who — tap send there."
                } else {
                    "Couldn't open your messaging app."
                },
            )
        }
    }

    private fun executeOpen(pa: PendingAction) {
        val pkg = pa.appPackage ?: return
        val intent = PhoneActions.launchIntent(getApplication(), pkg)
        val ok = intent != null && startPhoneIntent(intent)
        finishAction(
            pa,
            if (ok) {
                "Opening ${pa.appLabel ?: pa.target}."
            } else {
                "Couldn't open ${pa.appLabel ?: pa.target}."
            },
        )
    }

    private suspend fun addAssistant(
        sid: String,
        content: String,
        origin: String,
        sourcesJson: String = "",
    ) {
        db.messages().insert(
            MessageEntity(
                sessionId = sid,
                role = "assistant",
                content = content,
                createdAt = System.currentTimeMillis(),
                origin = origin,
                sourcesJson = sourcesJson,
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
