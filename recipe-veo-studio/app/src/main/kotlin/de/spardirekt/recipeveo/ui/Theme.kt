package de.spardirekt.recipeveo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Cream = Color(0xFFFAF6F0)
private val Ink = Color(0xFF1C1917)
private val Accent = Color(0xFF0F766E)

@Composable
fun RecipeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Cream,
            surface = Color.White,
            primary = Accent,
            onBackground = Ink,
            onSurface = Ink,
            onPrimary = Color.White,
        ),
        content = content,
    )
}
