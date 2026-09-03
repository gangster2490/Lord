package de.spardirekt.recipeveo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Cream = Color(0xFFFFF8F1)
private val Ink = Color(0xFF2B2118)
private val Tomato = Color(0xFFB42318)

@Composable
fun RecipeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Cream,
            surface = Color.White,
            primary = Tomato,
            onBackground = Ink,
            onSurface = Ink,
            onPrimary = Color.White,
        ),
        content = content,
    )
}
