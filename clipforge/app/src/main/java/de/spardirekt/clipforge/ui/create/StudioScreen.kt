package de.spardirekt.clipforge.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.spardirekt.clipforge.data.model.AdFormula
import de.spardirekt.clipforge.data.model.AdLanguage
import de.spardirekt.clipforge.data.model.AdLength
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.data.model.ProductPhoto
import de.spardirekt.clipforge.data.model.VisualStyle
import de.spardirekt.clipforge.ui.StudioEvent
import de.spardirekt.clipforge.ui.StudioUiState
import de.spardirekt.clipforge.ui.StudioViewModel
import de.spardirekt.clipforge.ui.components.BrandMark
import de.spardirekt.clipforge.ui.components.ChoiceChip
import de.spardirekt.clipforge.ui.components.ErrorBanner
import de.spardirekt.clipforge.ui.components.ForgeCard
import de.spardirekt.clipforge.ui.components.PrimaryAction
import de.spardirekt.clipforge.ui.components.SectionLabel
import de.spardirekt.clipforge.ui.theme.Background
import de.spardirekt.clipforge.ui.theme.BackgroundGlow
import de.spardirekt.clipforge.ui.theme.Cyan
import de.spardirekt.clipforge.ui.theme.Hairline
import de.spardirekt.clipforge.ui.theme.Magenta
import de.spardirekt.clipforge.ui.theme.Reels
import de.spardirekt.clipforge.ui.theme.Shorts
import de.spardirekt.clipforge.ui.theme.Surface2
import de.spardirekt.clipforge.ui.theme.TextDim
import de.spardirekt.clipforge.ui.theme.TextMid
import de.spardirekt.clipforge.ui.theme.TextPrimary
import de.spardirekt.clipforge.ui.theme.TikTok

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    state: StudioUiState,
    onEvent: (StudioEvent) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(StudioViewModel.MAX_PHOTOS),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onEvent(StudioEvent.PhotosPicked(uris, uris.map { it.lastPathSegment }))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BackgroundGlow, Background)),
            ),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandMark()
            Text(
                text = "Собери сильный ролик товара за один проход: хук, раскадровка, Veo-промпт и подпись под площадку.",
                color = TextMid,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            ForgeCard {
                SectionLabel("Фото товара")
                Text(
                    text = "До 8 кадров: товар, детали, использование, скрин описания. Главное — как выглядит продукт.",
                    color = TextMid,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                PhotoStrip(
                    photos = state.photos,
                    onAdd = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemove = { onEvent(StudioEvent.PhotoRemoved(it)) },
                )
            }

            ForgeCard {
                SectionLabel("Площадка")
                PlatformRow(state.platform) { onEvent(StudioEvent.PlatformChanged(it)) }
                Spacer(Modifier.height(12.dp))
                SectionLabel("Хронометраж")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdLength.entries.forEach { length ->
                        ChoiceChip(
                            label = length.label,
                            selected = state.length == length,
                            accent = Cyan,
                            onClick = { onEvent(StudioEvent.LengthChanged(length)) },
                        )
                    }
                }
            }

            ForgeCard {
                SectionLabel("Формула ролика")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdFormula.entries.forEach { formula ->
                        ChoiceChip(
                            label = formula.labelRu,
                            selected = state.formula == formula,
                            onClick = { onEvent(StudioEvent.FormulaChanged(formula)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionLabel("Визуальный стиль")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VisualStyle.entries.forEach { style ->
                        ChoiceChip(
                            label = style.labelRu,
                            selected = state.style == style,
                            accent = Cyan,
                            onClick = { onEvent(StudioEvent.StyleChanged(style)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionLabel("Язык речи и текста")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdLanguage.entries.forEach { language ->
                        ChoiceChip(
                            label = language.label,
                            selected = state.language == language,
                            onClick = { onEvent(StudioEvent.LanguageChanged(language)) },
                        )
                    }
                }
            }

            ForgeCard {
                SectionLabel("Пожелание (необязательно)")
                OutlinedTextField(
                    value = state.wish,
                    onValueChange = { onEvent(StudioEvent.WishChanged(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("wish"),
                    placeholder = { Text("Например: показать на рыбалке, без студии") },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Magenta,
                        unfocusedBorderColor = Hairline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Magenta,
                        focusedPlaceholderColor = TextDim,
                        unfocusedPlaceholderColor = TextDim,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            state.error?.let {
                ErrorBanner(
                    message = it,
                    onRetry = { onEvent(StudioEvent.Generate) },
                ) { onEvent(StudioEvent.DismissError) }
            }
            when {
                state.isDemo -> Text(
                    "Демо-режим: пакет соберётся локально, без сети.",
                    color = Cyan,
                    fontSize = 12.sp,
                )
                state.generateBlockedReason != null -> Text(
                    state.generateBlockedReason!!,
                    color = TextMid,
                    fontSize = 12.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row {
                PrimaryAction(
                    enabled = state.canGenerate,
                    loading = state.isGenerating,
                    label = "Собрать ролик",
                    loadingLabel = state.generateStage ?: "Собираю раскадровку…",
                    onClick = { onEvent(StudioEvent.Generate) },
                )
            }
            if (state.isGenerating) {
                Text(
                    text = "Отменить",
                    color = TextMid,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { onEvent(StudioEvent.CancelGenerate) }
                        .testTag("cancel_generate"),
                )
            }
        }
    }
}

@Composable
private fun PhotoStrip(
    photos: List<ProductPhoto>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        photos.forEachIndexed { index, photo ->
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp)),
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = "Фото товара ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Background.copy(alpha = 0.75f))
                        .clickable { onRemove(photo.uri) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Удалить фото ${index + 1}",
                        tint = TextPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                .clickable(onClick = onAdd)
                .testTag("add_photos"),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Add, contentDescription = "Добавить фото", tint = Magenta)
                Text("Фото", color = TextMid, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PlatformRow(
    selected: Platform,
    onSelect: (Platform) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Platform.entries.forEach { platform ->
            val accent = when (platform) {
                Platform.TIKTOK_SHOP -> TikTok
                Platform.REELS -> Reels
                Platform.SHORTS -> Shorts
            }
            val hint = when (platform) {
                Platform.TIKTOK_SHOP -> "CTA в корзине · без «ссылки в био»"
                Platform.REELS -> "CTA в профиле · эстетичная подпись"
                Platform.SHORTS -> "CTA в описании · короткий заголовок"
            }
            val isChosen = selected == platform
            val selectedBg = if (isChosen) accent.copy(alpha = 0.14f) else Surface2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(selectedBg)
                    .border(
                        1.dp,
                        if (isChosen) accent.copy(alpha = 0.7f) else Hairline,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(platform) }
                    .semantics(mergeDescendants = true) {
                        role = Role.RadioButton
                        this.selected = isChosen
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(platform.labelRu, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(hint, color = TextMid, fontSize = 12.sp)
                }
            }
        }
    }
}
