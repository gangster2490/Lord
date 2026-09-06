package de.spardirekt.clipforge.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.clipforge.data.model.AdPackage
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.ui.StudioEvent
import de.spardirekt.clipforge.ui.StudioUiState
import de.spardirekt.clipforge.ui.components.AccentPill
import de.spardirekt.clipforge.ui.components.CopyHeader
import de.spardirekt.clipforge.ui.components.ErrorBanner
import de.spardirekt.clipforge.ui.components.ForgeCard
import de.spardirekt.clipforge.ui.theme.Background
import de.spardirekt.clipforge.ui.theme.BackgroundGlow
import de.spardirekt.clipforge.ui.theme.Cyan
import de.spardirekt.clipforge.ui.theme.Gold
import de.spardirekt.clipforge.ui.theme.Hairline
import de.spardirekt.clipforge.ui.theme.Magenta
import de.spardirekt.clipforge.ui.theme.Reels
import de.spardirekt.clipforge.ui.theme.Shorts
import de.spardirekt.clipforge.ui.theme.Surface2
import de.spardirekt.clipforge.ui.theme.TextMid
import de.spardirekt.clipforge.ui.theme.TextPrimary
import de.spardirekt.clipforge.ui.theme.TikTok
import de.spardirekt.clipforge.ui.theme.Violet

@Composable
fun ResultScreen(
    state: StudioUiState,
    onEvent: (StudioEvent) -> Unit,
) {
    val ad = state.result ?: return
    val accent = platformAccent(state.platform)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundGlow, Background))),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Назад",
                tint = TextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onEvent(StudioEvent.CloseResult) }
                    .padding(8.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Пакет ролика", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "${state.platform.labelRu} · ${state.length.seconds}с · ${state.formula.labelRu}",
                    color = TextMid,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "Новый",
                color = Magenta,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onEvent(StudioEvent.RequestNewProject) }
                    .padding(8.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { ErrorBanner(it) { onEvent(StudioEvent.DismissError) } }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentPill(state.platform.labelEn, accent)
                AccentPill("${state.length.seconds}s", Cyan)
                AccentPill(state.language.label, Gold)
            }

            ForgeCard {
                CopyHeader("Товар", Gold) {
                    onEvent(StudioEvent.Copy(ad.product.visualLock, "Визуальный замок скопирован"))
                }
                Spacer(Modifier.height(8.dp))
                Text(ad.product.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(ad.product.category, color = TextMid, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(ad.product.sellingAngle, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(6.dp))
                Text("Замок: ${ad.product.visualLock}", color = TextMid, fontSize = 13.sp)
            }

            ForgeCard {
                CopyHeader("Почему конвертит", Violet) {
                    onEvent(StudioEvent.Copy(ad.whyItConverts, "Пояснение скопировано"))
                }
                Spacer(Modifier.height(8.dp))
                Text(ad.whyItConverts, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }

            ForgeCard {
                CopyHeader("5 хуков", Magenta) {
                    onEvent(StudioEvent.Copy(ad.hooks.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString("\n"), "Хуки скопированы"))
                }
                Spacer(Modifier.height(8.dp))
                ad.hooks.forEachIndexed { index, hook ->
                    Text("${index + 1}. $hook", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            ForgeCard {
                CopyHeader("Подпись и хештеги", accent) {
                    onEvent(StudioEvent.CopyCaption)
                }
                Spacer(Modifier.height(8.dp))
                Text(ad.caption, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text(ad.hashtags.joinToString("  "), color = Cyan, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text("CTA: ${ad.cta}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            ForgeCard {
                CopyHeader("Раскадровка", Cyan) {
                    val text = ad.storyboard.joinToString("\n") {
                        "${it.startSec}–${it.endSec}s · ${it.shot}\n${it.action}\nOverlay: ${it.overlay}"
                    }
                    onEvent(StudioEvent.Copy(text, "Раскадровка скопирована"))
                }
                Spacer(Modifier.height(8.dp))
                ad.storyboard.forEach { shot ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface2)
                            .padding(10.dp),
                    ) {
                        Text(
                            "${shot.startSec}–${shot.endSec}s · ${shot.shot}",
                            color = Cyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        Text(shot.action, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                        if (shot.overlay.isNotBlank()) {
                            Text("«${shot.overlay}»", color = TextMid, fontSize = 13.sp)
                        }
                    }
                }
            }

            ForgeCard {
                CopyHeader("Voiceover", Gold) {
                    onEvent(StudioEvent.Copy(ad.voiceover, "Voiceover скопирован"))
                }
                Spacer(Modifier.height(8.dp))
                MonoBlock(ad.voiceover)
                Spacer(Modifier.height(10.dp))
                Text("Музыка", color = TextMid, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(ad.music, color = TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("SFX", color = TextMid, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                MonoBlock(ad.soundEffects)
            }

            ForgeCard {
                CopyHeader("Veo 3.1 промпт", Magenta) {
                    onEvent(StudioEvent.CopyVeo)
                }
                Spacer(Modifier.height(8.dp))
                MonoBlock(ad.veoPrompt)
            }

            ForgeCard {
                CopyHeader("Превью кадра", Violet) {
                    onEvent(StudioEvent.Copy(ad.thumbnailPrompt, "Превью скопировано"))
                }
                Spacer(Modifier.height(8.dp))
                Text(ad.thumbnailPrompt, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isGenerating) {
                Text(
                    state.generateStage ?: "Собираю ролик…",
                    color = Cyan,
                    fontSize = 13.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Подпись", Modifier.weight(1f)) { onEvent(StudioEvent.CopyCaption) }
                ActionButton("Veo пакет", Modifier.weight(1f), Magenta) { onEvent(StudioEvent.CopyVeo) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Поделиться", Modifier.weight(1f)) { onEvent(StudioEvent.ShareVeo) }
                if (state.isGenerating) {
                    ActionButton("Отмена", Modifier.weight(1f), Gold) { onEvent(StudioEvent.CancelGenerate) }
                } else {
                    ActionButton("Ещё раз", Modifier.weight(1f), Gold) { onEvent(StudioEvent.Regenerate) }
                }
            }
            ActionButton("Скопировать всё", Modifier.fillMaxWidth()) {
                onEvent(StudioEvent.CopyAll)
            }
        }
    }
}

@Composable
private fun MonoBlock(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .padding(10.dp),
    )
}

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

fun platformAccent(platform: Platform) = when (platform) {
    Platform.TIKTOK_SHOP -> TikTok
    Platform.REELS -> Reels
    Platform.SHORTS -> Shorts
}
