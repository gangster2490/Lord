package de.spardirekt.agents.pro.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.ProjectImage
import de.spardirekt.agents.pro.model.VoiceLanguage
import de.spardirekt.agents.pro.ui.components.LightOutlineCard
import de.spardirekt.agents.pro.ui.components.NavyCard
import de.spardirekt.agents.pro.ui.components.SegmentedControl
import de.spardirekt.agents.pro.ui.components.SelectChip
import de.spardirekt.agents.pro.ui.components.VppTags
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors

private const val PHOTOS_PER_ROW = 3

@Composable
fun PhotosSection(
    images: List<ProjectImage>,
    onAddPhotos: () -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    val type = LocalVppType.current
    NavyCard {
        Text("Ваши фото товара", style = type.cardTitle.copy(color = VppColors.textLight))
        Spacer(Modifier.height(8.dp))
        Text(
            "Можно загружать фото товара и скриншоты с описанием — агент разберёт их автоматически.",
            style = type.secondary.copy(color = VppColors.textMuted, fontSize = 12.sp)
        )
        Spacer(Modifier.height(14.dp))
        if (images.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                images.chunked(PHOTOS_PER_ROW).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEachIndexed { columnIndex, img ->
                            PhotoThumb(
                                img = img,
                                index = rowIndex * PHOTOS_PER_ROW + columnIndex,
                                onRemove = { onRemovePhoto(img.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(PHOTOS_PER_ROW - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            CreateFormRules.photoCountLabel(images.size),
            style = type.secondary.copy(color = VppColors.textMuted),
            modifier = Modifier.testTag(VppTags.PHOTO_COUNT)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(VppColors.cardInset)
                .clickable(onClickLabel = "Добавить фото", onClick = onAddPhotos)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { role = Role.Button }
                .testTag(VppTags.ADD_PHOTO_BUTTON),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = VppColors.textLight)
            Spacer(Modifier.width(8.dp))
            Text(
                "+ Добавить фото",
                style = type.body.copy(color = VppColors.textLight, fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
fun OptionalWishSection(
    expanded: Boolean,
    text: String,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val type = LocalVppType.current
    LightOutlineCard(
        onClick = onToggle,
        onClickLabel = if (expanded) "Свернуть пожелание" else "Добавить пожелание"
    ) {
        Text(
            "+ Дополнительное пожелание",
            style = type.cardTitle.copy(color = VppColors.textDark)
        )
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = type.body.copy(color = VppColors.textDark),
                    cursorBrush = SolidColor(VppColors.accentPurple),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, VppColors.outlineSoft, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isBlank()) {
                                Text(
                                    "Без людей · Показать складывание · Больше макро",
                                    style = type.secondary.copy(color = VppColors.textMutedDark)
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VoiceSection(
    voice: VoiceLanguage,
    onVoiceChange: (VoiceLanguage) -> Unit
) {
    val type = LocalVppType.current
    NavyCard {
        Text("Озвучка", style = type.cardTitle.copy(color = VppColors.textLight))
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = listOf("DE", "RU", "OFF"),
            selected = voice.name,
            onSelect = { onVoiceChange(VoiceLanguage.valueOf(it)) }
        )
    }
}

@Composable
fun ModeSection(
    mode: AppMode,
    creative: CreativeMode,
    tiktokShopMode: Boolean,
    onModeChange: (AppMode) -> Unit,
    onCreativeChange: (CreativeMode) -> Unit,
    onTiktokChange: (Boolean) -> Unit
) {
    val type = LocalVppType.current
    NavyCard {
        Text("Режим", style = type.cardTitle.copy(color = VppColors.textLight))
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = listOf("Simple", "Advanced"),
            selected = mode.name,
            onSelect = { onModeChange(AppMode.valueOf(it)) }
        )
        if (mode == AppMode.Advanced) {
            Spacer(Modifier.height(16.dp))
            Text("Креатив", style = type.secondary.copy(color = VppColors.textMuted))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CreativeMode.entries.forEach { entry ->
                    SelectChip(
                        label = entry.name,
                        selected = creative == entry,
                        onClick = { onCreativeChange(entry) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("TikTok Shop Mode", style = type.cardTitle.copy(color = VppColors.textLight))
                Text(
                    "Безопасные формулировки для Shop",
                    style = type.secondary.copy(color = VppColors.textMuted, fontSize = 12.sp)
                )
            }
            Switch(
                checked = tiktokShopMode,
                onCheckedChange = onTiktokChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = VppColors.accentPurple,
                    uncheckedThumbColor = VppColors.textMuted,
                    uncheckedTrackColor = VppColors.cardInset
                )
            )
        }
    }
}
