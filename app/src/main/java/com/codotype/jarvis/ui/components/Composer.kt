package com.codotype.jarvis.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.codotype.jarvis.ui.theme.ArcBlue
import com.codotype.jarvis.ui.theme.ArcCyan
import com.codotype.jarvis.ui.theme.ErrorRed

@Composable
fun Composer(
    input: String,
    isGenerating: Boolean,
    isListening: Boolean,
    amplitude: Float,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message Jarvis…") },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp)
        )

        MicButton(
            isListening = isListening,
            amplitude = amplitude,
            onClick = onMicToggle
        )

        val primaryAction: () -> Unit = if (isGenerating) onStop else onSend
        IconButton(
            onClick = primaryAction,
            enabled = isGenerating || input.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .background(if (isGenerating) ErrorRed else ArcBlue, CircleShape)
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                contentDescription = if (isGenerating) "Stop" else "Send",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun MicButton(isListening: Boolean, amplitude: Float, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse"
    )
    val scale = if (isListening) 1f + amplitude.coerceIn(0f, 1f) * 0.35f * pulse else 1f

    Box(
        modifier = Modifier.size(48.dp).scale(scale),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(if (isListening) ArcCyan else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = if (isListening) "Stop listening" else "Speak",
                tint = if (isListening) MaterialTheme.colorScheme.background
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
