package com.rob.neon.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Graphite = Color(0xFF0D0F13)
val Panel = Color(0xFF171A20)
val PanelHi = Color(0xFF20242C)
val Steel = Color(0xFFC9CED8)
val Blue = Color(0xFF3F7BFF)
val Red = Color(0xFFE0343E)

private val Scheme = darkColorScheme(
    primary = Blue, onPrimary = Color.White,
    secondary = Steel, onSecondary = Graphite,
    tertiary = Red,
    background = Graphite, onBackground = Color(0xFFE8EAEE),
    surface = Panel, onSurface = Color(0xFFE8EAEE),
    surfaceVariant = PanelHi, onSurfaceVariant = Color(0xFFB4BAC6),
    outline = Color(0xFF3A4050),
    error = Red,
)

@Composable
fun RobNeonTheme(content: @Composable () -> Unit) {
    // The pack is dark by design; the UI stays dark regardless of the system setting.
    MaterialTheme(colorScheme = Scheme, content = content)
}
