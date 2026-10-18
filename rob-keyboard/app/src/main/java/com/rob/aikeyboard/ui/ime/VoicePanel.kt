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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.ime.Panel
import com.rob.aikeyboard.ime.RobKeyboardService
import com.rob.aikeyboard.text.Language
import com.rob.aikeyboard.translate.MissingModelsException
import com.rob.aikeyboard.translate.Translate
import kotlinx.coroutines.launch

/** Compact voice bar above the keys; dictated text goes straight into the field and can be fixed there. */
@Composable
fun VoicePanel(service: RobKeyboardService) {
    val app = RobApp.instance
    val voice = service.state.voice
    val scope = rememberCoroutineScope()
    var translating by remember { mutableStateOf(false) }
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
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    when {
                        voice.error != null -> voice.error!!
                        voice.listening && voice.partial.isNotEmpty() -> voice.partial
                        voice.listening -> "Говорите… (нажмите ■, чтобы остановить)"
                        voice.lastInserted != null -> "Вставлено: «${voice.lastInserted!!.trim()}» — можно исправить прямо в поле"
                        else -> "Нажмите на микрофон и говорите"
                    },
                    color = if (voice.error != null) ImeColors.danger else ImeColors.text,
                    fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            SmallButton("✕") { service.togglePanel(Panel.VOICE) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Language.entries.forEach { l ->
                Chip(l.code.uppercase(), voice.language == l) {
                    voice.language = l
                    if (voice.listening) service.startVoice()
                }
            }
            if (voice.lastInserted != null && !voice.listening) {
                SmallButton("Отменить") { service.undoVoice() }
                Language.entries.filter { it != voice.language }.forEach { target ->
                    SmallButton("→ ${target.code.uppercase()}", enabled = !translating) {
                        translating = true
                        scope.launch {
                            try {
                                val text = voice.lastInserted!!.trim()
                                service.replaceVoice(Translate.run(text, voice.language, target, app.prefs.translateEngine))
                            } catch (e: MissingModelsException) {
                                voice.error = "Сначала скачайте модели перевода (панель «Перевод» или настройки)."
                            } catch (e: Exception) {
                                voice.error = e.message
                            } finally {
                                translating = false
                            }
                        }
                    }
                }
            }
        }
    }
}
