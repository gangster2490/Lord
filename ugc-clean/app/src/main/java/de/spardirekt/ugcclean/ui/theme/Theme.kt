package de.spardirekt.ugcclean.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B0F14)
val Surface = Color(0xFF141B24)
val Surface2 = Color(0xFF1C2633)
val Accent = Color(0xFF3DDC97)
val AccentDim = Color(0xFF1E8F62)
val TextPrimary = Color(0xFFF4F7FA)
val TextMid = Color(0xFF8B9BB4)
val TextDim = Color(0xFF5C6B80)
val Danger = Color(0xFFFF6B6B)
val Hairline = Color(0xFF2A3544)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF042016),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    error = Danger,
)

@Composable
fun UgcCleanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
