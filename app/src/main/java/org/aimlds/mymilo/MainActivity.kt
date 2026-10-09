package org.aimlds.mymilo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.aimlds.mymilo.data.MessageEntity
import org.aimlds.mymilo.data.SessionEntity
import org.aimlds.mymilo.network.SourceDto
import org.aimlds.mymilo.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        handleIntent(intent)
        setContent {
            val skin by vm.skin.collectAsState()
            MaterialTheme(
                colorScheme = skin.scheme(),
                typography = org.aimlds.mymilo.ui.MiloTypography,
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MiloRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action == "org.aimlds.mymilo.action.VOICE_ASK") {
            vm.requestVoiceAutoStart()
        }
        // Build 12: text shared from other apps finally lands in the
        // composer instead of being swallowed (audit #20).
        if (intent?.action == android.content.Intent.ACTION_SEND &&
            intent.type == "text/plain"
        ) {
            intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                ?.takeIf { it.isNotBlank() }
                ?.let { vm.stageSharedText(it) }
        }
    }
}

@Composable
fun MiloRoot(vm: MainViewModel = viewModel()) {
    val connected by vm.connected.collectAsState()
    if (!connected) {
        SetupScreen(vm)
    } else {
        ChatScreen(vm)
    }
}

@Composable
fun SetupScreen(vm: MainViewModel) {
    var server by remember { mutableStateOf("https://mymilo-api.aimlds.org") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("mymilo", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your personal AI assistant. Works offline for local tasks; " +
                "goes to your MyMilo server for heavy models and search.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = server, onValueChange = { server = it },
            label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("Device token") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Get a token: open the server URL in your browser, sign in, " +
                "and register this device on the Devices page.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color(0xFFFF5D5D))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                vm.connect(server, token) { ok, msg ->
                    busy = false
                    if (!ok) error = msg
                }
            },
            enabled = !busy && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Connecting…" else "Connect") }
    }
}

