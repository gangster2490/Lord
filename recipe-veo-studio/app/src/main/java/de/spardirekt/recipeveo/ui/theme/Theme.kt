package de.spardirekt.recipeveo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.spardirekt.recipeveo.domain.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Saffron,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3E3416),
    onPrimaryContainer = Moon,
    secondary = Ember,
    onSecondary = Ink,
    background = Ink,
    onBackground = Moon,
    surface = Stage,
    onSurface = Moon,
    surfaceVariant = StageLift,
    onSurfaceVariant = MoonMuted,
    outline = Color(0xFF3A3846),
    error = Danger,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = Ember,
    onPrimary = Cream,
    primaryContainer = Color(0xFFF3D7B8),
    onPrimaryContainer = PaperInk,
    secondary = Saffron,
    onSecondary = PaperInk,
    background = Cream,
    onBackground = PaperInk,
    surface = Color(0xFFFFFBF4),
    onSurface = PaperInk,
    surfaceVariant = CreamDeep,
    onSurfaceVariant = PaperMuted,
    outline = Color(0xFFD9CDBB),
    error = Danger,
    onError = Cream,
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
