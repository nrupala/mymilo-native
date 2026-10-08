package org.aimlds.mymilo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.aimlds.mymilo.data.MessageEntity
import org.aimlds.mymilo.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
            color = Color.Gray, fontSize = 13.sp,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsState()
    val messages by vm.messages.collectAsState()
    val status by vm.status.collectAsState()
    val skillCount by vm.skillCount.collectAsState()
    val skinState = vm.skin.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(16.dp)) {
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
                        color = Color.Gray, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(sessions) { s ->
                            Text(
                                s.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.openSession(s.id)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Skin",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    org.aimlds.mymilo.ui.Skin.entries.forEach { s ->
                        val current = skinState.value == s
                        Text(
                            (if (current) "● " else "○ ") + s.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setSkin(s) }
                                .padding(vertical = 8.dp),
                            color = if (current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { vm.refreshFromServer() }) {
                        Text("Sync now")
                    }
                    TextButton(onClick = { vm.disconnect() }) {
                        Text("Disconnect device", color = Color(0xFFFF5D5D))
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("mymilo") },
                    navigationIcon = {
                        TextButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", fontSize = 20.sp)
                        }
                    },
                    actions = {
                        val ttsOn by vm.ttsEnabled.collectAsState()
                        TextButton(onClick = { vm.setTtsEnabled(!ttsOn) }) {
                            Text(if (ttsOn) "🔊" else "🔇", fontSize = 18.sp)
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    } else if (status.isNotEmpty()) {
                        Text(
                            status, color = Color.Gray, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val permLauncher = androidx.activity.compose
                            .rememberLauncherForActivityResult(
                                androidx.activity.result.contract
                                    .ActivityResultContracts.RequestPermission()
                            ) { granted -> if (granted) vm.startDictation() }
                        Button(
                            onClick = {
                                val granted = androidx.core.content.ContextCompat
                                    .checkSelfPermission(
                                        context, android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    if (listeningNow) vm.stopDictation()
                                    else vm.startDictation()
                                } else {
                                    permLauncher.launch(
                                        android.Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults
                                .buttonColors(
                                    containerColor = if (listeningNow)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.secondary,
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
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            ) {
                items(messages) { m -> MessageBubble(m) }
            }
        }
    }
}

@Composable
fun MessageBubble(m: MessageEntity) {
    val isUser = m.role == "user"
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) androidx.compose.foundation.layout.Arrangement.End
        else androidx.compose.foundation.layout.Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isUser) colors.primary else colors.surfaceVariant,
                    RoundedCornerShape(14.dp),
                )
                .padding(12.dp),
        ) {
            Column {
                Text(
                    m.content,
                    color = if (isUser) colors.onPrimary else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val originLabel = when (m.origin) {
                    "local-tool" -> "on this phone"
                    "local-model" -> "on-device model"
                    "server" -> "MyMilo server"
                    "queued" -> "queued"
                    else -> null
                }
                if (!isUser && originLabel != null) {
                    Text(
                        originLabel,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
