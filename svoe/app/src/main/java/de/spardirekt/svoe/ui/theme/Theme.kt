package de.spardirekt.svoe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.spardirekt.svoe.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Cream,
    primaryContainer = TerracottaSoft,
    onPrimaryContainer = Ink,
    secondary = Sage,
    onSecondary = Cream,
    tertiary = Gold,
    background = Paper,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFD4C8B8),
    error = Danger,
    onError = Cream,
)

private val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF1C140C),
    primaryContainer = Color(0xFF5A3318),
    onPrimaryContainer = Moon,
    secondary = NightSage,
    onSecondary = Night,
    tertiary = Color(0xFFE2C57A),
    background = Night,
    onBackground = Moon,
    surface = NightCard,
    onSurface = Moon,
    surfaceVariant = NightInset,
    onSurfaceVariant = MoonMuted,
    outline = Color(0xFF3A332C),
    error = Color(0xFFFF8A80),
    onError = Night,
)

@Composable
fun SvoeTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = SvoeTypography,
        content = content,
    )
}