private fun dayLabel(ts: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(java.util.Calendar.YEAR) ==
        then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) ==
        then.get(java.util.Calendar.DAY_OF_YEAR)
    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(java.util.Calendar.YEAR) ==
        then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) ==
        then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> "Today"
        yesterday -> "Yesterday"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
            .format(java.util.Date(ts))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionEntity,
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(8.dp),
            )
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpen() }
                .padding(vertical = 10.dp, horizontal = 6.dp),
        ) {
            Text(
                session.title,
                fontWeight = if (active) FontWeight.SemiBold
                else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                dayLabel(session.updatedAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            TextButton(onClick = { menu = true }) {
                Text("⋯", fontSize = 18.sp)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsState()
    val messages by vm.messages.collectAsState()
    val status by vm.status.collectAsState()
    val skillCount by vm.skillCount.collectAsState()
    val thinking by vm.thinking.collectAsState()
    val currentId by vm.currentSessionId.collectAsState()
    val skinState by vm.skin.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // Shared text from other apps lands in the composer (audit #20).
    val staged by vm.stagedText.collectAsState()
    LaunchedEffect(staged) {
        if (staged.isNotEmpty()) {
            input = staged
            vm.consumeStagedText()
        }
    }

    // Voice start (shared by the mic button and the Ask-Milo shortcut).
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.startDictation() }
    fun startVoice() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) vm.startDictation()
        else permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    val voiceAuto by vm.voiceAutoStart.collectAsState()
    LaunchedEffect(voiceAuto) {
        if (voiceAuto) {
            vm.consumeVoiceAutoStart()
            startVoice()
        }
    }

    // ── Chat export: share sheet or save-as-file (build 12) ─────
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val saveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts
            .CreateDocument("text/markdown")
    ) { uri ->
        val md = pendingExport
        pendingExport = null
        if (uri != null && md != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)
                        ?.use { it.write(md.toByteArray()) }
                    android.widget.Toast.makeText(
                        context, "Chat saved.", android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context, "Could not save the file.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    fun exportChat(saveToFile: Boolean) {
        val id = currentId ?: return
        scope.launch {
            val md = vm.exportThreadMarkdown(id)
            if (saveToFile) {
                pendingExport = md
                saveLauncher.launch("mymilo-chat.md")
            } else {
                val send = android.content.Intent(
                    android.content.Intent.ACTION_SEND
                ).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, md)
                }
                context.startActivity(
                    android.content.Intent.createChooser(send, "Share chat")
                )
            }
        }
    }

    // ── Dialog state (build 12) ──────────────────────────────────
    var renameTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var menuMsg by remember { mutableStateOf<MessageEntity?>(null) }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.renameSession(target.id, renameText)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this chat?") },
            text = {
                Text(
                    "“${target.title}” and all its messages will be " +
                        "gone for good."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteSession(target.id)
                        deleteTarget = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults
                        .buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.error,
                        ),
                ) { Text("Delete for good") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Keep it")
                }
            },
        )
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect this phone?") },
            text = {
                Text(
                    "Milo will stop answering on this phone until you " +
                        "connect it again with a token from the " +
                        "Devices page."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDisconnect = false
                        vm.disconnect()
                    },
                    colors = androidx.compose.material3.ButtonDefaults
                        .buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.error,
                        ),
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text("Stay connected")
                }
            },
        )
    }
    menuMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { menuMsg = null },
            title = { Text("Message") },
            text = {
                Column {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(m.content))
                        menuMsg = null
                        android.widget.Toast.makeText(
                            context, "Copied.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("Copy") }
                    TextButton(onClick = {
                        val send = android.content.Intent(
                            android.content.Intent.ACTION_SEND
                        ).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                m.content,
                            )
                        }
                        context.startActivity(
                            android.content.Intent
                                .createChooser(send, "Share message")
                        )
                        menuMsg = null
                    }) { Text("Share") }
                    TextButton(onClick = {
                        vm.readAloud(m.content)
                        menuMsg = null
                    }) { Text("Read aloud") }
                    if (m.role == "user") {
                        TextButton(onClick = {
                            vm.send(m.content)
                            menuMsg = null
                        }) { Text("Ask again") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuMsg = null }) {
                    Text("Close")
                }
            },
        )
    }

    // Auto-update prompt.
    val update by vm.updateInfo.collectAsState()
    var dismissedUpdate by remember { mutableStateOf(-1) }
    val pendingUpdate = update
    if (pendingUpdate != null && pendingUpdate.releaseNumber != dismissedUpdate) {
        AlertDialog(
            onDismissRequest = { dismissedUpdate = pendingUpdate.releaseNumber },
            title = { Text("Update available") },
            text = {
                Text(
                    "MyMilo build ${pendingUpdate.releaseNumber} is ready. " +
                        pendingUpdate.notes.take(240),
                )
            },
            confirmButton = {
                Button(onClick = {
                    dismissedUpdate = pendingUpdate.releaseNumber
                    vm.applyUpdate(context)
                }) { Text("Update now") }
            },
            dismissButton = {
                TextButton(onClick = {
                    dismissedUpdate = pendingUpdate.releaseNumber
                }) { Text("Later") }
            },
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(thinking) {
        if (thinking) {
            listState.animateScrollToItem(messages.size.coerceAtLeast(0))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxHeight().padding(16.dp)) {
                    Button(
                        onClick = {
                            vm.newSession()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("+ New chat") }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "$skillCount skills on this phone",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    var threadQuery by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = threadQuery,
                        onValueChange = { threadQuery = it },
                        placeholder = { Text("Search chats") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    val visibleSessions = if (threadQuery.isBlank()) {
                        sessions
                    } else {
                        sessions.filter {
                            it.title.contains(threadQuery, ignoreCase = true)
                        }
                    }
                    // Bounded + scrollable (build-10 fix: an unbounded
                    // LazyColumn in a plain Column ate the drawer).
                    LazyColumn(Modifier.weight(1f)) {
                        items(visibleSessions, key = { it.id }) { s ->
                            SessionRow(
                                session = s,
                                active = s.id == currentId,
                                onOpen = {
                                    vm.openSession(s.id)
                                    scope.launch { drawerState.close() }
                                },
                                onRename = {
                                    renameText = s.title
                                    renameTarget = s
                                },
                                onDelete = { deleteTarget = s },
                            )
                        }
                        if (visibleSessions.isEmpty()) {
                            item {
                                Text(
                                    if (sessions.isEmpty()) {
                                        "No chats yet — start one above."
                                    } else {
                                        "No chats match your search."
                                    },
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(
                                        vertical = 10.dp, horizontal = 6.dp
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        org.aimlds.mymilo.ui.Skin.entries.forEach { s ->
                            val current = skinState == s
                            Row(
                                modifier = Modifier
                                    .clickable { vm.setSkin(s) }
                                    .padding(
                                        horizontal = 8.dp,
                                        vertical = 8.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(
                                            s.scheme().primary,
                                            CircleShape,
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    s.label,
                                    color = if (current) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (current) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Assistant",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    var assistantStatus by remember { mutableStateOf("") }
                    // The reliable route on every OEM skin: Android's
                    // own assistant-settings screen, where MyMilo is
                    // listed because its VoiceInteractionService is
                    // valid. Try the specific screen, then the main
                    // Settings screen; always tell the user the path.
                    val openAssistantSettings = {
                        val actions = listOf(
                            android.provider.Settings
                                .ACTION_VOICE_INPUT_SETTINGS,
                            android.provider.Settings.ACTION_SETTINGS,
                        )
                        var opened = false
                        for (action in actions) {
                            try {
                                context.startActivity(
                                    android.content.Intent(action)
                                )
                                opened = true
                                break
                            } catch (e: Exception) {
                                // fall through to the next screen
                            }
                        }
                        assistantStatus = if (opened) {
                            "In Settings: Default apps → Digital " +
                                "assistant app → MyMilo."
                        } else {
                            "Couldn't open Settings automatically. Go " +
                                "to Settings → Apps → Default apps → " +
                                "Digital assistant app → MyMilo."
                        }
                    }
                    val roleLauncher = androidx.activity.compose
                        .rememberLauncherForActivityResult(
                            androidx.activity.result.contract
                                .ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            val rm = context.getSystemService(
                                android.app.role.RoleManager::class.java
                            )
                            assistantStatus = when {
                                rm != null &&
                                    rm.isRoleHeld(
                                        android.app.role.RoleManager
                                            .ROLE_ASSISTANT
                                    ) ->
                                    "Milo is your assistant ✓ Long-press " +
                                        "Home or swipe up from a bottom " +
                                        "corner to talk to Milo."

                                result.resultCode ==
                                    android.app.Activity.RESULT_OK ->
                                    "Done — Milo is set as your assistant."

                                else ->
                                    "Not set yet. Tap Assistant settings " +
                                        "below, choose Digital assistant " +
                                        "app, then MyMilo."
                            }
                        }
                    TextButton(onClick = {
                        // A tap must never be silent (build-7 bug: the
                        // role path could no-op with zero feedback).
                        android.widget.Toast.makeText(
                            context,
                            "Assistant setup…",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        try {
                            val rm = context.getSystemService(
                                android.app.role.RoleManager::class.java
                            )
                            when {
                                rm != null &&
                                    rm.isRoleHeld(
                                        android.app.role.RoleManager
                                            .ROLE_ASSISTANT
                                    ) ->
                                    assistantStatus =
                                        "Milo is already your " +
                                            "assistant ✓ Long-press Home " +
                                            "or swipe up from a bottom " +
                                            "corner to talk."

                                rm != null &&
                                    rm.isRoleAvailable(
                                        android.app.role.RoleManager
                                            .ROLE_ASSISTANT
                                    ) -> {
                                    roleLauncher.launch(
                                        rm.createRequestRoleIntent(
                                            android.app.role.RoleManager
                                                .ROLE_ASSISTANT
                                        )
                                    )
                                    assistantStatus =
                                        "Confirm in the system dialog " +
                                            "to finish."
                                }

                                else -> openAssistantSettings()
                            }
                        } catch (e: Exception) {
                            assistantStatus =
                                "The system dialog didn't open — " +
                                    "use Assistant settings below."
                            openAssistantSettings()
                        }
                    }) {
                        Text("Make Milo your assistant")
                    }
                    TextButton(onClick = { openAssistantSettings() }) {
                        Text("Assistant settings")
                    }
                    if (assistantStatus.isNotEmpty()) {
                        Text(
                            assistantStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    var diagText by remember {
                        mutableStateOf(
                            org.aimlds.mymilo.assistant.AssistantDiag
                                .last(context)
                        )
                    }
                    LaunchedEffect(drawerState.currentValue) {
                        diagText = org.aimlds.mymilo.assistant
                            .AssistantDiag.last(context)
                    }
                    if (diagText.isNotEmpty()) {
                        Text(
                            "Last assistant activity: $diagText",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Updates",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val downloadedBuild by vm.downloadedBuild
                        .collectAsState()
                    Row {
                        TextButton(
                            onClick = { vm.checkForUpdate(manual = true) }
                        ) {
                            Text("Check")
                        }
                        val avail = update
                        if (avail != null &&
                            downloadedBuild != avail.releaseNumber
                        ) {
                            TextButton(
                                onClick = { vm.downloadUpdate(context) }
                            ) {
                                Text("Download build ${avail.releaseNumber}")
                            }
                        }
                        val dl = downloadedBuild
                        if (dl != null) {
                            Button(
                                onClick = { vm.installDownloaded(context) }
                            ) {
                                Text("Install build $dl")
                            }
                        }
                    }
                    val updStatus by vm.updateStatus.collectAsState()
                    if (updStatus.isNotEmpty()) {
                        Text(
                            updStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.refreshFromServer() }) {
                        Text("Sync now")
                    }
                    TextButton(
                        onClick = { confirmDisconnect = true }
                    ) {
                        Text(
                            "Disconnect device",
                            color = Color(0xFFFF5D5D),
                        )
                    }
                    Text(
                        "MyMilo v" +
                            org.aimlds.mymilo.BuildConfig.VERSION_NAME,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(
                            horizontal = 16.dp, vertical = 4.dp
                        ),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            sessions.firstOrNull { it.id == currentId }
                                ?.title ?: "mymilo",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Text("☰", fontSize = 20.sp)
                        }
                    },
                    actions = {
                        val ttsOn by vm.ttsEnabled.collectAsState()
                        TextButton(onClick = { vm.setTtsEnabled(!ttsOn) }) {
                            Text(if (ttsOn) "🔊" else "🔇", fontSize = 18.sp)
                        }
                        var overflowOpen by remember {
                            mutableStateOf(false)
                        }
                        Box {
                            TextButton(onClick = { overflowOpen = true }) {
                                Text("⋮", fontSize = 20.sp)
                            }
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share this chat") },
                                    enabled = currentId != null,
                                    onClick = {
                                        overflowOpen = false
                                        exportChat(saveToFile = false)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Save chat as file") },
                                    enabled = currentId != null,
                                    onClick = {
                                        overflowOpen = false
                                        exportChat(saveToFile = true)
                                    },
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    val listeningNow by vm.listening.collectAsState()
                    val partial by vm.partialText.collectAsState()
                    if (listeningNow) {
                        Text(
                            "🎙 Listening… " + partial,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 2.dp
                            ),
                        )
                    } else if (status.isNotEmpty()) {
                        Text(
                            status,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 2.dp
                            ),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val listeningNow2 = listeningNow
                        Button(
                            onClick = {
                                if (listeningNow2) vm.stopDictation()
                                else startVoice()
                            },
                            colors = androidx.compose.material3.ButtonDefaults
                                .buttonColors(
                                    containerColor = if (listeningNow2) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                ),
                        ) { Text("🎤") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = input, onValueChange = { input = it },
                            placeholder = { Text("Message Milo…") },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (input.isNotBlank()) {
                                    vm.send(input)
                                    input = ""
                                }
                            },
                        ) { Text("➤") }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
            ) {
                if (messages.isEmpty() && !thinking) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 56.dp),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Hi, I'm Milo.",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ask me anything — I can work things " +
                                    "out, write with you, check the web, " +
                                    "and remember what matters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(20.dp))
                            listOf(
                                "What's 15% of 240?",
                                "Write a polite email declining a meeting",
                                "Latest news on AI chips",
                            ).forEach { suggestion ->
                                OutlinedButton(
                                    onClick = { vm.send(suggestion) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) { Text(suggestion) }
                            }
                        }
                    }
                }
                items(messages, key = { it.localId }) { m ->
                    MessageRow(
                        m = m,
                        sources = vm.parseSources(m.sourcesJson),
                        onLongPress = { menuMsg = m },
                        onOpenUrl = { url ->
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(url),
                                    )
                                )
                            } catch (e: Exception) {
                                // No browser / bad URL: stay silent-safe.
                            }
                        },
                    )
                }
                if (thinking) {
                    item {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Milo is thinking…",
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    m: MessageEntity,
    sources: List<SourceDto>,
    onLongPress: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (m.role == "user") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement =
                androidx.compose.foundation.layout.Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        colors.primary,
                        RoundedCornerShape(14.dp),
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                    .padding(12.dp),
            ) {
                Text(
                    m.content,
                    color = colors.onPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        return
    }
    // Assistant answers read full-width (Perplexity pattern), with
    // selectable text, an origin line, and the turn's sources.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        SelectionContainer {
            Text(
                m.content,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val originLabel = when (m.origin) {
            "local-tool" -> "on this phone"
            "local-model" -> "on-device model"
            "server" -> "MyMilo server"
            "queued" -> "queued"
            else -> null
        }
        if (originLabel != null) {
            Text(
                originLabel,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Sources",
                color = colors.primary,
                style = MaterialTheme.typography.labelSmall,
            )
            sources.forEach { s ->
                val url = s.url
                Text(
                    "· " + s.title,
                    color = if (!url.isNullOrBlank()) {
                        colors.primary
                    } else {
                        colors.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = if (!url.isNullOrBlank()) {
                        Modifier.clickable { onOpenUrl(url) }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
