package com.rob.aikeyboard.ui.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.ai.AiAction
import com.rob.aikeyboard.ai.AiPrompts
import com.rob.aikeyboard.ai.Tone
import com.rob.aikeyboard.data.AiProvider
import com.rob.aikeyboard.ime.Panel
import com.rob.aikeyboard.ime.RobKeyboardService
import com.rob.aikeyboard.text.Language
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * AI Writing: shows exactly which text will be sent, sends it only on "Отправить", and never
 * changes the field until the user taps "Применить".
 */
@Composable
fun AiWritingPanel(service: RobKeyboardService, height: Dp) {
    val app = RobApp.instance
    val state = service.state
    val scope = rememberCoroutineScope()
    val source = remember { service.sourceText() }
    var provider by remember { mutableStateOf(app.prefs.aiProvider) }
    var action by remember { mutableStateOf<AiAction>(AiAction.Correct) }
    var variants by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun reset() {
        job?.cancel()
        variants = emptyList()
        error = null
        busy = false
    }

    Column(Modifier.fillMaxWidth().height(height).background(ImeColors.board)) {
        PanelHeader("AI Writing", onClose = { reset(); service.togglePanel(Panel.AI) }) {
            AiProvider.entries.forEach { p ->
                Chip(p.label, p == provider) { provider = p; app.prefs.aiProvider = p; reset() }
            }
        }

        when {
            state.sensitiveField -> Notice("AI недоступен в полях пароля и других защищённых полях.")
            source == null -> Notice("Напишите сообщение или выделите текст, затем откройте AI Writing.")
            !app.ai.isConfigured(provider) -> {
                Notice(
                    "Для ${provider.label} нужен API-ключ (подписка на приложение его не заменяет). " +
                        "Добавьте ключ в настройках ROB Keyboard → AI. Ключ хранится зашифрованным только на этом устройстве.",
                )
                Row(Modifier.padding(8.dp)) { SmallButton("Открыть настройки", primary = true) { service.openSettings() } }
            }
            else -> {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
                    listOf(AiAction.Correct, AiAction.Suggest, AiAction.Continue, AiAction.Rewrite).forEach { a ->
                        Chip(a.label.removePrefix("AI "), action == a) { action = a; reset() }
                    }
                    Tone.entries.forEach { t ->
                        val a = AiAction.ChangeTone(t)
                        Chip(t.label, action == a) { action = a; reset() }
                    }
                    Language.entries.forEach { l ->
                        val a = AiAction.Translate(l)
                        Chip("→ ${l.code.uppercase()}", action == a) { action = a; reset() }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                    if (variants.isEmpty()) {
                        Text(
                            "${action.label}. Будет отправлено в ${provider.label} " +
                                (if (source.isSelection) "(выделенный текст):" else "(весь текст поля):"),
                            color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            source.text, color = ImeColors.text, fontSize = 15.sp, maxLines = 8, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .border(1.dp, ImeColors.cyanDim, RoundedCornerShape(8.dp)).padding(8.dp),
                        )
                        if (busy) CircularProgressIndicator(color = ImeColors.cyan, modifier = Modifier.padding(8.dp))
                        error?.let { Text(it, color = ImeColors.danger, fontSize = 13.sp) }
                    } else {
                        Text("Выберите вариант. Исходный текст пока не изменён.", color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        variants.forEachIndexed { i, v ->
                            Text(
                                v, color = ImeColors.text, fontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                                    .border(if (i == selected) 2.dp else 1.dp, if (i == selected) ImeColors.cyan else ImeColors.cyanDim, RoundedCornerShape(8.dp))
                                    .clickable { selected = i }.padding(8.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (variants.isEmpty()) {
                        SmallButton("Отправить в ${provider.label}", primary = true, enabled = !busy) {
                            busy = true
                            error = null
                            job = scope.launch {
                                try {
                                    val (system, user) = AiPrompts.build(action, source.text)
                                    variants = AiPrompts.parseVariants(app.ai.complete(provider, system, user))
                                    selected = 0
                                    if (variants.isEmpty()) error = "Пустой ответ"
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = e.message ?: e.toString()
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        if (busy) SmallButton("Стоп") { reset() }
                    } else {
                        SmallButton("Применить", primary = true) {
                            service.applyResult(source, variants[selected], append = action.appends)
                            reset()
                            service.togglePanel(Panel.AI)
                        }
                        SmallButton("Отклонить") { reset() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, color = ImeColors.text, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
}
