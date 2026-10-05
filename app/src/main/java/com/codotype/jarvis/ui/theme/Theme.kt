package com.codotype.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisColors = darkColorScheme(
    primary = ArcBlue,
    onPrimary = TextPrimary,
    primaryContainer = ArcBlueDark,
    secondary = ArcCyan,
    background = Charcoal,
    onBackground = TextPrimary,
    surface = CharcoalElevated,
    onSurface = TextPrimary,
    surfaceVariant = BubbleAssistant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

@Composable
fun JarvisTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColors,
        typography = JarvisTypography,
        content = content
    )
}
