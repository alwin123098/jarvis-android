package com.codotype.jarvis.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codotype.jarvis.data.ChatMessage
import com.codotype.jarvis.ui.components.Composer
import com.codotype.jarvis.ui.components.EmptyState
import com.codotype.jarvis.ui.components.MessageBubble
import com.codotype.jarvis.ui.components.StreamingBubble
import com.codotype.jarvis.ui.theme.ArcCyan
import com.codotype.jarvis.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    ui: UiState,
    amplitude: Float,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onInputChange: (String) -> Unit,
    onMicToggle: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleKnowledge: () -> Unit,
    onClear: () -> Unit,
    onOpenSetup: () -> Unit
) {
    val listState = rememberLazyListState()
    val itemCount = ui.messages.size + if (ui.isGenerating) 1 else 0

    LaunchedEffect(itemCount, ui.streaming) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Text(
                        text = "Jarvis",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = onToggleKnowledge) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = "Toggle knowledge",
                            tint = if (ui.useKnowledge) ArcCyan else TextSecondary
                        )
                    }
                    IconButton(onClick = onToggleVoice) {
                        Icon(
                            imageVector = if (ui.voiceOutput) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = "Toggle voice output",
                            tint = if (ui.voiceOutput) ArcCyan else TextSecondary
                        )
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear chat")
                    }
                    IconButton(onClick = onOpenSetup) {
                        Icon(Icons.Filled.Settings, contentDescription = "Models")
                    }
                }
            )
        },
        bottomBar = {
            Composer(
                input = ui.input,
                isGenerating = ui.isGenerating,
                isListening = ui.isListening,
                amplitude = amplitude,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                onMicToggle = onMicToggle
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (ui.messages.isEmpty() && !ui.isGenerating) {
                EmptyState(modifier = Modifier.padding(top = 64.dp))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                ) {
                    items(ui.messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                    if (ui.isGenerating) {
                        item { StreamingBubble(text = ui.streaming) }
                    }
                }
            }
        }
    }
}
