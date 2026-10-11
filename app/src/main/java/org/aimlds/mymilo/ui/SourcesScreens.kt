package org.aimlds.mymilo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.aimlds.mymilo.data.SourceEntity
import org.aimlds.mymilo.data.SourceTokenEntity

/**
 * Sources & Vault screens (v0.10.0). The writing rule for this
 * whole file is the owner's data-egress law: no silent egress,
 * ever — every outside destination says, in plain words and at
 * the point of choice, where the user's words go.
 */

/** Where a chat sent to this source actually goes, plainly. */
fun egressCaption(source: SourceEntity): String = when (source.kind) {
    "aetheris" ->
        "Your own server. Chats sent here stay on your systems."
    "openrouter" ->
        "Chats sent here go to OpenRouter and the company behind " +
            "the model — they leave your systems. The provider's " +
            "terms apply, and free models may train on what you send."
    "opencode" ->
        "Chats sent here go to OpenCode Zen and the company " +
            "behind the model — they leave your systems. The " +
            "provider's terms apply."
    else -> {
        val host = try {
            java.net.URI(source.baseUrl).host ?: source.baseUrl
        } catch (e: Exception) {
            source.baseUrl
        }
        "Chats sent here go to $host — they leave your systems, " +
            "unless that address is one of your own."
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "openrouter" -> "OpenRouter"
    "opencode" -> "OpenCode Zen"
    "aetheris" -> "Your server"
    else -> "Custom address"
}

@Composable
fun SourcesScreen(vm: MainViewModel) {
    val sources by vm.sourcesList.collectAsState()
    val tokens by vm.tokenRows.collectAsState()
    val aetherisKeySaved by vm.aetherisKeySaved.collectAsState()
    val aetheris = sources.firstOrNull { it.kind == "aetheris" }
    val others = sources.filter { it.kind != "aetheris" }

    var addKeyFor by remember { mutableStateOf<SourceEntity?>(null) }
    var replaceEntry by remember { mutableStateOf<SourceTokenEntity?>(null) }
    var editSource by remember { mutableStateOf<SourceEntity?>(null) }
    var addKind by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<SourceEntity?>(null) }
    var replaceAetheris by remember { mutableStateOf(false) }
    var removeAetheris by remember { mutableStateOf(false) }

    SubScreen(
        title = "Sources & keys",
        subtitle = "Every place Milo can think — and where your words go",
        vm = vm,
    ) {
        LazyColumn {
            item {
                Text(
                    "Keys live in this phone's vault, locked by the " +
                        "phone itself. Once a key is saved it can " +
                        "never be shown again — only replaced or " +
                        "deleted. That's on purpose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }
            // ── Aetheris: source #1, the default brain ──
            item {
                Text(
                    "Aetheris",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    egressCaption(
                        aetheris ?: SourceEntity(
                            "aetheris", "Aetheris", "aetheris", "", "auto", 0
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (aetherisKeySaved) {
                        "Device key saved ····"
                    } else {
                        "No key saved on this phone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    TextButton(onClick = { replaceAetheris = true }) {
                        Text("Replace key")
                    }
                    if (aetherisKeySaved) {
                        TextButton(onClick = { removeAetheris = true }) {
                            Text("Remove key")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            // ── The user's own sources ──
            items(others, key = { it.id }) { source ->
                val its = tokens.filter { it.sourceId == source.id }
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row {
                        Text(
                            source.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { editSource = source }) {
                            Text("Edit")
                        }
                        TextButton(onClick = { deleteTarget = source }) {
                            Text("Delete")
                        }
                    }
                    Text(
                        "${kindLabel(source.kind)} · answers with " +
                            source.model.ifBlank { "(no model set)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        egressCaption(source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (its.isEmpty()) {
                        Text(
                            "No key saved yet — this source can't " +
                                "answer until you add one.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    its.forEach { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.label +
                                        if (entry.active) " · in use" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "Key saved ····",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme
                                        .colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!entry.active) {
                                TextButton(
                                    onClick = { vm.setActiveToken(entry) }
                                ) { Text("Use") }
                            }
                            TextButton(
                                onClick = { replaceEntry = entry }
                            ) { Text("Replace") }
                            TextButton(
                                onClick = { vm.deleteToken(entry) }
                            ) { Text("Delete") }
                        }
                    }
                    OutlinedButton(
                        onClick = { addKeyFor = source },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add a key") }
                }
            }
            // ── Add a source ──
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Add a source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { addKind = "openrouter" },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("OpenRouter") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { addKind = "opencode" },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("OpenCode Zen") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { addKind = "custom" },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("A custom address") }
                Spacer(Modifier.height(14.dp))
                TextButton(
                    onClick = { vm.showScreen("data") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Where your data goes →") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────
    addKeyFor?.let { source ->
        KeyEntryDialog(
            title = "Add a key for ${source.name}",
            note = egressCaption(source) +
                "\n\nYou won't be able to see this key again " +
                "after saving.",
            showLabel = true,
            onDismiss = { addKeyFor = null },
            onSave = { label, secret ->
                vm.addToken(source.id, label, secret)
                addKeyFor = null
            },
        )
    }
    replaceEntry?.let { entry ->
        KeyEntryDialog(
            title = "Replace the “${entry.label}” key",
            note = "The old key is gone the moment you save. " +
                "You won't be able to see the new key again either.",
            showLabel = false,
            onDismiss = { replaceEntry = null },
            onSave = { _, secret ->
                vm.replaceToken(entry, secret)
                replaceEntry = null
            },
        )
    }
    editSource?.let { source ->
        SourceEditDialog(
            source = source,
            onDismiss = { editSource = null },
            onSave = { updated ->
                vm.saveSource(updated)
                editSource = null
            },
        )
    }
    addKind?.let { kind ->
        SourceEditDialog(
            source = when (kind) {
                "openrouter" -> SourceEntity(
                    "", "OpenRouter", "openrouter",
                    "https://openrouter.ai/api/v1",
                    "deepseek/deepseek-chat", 0,
                )
                "opencode" -> SourceEntity(
                    "", "OpenCode Zen", "opencode",
                    "https://opencode.ai/zen/v1", "big-pickle", 0,
                )
                else -> SourceEntity("", "", "custom", "https://", "", 0)
            },
            onDismiss = { addKind = null },
            onSave = { created ->
                vm.addSource(created.name, created.kind, created.baseUrl, created.model)
                addKind = null
            },
        )
    }
    deleteTarget?.let { source ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${source.name}?") },
            text = {
                Text(
                    "The source and its saved keys are removed " +
                        "from this phone. Chats that used it fall " +
                        "back to Aetheris."
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.deleteSource(source)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (replaceAetheris) {
        KeyEntryDialog(
            title = "Replace the Aetheris device key",
            note = "This is the key this phone uses with your own " +
                "server. The new key is checked against the " +
                "server before it's kept.",
            showLabel = false,
            onDismiss = { replaceAetheris = false },
            onSave = { _, secret ->
                vm.replaceAetherisKey(secret) { _, _ -> }
                replaceAetheris = false
            },
        )
    }
    if (removeAetheris) {
        AlertDialog(
            onDismissRequest = { removeAetheris = false },
            title = { Text("Remove the Aetheris key?") },
            text = {
                Text(
                    "This disconnects the phone from your server. " +
                        "Your chats stay on the phone; you can " +
                        "reconnect any time with a new key."
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.disconnect()
                    removeAetheris = false
                }) { Text("Remove & disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { removeAetheris = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun KeyEntryDialog(
    title: String,
    note: String,
    showLabel: Boolean,
    onDismiss: () -> Unit,
    onSave: (label: String, secret: String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (showLabel) {
                    OutlinedTextField(
                        value = label, onValueChange = { label = it },
                        label = { Text("Name this key (e.g. work)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = secret, onValueChange = { secret = it },
                    label = { Text("Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, secret) },
                enabled = secret.isNotBlank(),
            ) { Text("Save key") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SourceEditDialog(
    source: SourceEntity,
    onDismiss: () -> Unit,
    onSave: (SourceEntity) -> Unit,
) {
    var name by remember { mutableStateOf(source.name) }
    var baseUrl by remember { mutableStateOf(source.baseUrl) }
    var model by remember { mutableStateOf(source.model) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (source.id.isEmpty()) "Add a source" else "Edit ${source.name}")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Model it answers with") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    egressCaption(source.copy(name = name, baseUrl = baseUrl)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        source.copy(
                            name = name, baseUrl = baseUrl, model = model
                        )
                    )
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() &&
                    model.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DataControlsScreen(vm: MainViewModel) {
    val sources by vm.sourcesList.collectAsState()
    SubScreen(
        title = "Where your data goes",
        subtitle = "Every destination, in plain words",
        vm = vm,
    ) {
        LazyColumn {
            item {
                Text(
                    "This phone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Your chats are stored here. Your keys are " +
                        "locked in the phone's vault — never shown, " +
                        "never synced, never sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }
            items(sources, key = { it.id }) { source ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        source.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        egressCaption(source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Milo never sends a chat to an outside source " +
                        "unless you picked that brain for the chat — " +
                        "and the brain's name always sits above the " +
                        "message box, so you can see it before you " +
                        "send. Anything you share or save from a " +
                        "chat holds the conversation, never your keys.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
