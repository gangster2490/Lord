package de.spardirekt.recipeveo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.spardirekt.recipeveo.domain.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Ink,
    primaryContainer = TealDeep,
    onPrimaryContainer = Moon,
    secondary = LensGold,
    onSecondary = Ink,
    background = Ink,
    onBackground = Moon,
    surface = Stage,
    onSurface = Moon,
    surfaceVariant = StageLift,
    onSurfaceVariant = MoonMuted,
    outline = Color(0xFF314045),
    error = Danger,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = TealDeep,
    onPrimary = Color(0xFFF4FFFC),
    primaryContainer = Color(0xFFC8EBE5),
    onPrimaryContainer = PaperInk,
    secondary = Color(0xFF9A7420),
    onSecondary = Paper,
    background = Paper,
    onBackground = PaperInk,
    surface = Color(0xFFFFFFFF),
    onSurface = PaperInk,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = PaperMuted,
    outline = Color(0xFFC5D4D0),
    error = Danger,
    onError = Paper,
)

@Composable
fun RecipeVeoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = StudioTypography,
        content = content,
    )
}
