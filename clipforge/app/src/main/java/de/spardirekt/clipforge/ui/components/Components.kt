package de.spardirekt.clipforge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.clipforge.ui.theme.Cyan
import de.spardirekt.clipforge.ui.theme.Hairline
import de.spardirekt.clipforge.ui.theme.Magenta
import de.spardirekt.clipforge.ui.theme.MagentaBorder
import de.spardirekt.clipforge.ui.theme.Surface
import de.spardirekt.clipforge.ui.theme.Surface2
import de.spardirekt.clipforge.ui.theme.TextDim
import de.spardirekt.clipforge.ui.theme.TextMid
import de.spardirekt.clipforge.ui.theme.TextPrimary

val CardShape = RoundedCornerShape(16.dp)
val ChipShape = RoundedCornerShape(22.dp)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(bottom = 8.dp),
        color = TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
    )
}

@Composable
fun ForgeCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .border(1.dp, Hairline, CardShape)
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    accent: Color = Magenta,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val isSelected = selected
    val bg = if (isSelected) accent.copy(alpha = 0.18f) else Surface2
    val border = if (isSelected) accent.copy(alpha = 0.7f) else Hairline
    val fg = if (!enabled) TextDim else if (isSelected) TextPrimary else TextMid
    Text(
        text = label,
        modifier = Modifier
            .clip(ChipShape)
            .background(bg)
            .border(1.dp, border, ChipShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = isSelected
                if (!enabled) disabled()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = fg,
        fontSize = 13.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
    )
}

@Composable
fun CopyHeader(
    title: String,
    accent: Color = Cyan,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = title,
                modifier = Modifier.padding(start = 8.dp),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCopy)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Копировать $title",
                tint = TextMid,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Копировать",
                modifier = Modifier.padding(start = 4.dp),
                color = TextMid,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(Magenta, Cyan)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("▶", color = Color.White, fontSize = 14.sp)
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = "ClipForge",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = (-0.3).sp,
            )
            Text(
                text = "TikTok Shop · Reels · Shorts",
                color = TextMid,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun RowScope.PrimaryAction(
    enabled: Boolean,
    loading: Boolean,
    label: String,
    loadingLabel: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(
                if (enabled || loading) {
                    Brush.horizontalGradient(listOf(Magenta, Color(0xFFFF6B4A)))
                } else {
                    Brush.horizontalGradient(listOf(Surface2, Surface2))
                },
            )
            .border(1.dp, if (enabled) MagentaBorder else Hairline, shape)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled || loading) disabled()
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) loadingLabel else label,
            color = if (enabled || loading) Color.White else TextDim,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(de.spardirekt.clipforge.ui.theme.ErrorBg)
            .border(1.dp, de.spardirekt.clipforge.ui.theme.ErrorBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = de.spardirekt.clipforge.ui.theme.ErrorRed,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            Text(
                "Ещё раз",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            "✕",
            color = TextMid,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(start = 8.dp),
        )
    }
}

@Composable
fun AccentPill(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(ChipShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), ChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}
