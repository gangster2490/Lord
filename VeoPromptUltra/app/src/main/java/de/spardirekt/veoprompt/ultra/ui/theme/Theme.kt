package de.spardirekt.veoprompt.ultra.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object UltraColors {
    val pearl = Color(0xFFF3EEF8)
    val pearlDeep = Color(0xFFE8DFF4)
    val lavender = Color(0xFFD9CCF0)
    val midnight = Color(0xFF0B1028)
    val midnightLift = Color(0xFF151C3C)
    val navyCard = Color(0xFF121A3A)
    val violet = Color(0xFF7C4DFF)
    val electric = Color(0xFF2979FF)
    val glass = Color(0x33FFFFFF)
    val glassStrong = Color(0x55FFFFFF)
    val textOnLight = Color(0xFF16162A)
    val textMuted = Color(0xFF6B6B86)
    val textOnNavy = Color(0xFFF7F4FF)
    val textOnNavyMuted = Color(0xFFB7B8D4)
    val success = Color(0xFF3DDC97)
    val danger = Color(0xFFFF6B8A)
    val warning = Color(0xFFFFC857)
    val bottomBar = Color(0xFFF7F3FC)
    val navPill = Color(0xFFE4D7FF)
}

object UltraGradients {
    val primary = Brush.horizontalGradient(
        listOf(UltraColors.violet, UltraColors.electric)
    )
    val cardShine = Brush.verticalGradient(
        listOf(Color(0x22FFFFFF), Color.Transparent)
    )
    val hero = Brush.linearGradient(
        listOf(Color(0xFF7C4DFF), Color(0xFF5B7CFF), Color(0xFF2979FF))
    )
}

object UltraShapes {
    val card = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(22.dp)
    val button = RoundedCornerShape(24.dp)
    val thumb = RoundedCornerShape(16.dp)
}

val LocalBottomBarInset = compositionLocalOf { 0.dp }

private val UltraTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun VeoPromptUltraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = UltraColors.pearl,
            surface = UltraColors.pearl,
            primary = UltraColors.violet,
            onPrimary = Color.White,
            onBackground = UltraColors.textOnLight,
            onSurface = UltraColors.textOnLight
        ),
        typography = UltraTypography,
        content = content
    )
}
