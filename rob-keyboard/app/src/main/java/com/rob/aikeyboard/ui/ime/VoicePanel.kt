package com.rob.aikeyboard.ui.ime

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.aikeyboard.ime.Panel
import com.rob.aikeyboard.ime.RobKeyboardService
import com.rob.aikeyboard.text.Language

/**
 * Voice bar above the keys: choose the spoken language and the language to write in.
 * Dictated text goes straight into the field (translated offline if a different output is chosen).
 */
@Composable
fun VoicePanel(service: RobKeyboardService) {
    val voice = service.state.voice
    val pulse by animateFloatAsState(if (voice.listening) 1f + (voice.level.coerceIn(0f, 10f) / 25f) else 1f, label = "pulse")

    Column(Modifier.fillMaxWidth().background(ImeColors.bar).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).scale(pulse).clip(CircleShape)
                    .background(if (voice.listening) ImeColors.cyan else ImeColors.chip)
                    .clickable(onClickLabel = if (voice.listening) "Остановить запись" else "Начать запись") {
                        if (voice.listening) service.stopVoice() else service.startVoice()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (voice.listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = if (voice.listening) ImeColors.key else ImeColors.cyan,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                when {
                    voice.translating -> "Перевожу…"
                    voice.error != null -> voice.error!!
                    voice.listening && voice.partial.isNotEmpty() -> voice.partial
                    voice.listening -> "Говорите (${voice.language.displayName})… ■ — стоп"
                    voice.lastInserted != null -> "Вставлено: «${voice.lastInserted!!.trim()}» — можно поправить прямо в поле"
                    else -> "Нажмите 🎤 и говорите"
                },
                color = if (voice.error != null) ImeColors.danger else ImeColors.text,
                fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            SmallButton("✕") { service.togglePanel(Panel.VOICE) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Label("Говорю:")
            Language.entries.forEach { l -> Chip(l.code.uppercase(), voice.language == l) { service.setVoiceLanguage(l) } }
            Label("  Пишу:")
            Chip("как сказано", voice.output == null || voice.output == voice.language) { service.setVoiceOutput(null) }
            Language.entries.filter { it != voice.language }.forEach { l ->
                Chip(l.code.uppercase(), voice.output == l) { service.setVoiceOutput(l) }
            }
        }
        val showUndo = voice.lastInserted != null && !voice.listening
        if (showUndo || voice.offerFallback || voice.missingModels.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                if (voice.missingModels.isNotEmpty()) {
                    SmallButton("Скачать модели перевода", primary = true, enabled = !voice.translating) { service.downloadVoiceModels() }
                }
                if (voice.offerFallback) {
                    SmallButton("Голосовой ввод Google", primary = true) { service.startFallbackVoice() }
                }
                if (showUndo) SmallButton("Отменить вставку") { service.undoVoice() }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = ImeColors.textDim, fontSize = 13.sp, modifier = Modifier.padding(end = 2.dp))
}
