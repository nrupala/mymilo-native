package org.aimlds.mymilo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aimlds.mymilo.local.LocalModels

/**
 * Models on this phone (v0.13.0). The picker is the
 * intelligence: this phone is measured first — memory,
 * free space, chip — and every model carries its honest
 * verdict for THIS phone before anyone downloads a byte.
 * Downloading is the only trip a model makes; after that
 * it lives here and answers with no network at all.
 */
@Composable
fun LocalModelsScreen(vm: MainViewModel) {
    val device by vm.deviceInfo.collectAsState()
    val states by vm.modelStates.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val errors by vm.downloadErrors.collectAsState()

    LaunchedEffect(Unit) { vm.refreshLocalModels() }

    SubScreen(
        title = "Models on this phone",
        subtitle = "A brain that lives here — nothing you " +
            "say to it leaves the phone",
        vm = vm,
    ) {
        LazyColumn {
            item {
                val d = device
                Text(
                    "Your phone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (d == null) {
                        "Measuring…"
                    } else {
                        "${d.chip} · ${d.cores} cores · " +
                            String.format(
                                "%.0f GB memory",
                                d.ramMB / 1024.0,
                            ) +
                            " · " +
                            LocalModels.sizeText(
                                d.freeStorageMB * 1024 * 1024,
                            ) +
                            " free"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme
                        .onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }
            items(states, key = { it.model.id }) { st ->
                val m = st.model
                val pct = downloads[m.id]
                val error = errors[m.id]
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row {
                        Text(
                            m.name,
                            style = MaterialTheme.typography
                                .titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (st.isBest && !st.downloaded) {
                            Text(
                                "★ Best for your phone",
                                style = MaterialTheme.typography
                                    .labelLarge,
                                color = MaterialTheme.colorScheme
                                    .primary,
                            )
                        }
                    }
                    Text(
                        "${m.maker} · " +
                            LocalModels.sizeText(m.sizeBytes) +
                            " download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    )
                    Text(
                        m.blurb,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (st.downloaded) {
                            "Downloaded — on this phone"
                        } else {
                            LocalModels.verdictText(st.verdict)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            st.downloaded ->
                                MaterialTheme.colorScheme.primary
                            st.verdict ==
                                LocalModels.Verdict.RUNS_WELL ->
                                MaterialTheme.colorScheme.primary
                            st.verdict ==
                                LocalModels.Verdict.WORKS_SLOWER ->
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            else ->
                                MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    when {
                        pct != null -> {
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row {
                                Text(
                                    "$pct% of " +
                                        LocalModels.sizeText(
                                            m.sizeBytes,
                                        ),
                                    style = MaterialTheme
                                        .typography.bodySmall,
                                    color = MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { vm.cancelDownload(m) }
                                ) { Text("Cancel") }
                            }
                        }
                        st.downloaded -> {
                            if (st.isActive) {
                                Text(
                                    "In use — chats set to " +
                                        "“This phone” answer " +
                                        "with this model.",
                                    style = MaterialTheme
                                        .typography.bodyMedium,
                                )
                                TextButton(
                                    onClick = { vm.deleteModel(m) }
                                ) { Text("Delete model") }
                            } else {
                                Row {
                                    Button(
                                        onClick = {
                                            vm.setActiveModel(m)
                                        },
                                        modifier = Modifier
                                            .weight(1f),
                                    ) { Text("Use this model") }
                                    TextButton(
                                        onClick = {
                                            vm.deleteModel(m)
                                        }
                                    ) { Text("Delete") }
                                }
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { vm.downloadModel(m) },
                                enabled = st.verdict ==
                                    LocalModels.Verdict.RUNS_WELL ||
                                    st.verdict ==
                                    LocalModels.Verdict
                                        .WORKS_SLOWER,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Download") }
                        }
                    }
                    if (error != null && pct == null) {
                        Text(
                            error,
                            style = MaterialTheme.typography
                                .bodySmall,
                            color = MaterialTheme.colorScheme
                                .error,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Downloads come from Hugging Face, the " +
                        "publishers' own site — that one trip " +
                        "is how the file arrives. After that " +
                        "the model is yours: it answers with " +
                        "the phone in airplane mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme
                        .onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
