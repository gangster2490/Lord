package de.spardirekt.clipforge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Magenta,
    onPrimary = Color.White,
    secondary = Cyan,
    onSecondary = Color.Black,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    error = ErrorRed,
    onError = Color.Black,
    outline = Hairline,
    tertiary = Gold,
)

@Composable
fun ClipForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography,
        content = content,
    )
}
