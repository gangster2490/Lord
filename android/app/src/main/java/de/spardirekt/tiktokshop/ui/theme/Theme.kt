package de.spardirekt.tiktokshop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShopColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.Black,
    secondary = AccentOrange,
    onSecondary = Color.Black,
    background = Bg,
    onBackground = TextPrimary,
    surface = Bg2,
    onSurface = TextPrimary,
    surfaceVariant = Bg3,
    onSurfaceVariant = TextMid,
    outline = Border,
    error = Danger,
    onError = Color.White,
)

@Composable
fun TikTokShopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShopColorScheme,
        typography = AppTypography,
        content = content,
    )
}
