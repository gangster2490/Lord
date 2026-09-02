package de.spardirekt.recipeveo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.recipeveo.ui.theme.GradientAccent
import de.spardirekt.recipeveo.ui.theme.LocalVppType
import de.spardirekt.recipeveo.ui.theme.VppColors
import de.spardirekt.recipeveo.ui.theme.VppShapes

/**
 * The four button styles the product actually uses. Before this existed every
 * screen and dialog hand-rolled its own `Box + clickable`, which drifted apart
 * and exposed no button role to accessibility services.
 *
 * Callers set their own width so the same button works full-bleed or inside a row.
 */

private val DisabledGradient = Brush.horizontalGradient(
    listOf(Color(0xFF9A9AB0), Color(0xFF8A8AA0))
)

/** Primary call to action: full-height violet→blue gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press")
    Box(
        modifier = modifier
            .height(60.dp)
            .scale(scale)
            .shadow(if (enabled) 12.dp else 0.dp, VppShapes.buttonShape, clip = false)
            .clip(VppShapes.buttonShape)
            .background(if (enabled) GradientAccent else DisabledGradient)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .buttonSemantics(enabled),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = LocalVppType.current.buttonLabel.copy(color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Compact gradient button for dialogs and inline card actions. */
@Composable
fun GradientPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) GradientAccent else DisabledGradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp)
            .buttonSemantics(enabled),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = LocalVppType.current.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Secondary action. Pass [icon] to shrink the label — used where several
 * secondary actions share one row and the icon carries the meaning.
 *
 * [contentColor] defaults to the on-navy label because most outlined actions sit
 * inside a dark card; pass [VppColors.textDark] when the button sits directly on
 * the light page, otherwise the label washes out against it.
 */
@Composable
fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    height: Dp = 52.dp,
    contentColor: Color = VppColors.textLight
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(VppShapes.buttonShape)
            .border(1.5.dp, VppColors.accentPurple.copy(alpha = 0.55f), VppShapes.buttonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .buttonSemantics(enabled = true),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = VppColors.accentPurple,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = LocalVppType.current.buttonLabel.copy(
                color = contentColor,
                fontSize = if (icon != null) 13.sp else 14.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** Outlined button for irreversible actions such as deleting the API key. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, VppColors.error.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .buttonSemantics(enabled = true),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = VppColors.error, style = LocalVppType.current.body)
    }
}

/**
 * Merges the label into one accessibility node and reports the button role, so
 * TalkBack announces "button" and UI automation can find the control by its text.
 */
private fun Modifier.buttonSemantics(enabled: Boolean): Modifier =
    semantics(mergeDescendants = true) {
        role = Role.Button
        if (!enabled) disabled()
    }
