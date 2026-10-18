package com.rob.aikeyboard.ui.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.data.TranslateEngine
import com.rob.aikeyboard.ime.InternalField
import com.rob.aikeyboard.ime.Panel
import com.rob.aikeyboard.ime.RobKeyboardService
import com.rob.aikeyboard.text.Language
import com.rob.aikeyboard.translate.MissingModelsException
import com.rob.aikeyboard.translate.Translate
import kotlinx.coroutines.launch

@Composable
fun TranslatePanel(service: RobKeyboardService, height: Dp) {
    val app = RobApp.instance
    val state = service.state
    val scope = rememberCoroutineScope()
    val source = remember { service.sourceText() }
    var text by remember { mutableStateOf(source?.text.orEmpty()) }
    val detected = remember(text) { Translate.detect(text, state.language) }
    var chosenTarget by remember { mutableStateOf(defaultTarget(detected)) }
    val target = if (chosenTarget == detected) defaultTarget(detected) else chosenTarget
    var engine by remember { mutableStateOf(app.prefs.translateEngine) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var missing by remember { mutableStateOf<Set<Language>>(emptySet()) }
    val editing = state.internalField

    if (editing != null) {
        Column(Modifier.fillMaxWidth().background(ImeColors.board)) {
            Row(Modifier.fillMaxWidth().padding(8.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                InternalFieldBox(editing.text, editing.hint, Modifier.weight(1f))
                SmallButton("Готово", primary = true) {
                    text = editing.text
                    result = null
                    state.internalField = null
                }
            }
            KeyboardHost(service)
        }
        return
    }

    fun translate() {
        if (text.isBlank()) return
        busy = true
        error = null
        result = null
        missing = emptySet()
        scope.launch {
            try {
                result = Translate.run(text, detected, target, engine)
            } catch (e: MissingModelsException) {
                missing = e.languages
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().height(height).background(ImeColors.board)) {
        PanelHeader("Перевод", onClose = { service.togglePanel(Panel.TRANSLATE) })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${detected.displayName} →", color = ImeColors.textDim, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
            Language.entries.filter { it != detected }.forEach { l ->
                Chip(l.displayName, l == target) { chosenTarget = l; result = null }
            }
            Text("  ", color = ImeColors.textDim)
            TranslateEngine.entries.forEach { e ->
                Chip(if (e == TranslateEngine.OFFLINE) "Офлайн" else "AI (${app.prefs.aiProvider.label})", e == engine) {
                    engine = e
                    app.prefs.translateEngine = e
                    result = null
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            Text(
                when {
                    source == null && text.isBlank() -> "Поле пустое — нажмите «Ввести текст»."
                    source?.isSelection == true -> "Выделенный текст:"
                    else -> "Текст из поля ввода:"
                },
                color = ImeColors.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text, color = ImeColors.text, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(vertical = 4.dp),
            )
            if (engine == TranslateEngine.AI && !state.sensitiveField) {
                Text(
                    "При облачном переводе этот текст будет отправлен в ${app.prefs.aiProvider.label}.",
                    color = ImeColors.textDim, fontSize = 11.sp,
                )
            }
            if (busy) CircularProgressIndicator(color = ImeColors.cyan, modifier = Modifier.padding(8.dp))
            error?.let { Text(it, color = ImeColors.danger, fontSize = 13.sp) }
            if (missing.isNotEmpty()) {
                Text(
                    "Для офлайн-перевода нужно один раз скачать модели: ${missing.joinToString { it.displayName }} (≈30 МБ каждая)." +
                        if (app.prefs.translateWifiOnly) " Загрузка только по Wi-Fi." else "",
                    color = ImeColors.text, fontSize = 13.sp,
                )
                SmallButton("Скачать модели", primary = true, enabled = !busy) {
                    busy = true
                    scope.launch {
                        try {
                            missing.forEach { app.translator.download(it, app.prefs.translateWifiOnly) }
                            missing = emptySet()
                            busy = false
                            translate()
                        } catch (e: Exception) {
                            error = "Загрузка не удалась: ${e.message}"
                            busy = false
                        }
                    }
                }
            }
            result?.let {
                Text("Перевод (${target.displayName}):", color = ImeColors.cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    it, color = ImeColors.text, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .border(1.dp, ImeColors.cyanDim, RoundedCornerShape(8.dp)).padding(8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallButton(if (text.isBlank()) "Ввести текст" else "Изменить") {
                state.internalField = InternalField("Текст для перевода").also { it.text = text }
            }
            if (result == null) {
                SmallButton("Перевести", primary = true, enabled = text.isNotBlank() && !busy && !(engine == TranslateEngine.AI && state.sensitiveField)) { translate() }
            } else {
                SmallButton("Вставить перевод", primary = true) {
                    service.applyResult(if (text == source?.text) source else null, result!!, append = text != source?.text)
                    service.togglePanel(Panel.TRANSLATE)
                }
                SmallButton("Отмена") { result = null }
            }
        }
    }
}

private fun defaultTarget(source: Language) = when (source) {
    Language.RU -> Language.DE
    Language.DE -> Language.RU
    Language.EN -> Language.RU
}
