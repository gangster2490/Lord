package com.rob.veocreator.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VeoDarkColorScheme = darkColorScheme(
    primary = VeoYellow,
    onPrimary = VeoBackground,
    secondary = VeoYellowDim,
    background = VeoBackground,
    onBackground = VeoTextPrimary,
    surface = VeoSurface,
    onSurface = VeoTextPrimary,
    surfaceVariant = VeoSurfaceElevated,
    onSurfaceVariant = VeoTextSecondary,
    error = VeoError,
    outline = VeoDivider
)

@Composable
fun VeoCreatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = VeoDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VeoTypography,
        content = content
    )
}
