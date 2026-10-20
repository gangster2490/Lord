package com.lord.alarm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFB547)
val Bg = Color(0xFF0E1116)
val Surface = Color(0xFF181D26)
val SurfaceHigh = Color(0xFF222936)
val Muted = Color(0xFF8B93A3)

private val colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241500),
    primaryContainer = Color(0xFF4A3300),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFF9DB3FF),
    background = Bg,
    onBackground = Color(0xFFE8EAF0),
    surface = Surface,
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF2B3342),
    outline = Color(0xFF3A4252),
)

@Composable
fun BudilnikTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
