package de.spardirekt.tiktokshop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Neon,
    onPrimary = Color.Black,
    secondary = Neon,
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
)

@Composable
fun TikTokShopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography,
        content = content,
    )
}
