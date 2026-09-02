package de.spardirekt.recipeveo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.recipeveo.ui.theme.GradientAccent
import de.spardirekt.recipeveo.ui.theme.GradientTitle
import de.spardirekt.recipeveo.ui.theme.LocalVppType
import de.spardirekt.recipeveo.ui.theme.VppColors
import de.spardirekt.recipeveo.ui.theme.VppShapes

@Composable
fun AppHeader(
    onNewProject: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val type = LocalVppType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GradientAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Single line: on a 360.dp phone the brand used to wrap onto three
            // rows and push the whole page down.
            Text(
                "Recipe VEO Studio",
                style = type.cardTitle.copy(color = VppColors.textDark, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "VEO 3.1 Product Ads",
                style = type.secondary.copy(color = VppColors.textMutedDark, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onNewProject != null) {
            Text(
                "Новый проект",
                style = type.badge.copy(color = VppColors.accentPurple, fontSize = 12.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Новый проект") { onNewProject() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) { role = Role.Button }
            )
        }
        if (trailing != null) {
            if (onNewProject != null) Spacer(Modifier.width(8.dp))
            trailing()
        } else {
            if (onHistory != null) {
                HeaderSquareButton(onClick = onHistory, contentDescription = "История") {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = VppColors.textLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onMenu != null) {
                Spacer(Modifier.width(8.dp))
                HeaderSquareButton(onClick = onMenu, contentDescription = "Настройки") {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = null,
                        tint = VppColors.textLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Icon-only header action. [contentDescription] is required because the glyph
 * alone tells a screen reader nothing.
 */
@Composable
fun HeaderSquareButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(VppColors.cardNavy)
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun GradientHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LocalVppType.current.screenTitle.copy(
            brush = GradientTitle,
            fontWeight = FontWeight.Bold
        ),
        modifier = modifier
    )
}

@Composable
fun NavyCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, VppShapes.cardShape, clip = false)
            .clip(VppShapes.cardShape)
            .background(VppColors.cardNavy)
            .padding(22.dp),
        content = content
    )
}

@Composable
fun LightOutlineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(VppShapes.cardShape)
            .border(1.5.dp, VppColors.outlineSoft, VppShapes.cardShape)
            .background(Color.White.copy(alpha = 0.55f))
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClickLabel = onClickLabel, onClick = onClick)
                        .semantics { role = Role.Button }
                } else {
                    Modifier
                }
            )
            .padding(18.dp),
        content = content
    )
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VppColors.cardInset)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .then(
                        if (isSelected) Modifier.background(GradientAccent)
                        else Modifier.background(Color.Transparent)
                    )
                    .clickable { onSelect(option) }
                    .semantics(mergeDescendants = true) {
                        role = Role.RadioButton
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    style = LocalVppType.current.badge.copy(
                        color = if (isSelected) Color.White else VppColors.textMuted,
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isSelected = selected
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(VppShapes.chipShape)
            .then(
                if (isSelected) Modifier.background(GradientAccent)
                else Modifier.background(VppColors.cardInset)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = LocalVppType.current.badge.copy(
                color = if (isSelected) Color.White else VppColors.textMuted,
                fontSize = 12.sp
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Label plus switch. The whole row toggles, which both widens the touch target
 * and lets a screen reader read the label and the switch as one control instead
 * of announcing a bare "switch".
 */
@Composable
fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val type = LocalVppType.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = type.cardTitle.copy(color = VppColors.textLight))
            if (description != null) {
                Text(
                    description,
                    style = type.secondary.copy(color = VppColors.textMuted, fontSize = 12.sp)
                )
            }
        }
        Switch(
            checked = checked,
            // The row owns the interaction; a second handler here would report
            // the control twice to accessibility services.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VppColors.accentPurple,
                uncheckedThumbColor = VppColors.textMuted,
                uncheckedTrackColor = VppColors.cardInset
            )
        )
    }
}

/** One row of a single-choice list, announced as a radio button. */
@Composable
fun RadioOptionRow(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) VppColors.cardInset else Color.Transparent)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), content = content)
        trailing?.invoke()
    }
}

@Composable
fun StatusPill(text: String, success: Boolean) {
    Box(
        modifier = Modifier
            .clip(VppShapes.chipShape)
            .background(if (success) Color(0xFF1E3B32) else Color(0xFF3A2430))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = LocalVppType.current.badge.copy(
                color = if (success) VppColors.success else VppColors.error
            )
        )
    }
}

@Composable
fun ProgressRail(
    stages: List<String>,
    activeIndex: Int,
    completedThrough: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        stages.forEachIndexed { index, label ->
            val done = index <= completedThrough
            val active = index == activeIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        done && !active -> {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(VppColors.success),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        active -> {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(VppColors.accentPurple)
                            )
                        }
                        else -> {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(VppColors.textMuted.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = LocalVppType.current.body.copy(
                        color = when {
                            active -> VppColors.textLight
                            done -> VppColors.textLight.copy(alpha = 0.85f)
                            else -> VppColors.textMuted
                        },
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }
    }
}
