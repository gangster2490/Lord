package de.spardirekt.veoprompt.ultra.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.spardirekt.veoprompt.ultra.model.AppMode
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.ProjectImage
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import de.spardirekt.veoprompt.ultra.ui.components.Chip
import de.spardirekt.veoprompt.ultra.ui.components.GradientButton
import de.spardirekt.veoprompt.ultra.ui.components.NavyCard
import de.spardirekt.veoprompt.ultra.ui.components.PearlCard
import de.spardirekt.veoprompt.ultra.ui.theme.LocalBottomBarInset
import de.spardirekt.veoprompt.ultra.ui.theme.UltraColors
import de.spardirekt.veoprompt.ultra.ui.theme.UltraGradients

@Composable
fun CreateScreen(
    viewModel: CreateViewModel,
    onOpenResult: (String) -> Unit,
    onOpenGeneration: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.bootstrap() }
    LaunchedEffect(state.navigateToGenerationId) {
        val id = state.navigateToGenerationId ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        onOpenGeneration(id)
    }
    LaunchedEffect(state.navigateToResultId) {
        val id = state.navigateToResultId ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        onOpenResult(id)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(15)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    val bottom = LocalBottomBarInset.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UltraColors.pearl)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottom + 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Veo Prompt Ultra", fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = UltraColors.textOnLight)
                    Text("VEO 3.1 Product Ads", color = UltraColors.textMuted, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(UltraGradients.primary)
                        .clickable { viewModel.newProject() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Новый проект", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        item(key = "hero") {
            NavyCard {
                Text("Создай точный VEO Prompt", color = UltraColors.textOnNavy, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Фото товара → анализ → готовая реклама за 8 секунд",
                    color = UltraColors.textOnNavyMuted,
                    fontSize = 14.sp
                )
            }
        }
        item(key = "photos") {
            PearlCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Фото товара", fontWeight = FontWeight.SemiBold, color = UltraColors.textOnLight, modifier = Modifier.weight(1f))
                    Text("${state.images.size} / 15", color = UltraColors.textMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "add") {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(UltraColors.pearlDeep)
                                .border(1.dp, UltraColors.violet.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .clickable {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Добавить", tint = UltraColors.violet)
                        }
                    }
                    items(state.images, key = { it.id }) { img ->
                        PhotoThumb(img, onRemove = { viewModel.removeImage(img.id) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Добавляй фото товара и скриншоты описания — агент разберёт их автоматически.",
                    color = UltraColors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        item(key = "wish") {
            PearlCard {
                Text(
                    "+ Дополнительное пожелание",
                    color = UltraColors.violet,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { viewModel.toggleWish() }
                )
                if (state.wishExpanded) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.optionalWish,
                        onValueChange = viewModel::setWish,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Например: акцент на крышку") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                }
            }
        }
        item(key = "voice") {
            PearlCard {
                Text("Озвучка", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceLanguage.entries.forEach { v ->
                        Chip(v.name, state.voice == v, onClick = { viewModel.setVoice(v) })
                    }
                }
            }
        }
        item(key = "creative") {
            PearlCard {
                Text("Creative", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val modes = CreativeMode.entries
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { mode ->
                                Chip(mode.uiLabel(), state.creative == mode, onClick = { viewModel.setCreative(mode) })
                            }
                        }
                    }
                }
            }
        }
        item(key = "mode") {
            PearlCard {
                Text("Режим", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Simple", state.mode == AppMode.Simple) { viewModel.setMode(AppMode.Simple) }
                    Chip("Advanced", state.mode == AppMode.Advanced) { viewModel.setMode(AppMode.Advanced) }
                }
                if (state.mode == AppMode.Advanced) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Advanced: в результате будут visual signature, Product Lock и полный Safety Audit.",
                        color = UltraColors.textMuted,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TikTok Shop Mode", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(
                        checked = state.tiktokShopMode,
                        onCheckedChange = viewModel::setTiktok,
                        colors = SwitchDefaults.colors(checkedTrackColor = UltraColors.violet)
                    )
                }
            }
        }
        item(key = "cta") {
            val enabled = CreateFormRules.canGenerate(state.images.size, state.isGenerating)
            GradientButton(
                text = "✦ Создать VEO Prompt",
                onClick = { viewModel.onGenerate() },
                enabled = enabled
            )
            val hint = CreateFormRules.blockingHint(state.images.size, state.isGenerating)
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(hint, color = UltraColors.textMuted, fontSize = 12.sp)
            }
            if (state.errorMessage.isNotBlank() && !state.isGenerating) {
                Spacer(Modifier.height(8.dp))
                Text(state.errorMessage, color = UltraColors.danger, fontSize = 13.sp)
            }
        }
    }

    if (state.showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissApiDialog,
            title = { Text("OpenAI API ключ") },
            text = {
                OutlinedTextField(
                    value = state.apiKeyInput,
                    onValueChange = viewModel::setApiKeyInput,
                    placeholder = { Text("sk-...") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveApiKeyAndContinue) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissApiDialog) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun PhotoThumb(img: ProjectImage, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(84.dp)) {
        AsyncImage(
            model = img.localPath ?: img.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UltraColors.midnight.copy(alpha = 0.75f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(img.category.badgeLabel(), color = Color.White, fontSize = 9.sp)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Удалить", tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}
