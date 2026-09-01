package de.spardirekt.agents.pro.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Runtime layout metrics. These are not part of the locked visual baseline —
 * colors, radii, and the type scale all stay in Theme.kt.
 */
object VppLayout {
    /** Gap between the scrolling content and whatever floats over its bottom edge. */
    val floatingContentGap: Dp = 16.dp
    val sectionGap: Dp = 14.dp
    val screenTopPadding: Dp = 12.dp
}

/**
 * Height the bottom navigation currently occupies, including the system gesture
 * inset. The nav host measures the real bar and publishes it here so screens can
 * pad their scrolling content exactly, instead of guessing with a fixed value
 * that clipped the last card on short screens.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }
