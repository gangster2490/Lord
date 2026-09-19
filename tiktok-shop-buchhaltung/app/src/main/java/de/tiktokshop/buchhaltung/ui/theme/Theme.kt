package de.tiktokshop.buchhaltung.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TikTokPink = Color(0xFFFE2C55)
private val TikTokCyan = Color(0xFF25F4EE)

private val LightColors = lightColorScheme(
    primary = TikTokPink,
    secondary = TikTokCyan,
)

private val DarkColors = darkColorScheme(
    primary = TikTokPink,
    secondary = TikTokCyan,
)

@Composable
fun TikTokBuchhaltungTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
