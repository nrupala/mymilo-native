package org.aimlds.mymilo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.aimlds.mymilo.BuildConfig
import org.aimlds.mymilo.data.SkillEntity

/** Shared chrome for the Guide & About sub-screens. */
@Composable
private fun SubScreen(
    title: String,
    subtitle: String,
    vm: MainViewModel,
    content: @Composable () -> Unit,
) {
    // Assessment F2: the system Back button returns to chat
    // instead of leaving the app from a sub-screen.
    androidx.activity.compose.BackHandler { vm.showScreen("chat") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.showScreen("chat") }) {
                Text("← Back")
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun SkillsScreen(vm: MainViewModel) {
    val workspace by vm.workspaceSkill.collectAsState()
    if (workspace != null) {
        SkillWorkspace(workspace!!, vm)
        return
    }
    val catalog by vm.skillCatalog.collectAsState()
    val pins by vm.pinnedSkills.collectAsState()
    var query by remember { mutableStateOf("") }

    SubScreen(
        title = "Skills",
        subtitle = "${catalog.size} skills on this phone — " +
            "tap one to put it to work.",
        vm = vm,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search skills") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        val filtered = if (query.isBlank()) {
            catalog
        } else {
            catalog.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.blurb.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }
        }
        val pinned = filtered.filter { pins.contains(it.name) }
        val byCategory = filtered.groupBy { it.category }
        val orderedCats = CATEGORY_ORDER.filter { byCategory.containsKey(it) } +
            byCategory.keys.filter { it !in CATEGORY_ORDER }.sorted()
        LazyColumn(Modifier.fillMaxSize()) {
            if (catalog.isEmpty()) {
                item {
                    Text(
                        "Skills are still loading. Connect and tap " +
                            "Sync now in the drawer, then come back.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            if (pinned.isNotEmpty()) {
                item {
                    Text(
                        "Your favorites",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(pinned, key = { "pin-" + it.name }) { s ->
                    SkillRow(s, pins.contains(s.name),
                        onStar = { vm.togglePin(s.name) },
                        onTap = { vm.openWorkspace(s) })
                }
            }
            for (cat in orderedCats) {
                item {
                    Text(
                        "$cat · ${byCategory[cat]?.size ?: 0}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(byCategory[cat].orEmpty(), key = { it.name }) { s ->
                    SkillRow(s, pins.contains(s.name),
                        onStar = { vm.togglePin(s.name) },
                        onTap = { vm.openWorkspace(s) })
                }
            }
            if (filtered.isEmpty() && catalog.isNotEmpty()) {
                item {
                    Text(
                        "No skills match your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}


@Composable
private fun SkillRow(
    s: SkillEntity,
    pinned: Boolean,
    onStar: () -> Unit,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                humanSkillName(s.name),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                s.blurb.ifBlank { s.description },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onStar) {
            Text(
                if (pinned) "★" else "☆",
                color = if (pinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
fun GuideScreen(vm: MainViewModel) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        try {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                )
            )
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context, "No browser available.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    SubScreen(
        title = "Help & guide",
        subtitle = "What Milo can do, and how to ask.",
        vm = vm,
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Quick start",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            items(QUICK_START) { step ->
                Text(
                    "•  $step",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Use cases",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            items(USE_CASES) { uc ->
                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(uc.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            uc.line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        )
                        Text(
                            "“${uc.prompt}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        TextButton(onClick = {
                            val skill = uc.skillName
                            if (skill != null) {
                                vm.runSkill(
                                    skill, humanSkillName(skill), uc.prompt
                                )
                            } else {
                                vm.stageSharedText(uc.prompt)
                                vm.showScreen("chat")
                            }
                        }) { Text("Try it") }
                    }
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Questions, answered",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            items(FAQS) { (q, a) ->
                var open by remember(q) { mutableStateOf(false) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open = !open }
                        .padding(vertical = 8.dp),
                ) {
                    Text(q, fontWeight = FontWeight.SemiBold)
                    if (open) {
                        Text(
                            a,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { openUrl("https://mymilo.aimlds.org/guide") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Read this guide in your browser") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.showScreen("about") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("About MyMilo") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun AboutScreen(vm: MainViewModel) {
    val supportUrl by vm.supportUrl.collectAsState()
    val context = LocalContext.current
    fun openUrl(url: String) {
        try {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                )
            )
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context, "Could not open the link.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    SubScreen(
        title = "About",
        subtitle = "Who made this, and where it lives.",
        vm = vm,
    ) {
        Column {
            Text(
                "MyMilo",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Your personal AI assistant — your server, your " +
                    "data, your skills.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} · " +
                    "build ${BuildConfig.BUILD_NUMBER - 500}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Text("Owned by Nrupal Akolkar")
            Text(
                "Built with Muse by Meta",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = {
                    openUrl("https://github.com/nrupala/mymilo")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Source code — the server") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    openUrl("https://github.com/nrupala/mymilo-native")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Source code — this app") }
            if (supportUrl.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { openUrl(supportUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("❤ Support MyMilo") }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Your chats and memory live on your own server. " +
                    "Nothing is sold, and there are no ads — ever.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Skill Pair Program — the workspace shell.
 *
 * A skill is a professional tool and its layout is part of the
 * tool: tapping a skill opens its own room, shaped by its pair
 * data (archetype + layout). The frame is one; the shape inside
 * comes from the skill — steps for engines, the four blocks for
 * reference skills, inputs it needs stated before it starts.
 * Skills without pair data yet get the Knowledge shape: what it
 * does, the example, the full method, and the run box. */
@Composable
fun SkillWorkspace(s: SkillEntity, vm: MainViewModel) {
    androidx.activity.compose.BackHandler { vm.closeWorkspace() }
    val layout = remember(s.name, s.layoutJson) {
        try {
            if (s.layoutJson.isBlank()) null
            else org.json.JSONObject(s.layoutJson)
        } catch (e: Exception) {
            null
        }
    }
    val steps = remember(layout) {
        val arr = layout?.optJSONArray("steps") ?: return@remember emptyList()
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }
    val blocks = remember(layout) {
        val arr = layout?.optJSONArray("blocks") ?: return@remember emptyList()
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }
    val inputs = remember(layout) {
        val arr = layout?.optJSONArray("inputs") ?: return@remember emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val label = o.optString("label")
            if (label.isBlank()) null
            else label to o.optBoolean("required", false)
        }
    }
    var prompt by remember(s.name) { mutableStateOf("") }
    var showMethod by remember(s.name) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.closeWorkspace() }) {
                Text("← Skills")
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    humanSkillName(s.name),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    s.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    s.blurb.ifBlank { s.description },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Runs on your Aetheris server — this conversation " +
                        "stays on your own systems.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (steps.isNotEmpty()) {
                item {
                    WorkspaceSection("How it works")
                }
                items(steps.size) { i ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            steps[i],
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (blocks.isNotEmpty()) {
                item {
                    WorkspaceSection("Every answer follows this shape")
                }
                items(blocks) { b ->
                    val note = when (b) {
                        "Answer" -> "the direct answer, cited"
                        "Why" -> "the reasoning behind it"
                        "Proof" -> "what would verify it"
                        "Limits" -> "what it does not cover"
                        else -> ""
                    }
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            b,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        if (note.isNotBlank()) {
                            Text(
                                note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (inputs.size > 1 || (inputs.size == 1 && s.archetype != "reference")) {
                item {
                    WorkspaceSection("Have ready")
                }
                items(inputs) { (label, required) ->
                    Text(
                        "• $label" + if (required) "" else " (optional)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            if (s.example.isNotBlank()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Example: “${s.example}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = {
                        Text(
                            when (s.archetype) {
                                "engine" -> "Tell it what you have — " +
                                    "it walks the steps with you."
                                "reference" -> "Ask your question…"
                                else -> "What should this skill work on?"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.runSkill(s.name, humanSkillName(s.name), prompt)
                    },
                    enabled = prompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Run ${humanSkillName(s.name)}") }
            }
            item {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { showMethod = !showMethod }) {
                    Text(
                        if (showMethod) "Hide the full method"
                        else "Read the full method"
                    )
                }
                if (showMethod) {
                    Text(
                        s.content,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}
