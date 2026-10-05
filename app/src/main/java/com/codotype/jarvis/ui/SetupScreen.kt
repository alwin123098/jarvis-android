package com.codotype.jarvis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codotype.jarvis.model.ModelCatalog
import com.codotype.jarvis.model.ModelKind
import com.codotype.jarvis.model.ModelSpec
import com.codotype.jarvis.ui.theme.ArcCyan
import com.codotype.jarvis.ui.theme.TextSecondary

@Composable
fun SetupScreen(
    ui: UiState,
    onDownload: (ModelSpec) -> Unit,
    onDelete: (ModelSpec) -> Unit,
    onSelectLlm: (ModelSpec) -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Set up Jarvis", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Text(
                "The app itself is tiny (~10 MB). The AI brain, speech, and voice " +
                    "models are downloaded once from Hugging Face and then run fully " +
                    "offline on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        if (!ui.nativeAvailable) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "Native engine not built. Rebuild with " +
                            "-Pjarvis.buildNative=true after running scripts/fetch_native.sh.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            SectionTitle("Language model (required)")
        }
        items(ModelCatalog.all.filter { it.kind == ModelKind.LLM }) { spec ->
            ModelRow(spec, ui, onDownload, onDelete, onSelectLlm, selectable = true)
        }

        item { SectionTitle("Speech recognition (required)") }
        items(ModelCatalog.all.filter { it.kind == ModelKind.STT }) { spec ->
            ModelRow(spec, ui, onDownload, onDelete, onSelectLlm, selectable = false)
        }

        item { SectionTitle("Voice output (optional)") }
        items(ModelCatalog.all.filter { it.kind == ModelKind.TTS }) { spec ->
            ModelRow(spec, ui, onDownload, onDelete, onSelectLlm, selectable = false)
        }

        item {
            Button(
                onClick = onContinue,
                enabled = ui.hasLlm && ui.hasStt,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(if (ui.hasLlm && ui.hasStt) "Start chatting" else "Download required models to continue")
            }
        }
        item {
            Text(
                "Models: ${ModelCatalog.defaultLlm.repo}, ${ModelCatalog.defaultStt.repo}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ModelRow(
    spec: ModelSpec,
    ui: UiState,
    onDownload: (ModelSpec) -> Unit,
    onDelete: (ModelSpec) -> Unit,
    onSelectLlm: (ModelSpec) -> Unit,
    selectable: Boolean
) {
    val downloaded = spec.id in ui.downloadedIds
    val active = ui.activeLlmId == spec.id
    val downloading = ui.downloading?.takeIf { it.specId == spec.id }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        spec.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "≈ ${spec.approxSizeMb} MB · ${spec.license}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                if (active) {
                    Text("Active", color = ArcCyan, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                spec.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (downloading != null) {
                LinearProgressIndicator(
                    progress = { downloading.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                Text(
                    "${(downloading.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        downloaded && selectable && !active -> {
                            OutlinedButton(onClick = { onSelectLlm(spec) }) { Text("Use") }
                            TextButton(onClick = { onDelete(spec) }) { Text("Delete") }
                        }
                        downloaded -> {
                            Text("Downloaded", color = ArcCyan, style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { onDelete(spec) }) { Text("Delete") }
                        }
                        else -> {
                            Button(onClick = { onDownload(spec) }) { Text("Download") }
                        }
                    }
                }
            }
        }
    }
}
