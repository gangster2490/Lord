package de.spardirekt.veoprompt.ultra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.veoprompt.ultra.ui.theme.UltraColors
import de.spardirekt.veoprompt.ultra.ui.theme.UltraGradients
import de.spardirekt.veoprompt.ultra.ui.theme.UltraShapes

@Composable
fun NavyCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(18.dp, UltraShapes.card, ambientColor = Color(0x33121A3A), spotColor = Color(0x44121A3A))
            .clip(UltraShapes.card)
            .background(UltraColors.navyCard)
            .background(UltraGradients.cardShine)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun PearlCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(10.dp, UltraShapes.card, ambientColor = Color(0x14000000), spotColor = Color(0x1A7C4DFF))
            .clip(UltraShapes.card)
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.8f), UltraShapes.card)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val brush = if (enabled) UltraGradients.primary else Brush.horizontalGradient(
        listOf(Color(0xFFB8B4C8), Color(0xFF9AA0B8))
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(if (enabled) 12.dp else 0.dp, UltraShapes.button, spotColor = UltraColors.violet)
            .clip(UltraShapes.button)
            .background(brush)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(UltraShapes.button)
            .background(UltraColors.navyCard.copy(alpha = 0.08f))
            .border(1.dp, UltraColors.violet.copy(alpha = 0.35f), UltraShapes.button)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = UltraColors.midnight, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) UltraGradients.primary else Brush.horizontalGradient(
        listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.7f))
    )
    val fg = if (selected) Color.White else UltraColors.textOnLight
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
