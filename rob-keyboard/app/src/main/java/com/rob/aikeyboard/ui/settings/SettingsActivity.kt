package com.rob.aikeyboard.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rob.aikeyboard.BuildConfig
import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.data.AiProvider
import com.rob.aikeyboard.data.SpaceSwipe
import com.rob.aikeyboard.data.TranslateEngine
import com.rob.aikeyboard.data.Vibration
import com.rob.aikeyboard.emoji.EmojiIntensity
import com.rob.aikeyboard.security.SecureStore
import com.rob.aikeyboard.text.Language
import kotlinx.coroutines.launch

private val Cyan = Color(0xFF2FB8F6)
private val Board = Color(0xFF1E1E20)
private val CardBg = Color(0xFF2A2A2D)

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Cyan, background = Board, surface = CardBg)) {
                SettingsScreen()
            }
        }
    }
}

private fun isEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isSelected(context: Context): Boolean =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(context.packageName + "/") == true

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = RobApp.instance
    val prefs = app.prefs
    // Bumped on every change so all rows re-read their values.
    var tick by remember { mutableIntStateOf(0) }
    fun update(block: () -> Unit) { block(); tick++ }
    var enabled by remember { mutableStateOf(isEnabled(context)) }
    var selected by remember { mutableStateOf(isSelected(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { enabled = isEnabled(context); selected = isSelected(context) }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    androidx.compose.runtime.key(tick) {
    Column(
        Modifier.fillMaxSize().background(Board).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ROB AI Keyboard", color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Версия ${BuildConfig.VERSION_NAME}", color = Color.Gray, fontSize = 12.sp)

            Section("Подключение") {
                StatusLine("1. Клавиатура включена в Android", enabled)
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }) { Text("Открыть настройки клавиатур") }
                StatusLine("2. ROB выбрана как текущая клавиатура", selected)
                Button(onClick = {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                }, enabled = enabled) { Text("Выбрать клавиатуру") }
                var test by remember { mutableStateOf("") }
                OutlinedTextField(test, { test = it }, label = { Text("Поле для проверки") }, modifier = Modifier.fillMaxWidth())
                Hint("Проверка орфографии в других приложениях: Настройки Android → Общее управление → Язык и ввод → Проверка правописания → ROB AI Keyboard.")
            }

            Section("Языки") {
                FlowRow {
                    Language.entries.forEach { l ->
                        val on = l in prefs.enabledLanguages
                        Choice(l.displayName, on) {
                            update {
                                val list = if (on) prefs.enabledLanguages - l else prefs.enabledLanguages + l
                                if (list.isNotEmpty()) prefs.enabledLanguages = Language.entries.filter { it in list }
                            }
                        }
                    }
                }
                Toggle("Автоопределение языка (DE/EN подсказки)", prefs.autoDetectLanguage) { update { prefs.autoDetectLanguage = it } }
                Label("Свайп по пробелу")
                FlowRow { SpaceSwipe.entries.forEach { s -> Choice(s.label, prefs.spaceSwipe == s) { update { prefs.spaceSwipe = s } } } }
                Hint("Долгое нажатие на пробел + движение — перемещение курсора. Свайп влево по ⌫ — удаление слов.")
            }

            Section("Ввод текста") {
                Toggle("Подсказки слов", prefs.showSuggestions) { update { prefs.showSuggestions = it } }
                Toggle("Автокоррекция", prefs.autoCorrect) { update { prefs.autoCorrect = it } }
                Toggle("Заглавная в начале предложения (во всех чатах)", prefs.autoCapitalize) { update { prefs.autoCapitalize = it } }
                Toggle("Автозапятые при наборе (что, но, чтобы, который…)", prefs.autoComma) { update { prefs.autoComma = it } }
                Toggle("Двойной пробел → точка", prefs.doubleSpacePeriod) { update { prefs.doubleSpacePeriod = it } }
                Toggle("Цифры на верхнем ряду (долгое нажатие)", prefs.numberHints) { update { prefs.numberHints = it } }
                PrefSlider("Высота клавиш", prefs.keyHeightScale, 0.75f..1.4f) { v -> update { prefs.keyHeightScale = v } }
                Label("Вибрация")
                FlowRow { Vibration.entries.forEach { v -> Choice(v.label, prefs.vibration == v) { update { prefs.vibration = v } } } }
                Toggle("Звук нажатий", prefs.soundOnKeypress) { update { prefs.soundOnKeypress = it } }
                if (prefs.soundOnKeypress) PrefSlider("Громкость", prefs.soundVolume, 0.05f..1f) { v -> update { prefs.soundVolume = v } }
            }

            Section("Emoji") {
                Toggle("✨ Auto Emoji", prefs.autoEmoji) { update { prefs.autoEmoji = it } }
                Label("Интенсивность")
                FlowRow {
                    listOf(EmojiIntensity.LOW to "Мало", EmojiIntensity.NORMAL to "Нормально", EmojiIntensity.HIGH to "Много").forEach { (i, l) ->
                        Choice(l, prefs.emojiIntensity == i) { update { prefs.emojiIntensity = i } }
                    }
                }
                Hint("Работает локально, без интернета. Backspace сразу после вставки убирает смайлик.")
                OutlinedButton(onClick = { update { app.emojiHistory.clear(); app.saveEmojiHistory() } }) { Text("Очистить недавние Emoji") }
            }

            Section("AI (ChatGPT / Claude)") {
                Hint(
                    "Для AI Writing и облачного перевода нужен собственный API-ключ OpenAI или Anthropic. Подписка ChatGPT Plus / Claude Pro его не заменяет. " +
                        "Ключ хранится зашифрованным (Android Keystore) только на этом устройстве. Текст отправляется только после нажатия «Отправить».",
                )
                Toggle("Кнопка ✎ исправляет через AI (точнее запятые; текст уходит в выбранный AI)", prefs.quickFixWithAi) {
                    update { prefs.quickFixWithAi = it }
                }
                Hint("Без этого ✎ исправляет офлайн и бесплатно: опечатки, заглавные, пробелы, частые запятые, точку в конце.")
                Label("Провайдер по умолчанию")
                FlowRow { AiProvider.entries.forEach { p -> Choice(p.label, prefs.aiProvider == p) { update { prefs.aiProvider = p } } } }
                ApiKeyField("OpenAI API-ключ", SecureStore.OPENAI_KEY) { tick++ }
                ModelField("Модель OpenAI", prefs.openAiModel) { update { prefs.openAiModel = it } }
                ApiKeyField("Anthropic API-ключ", SecureStore.ANTHROPIC_KEY) { tick++ }
                ModelField("Модель Anthropic", prefs.anthropicModel) { update { prefs.anthropicModel = it } }
                ModelField("Адрес API OpenAI (или HTTPS-прокси)", prefs.openAiBaseUrl) { update { prefs.openAiBaseUrl = it } }
                ModelField("Адрес API Anthropic (или HTTPS-прокси)", prefs.anthropicBaseUrl) { update { prefs.anthropicBaseUrl = it } }
            }

            Section("Переводчик") {
                FlowRow { TranslateEngine.entries.forEach { e -> Choice(e.label, prefs.translateEngine == e) { update { prefs.translateEngine = e } } } }
                Toggle("Загружать модели только по Wi-Fi", prefs.translateWifiOnly) { update { prefs.translateWifiOnly = it } }
                var downloaded by remember { mutableStateOf<Set<Language>>(emptySet()) }
                var busy by remember { mutableStateOf(false) }
                LaunchedEffect(tick) { downloaded = app.translator.downloadedLanguages() }
                Language.entries.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${l.displayName}: ${if (l in downloaded) "загружена ✓" else "не загружена"}", color = Color.White, modifier = Modifier.weight(1f))
                        if (l in downloaded) {
                            TextButton(onClick = { scope.launch { app.translator.delete(l); tick++ } }) { Text("Удалить") }
                        } else {
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { app.translator.download(l, prefs.translateWifiOnly) }
                                        .onFailure { Toast.makeText(context, "Ошибка загрузки: ${it.message}", Toast.LENGTH_LONG).show() }
                                    busy = false
                                    tick++
                                }
                            }) { Text(if (busy) "…" else "Скачать (~30 МБ)") }
                        }
                    }
                }
                Hint("Офлайн-перевод выполняется на устройстве (ML Kit), текст никуда не отправляется. Английская модель нужна всегда как промежуточная.")
            }

            Section("Голосовой ввод") {
                Toggle("Предпочитать офлайн-распознавание", prefs.voicePreferOffline) { update { prefs.voicePreferOffline = it } }
                Hint("Используется системный распознаватель речи Android. Офлайн-режим работает, если на устройстве установлены языковые пакеты.")
            }

            Section("Буфер обмена") {
                Toggle("История буфера обмена", prefs.clipboardHistory) { update { prefs.clipboardHistory = it } }
                Hint("10 последних элементов + закреплённые. Записывается только пока ROB — активная клавиатура. Содержимое из полей паролей и помеченное как конфиденциальное не сохраняется.")
                OutlinedButton(onClick = { app.io.execute { app.clipboard.clearAll() }; Toast.makeText(context, "История буфера удалена", Toast.LENGTH_SHORT).show() }) {
                    Text("Удалить всю историю (вкл. закреплённые)")
                }
            }

            Section("Конфиденциальность") {
                Toggle("Учиться на моём вводе (подсказки)", prefs.personalization) { update { prefs.personalization = it } }
                Hint(
                    "Хранится только на устройстве и зашифровано: выученные слова, история буфера, недавние Emoji, API-ключи. " +
                        "Нет журналов нажатий, рекламы и трекеров. В поля паролей и режим «инкогнито» ничего не записывается.",
                )
                OutlinedButton(onClick = { app.io.execute { app.userModel.clear() }; Toast.makeText(context, "Выученные слова удалены", Toast.LENGTH_SHORT).show() }) {
                    Text("Удалить выученные слова")
                }
                Button(onClick = { confirmDelete = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) {
                    Text("Удалить все локальные данные")
                }
            }

            Section("О приложении") {
                Hint(
                    "Словари: hermitdave/FrequencyWords (OpenSubtitles, CC BY-SA 4.0); корпус Лейпцигского университета (CC BY 4.0); " +
                        "регистр немецких слов — igerman98/LibreOffice (GPL). Emoji-данные: Unicode emoji-test и CLDR annotations (Unicode License).",
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить все данные?") },
            text = { Text("Будут удалены выученные слова, буфер обмена, картинки, Emoji-история, API-ключи и модели перевода.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    app.io.execute { app.deleteAllData(includeSettings = false) }
                    tick++
                    Toast.makeText(context, "Все данные удалены", Toast.LENGTH_SHORT).show()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Cyan, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Text((if (ok) "✅ " else "⬜ ") + text, color = Color.White)
}

@Composable
private fun Label(text: String) = Text(text, color = Color.White, fontSize = 14.sp)

@Composable
private fun Hint(text: String) = Text(text, color = Color(0xFFAAAAAA), fontSize = 12.sp)

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Cyan))
    }
}

/** Keeps the value locally while dragging and saves it when the finger lifts. */
@Composable
private fun PrefSlider(label: String, initial: Float, range: ClosedFloatingPointRange<Float>, onSave: (Float) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    Label("$label: ${(value * 100).toInt()}%")
    Slider(value, { value = it }, valueRange = range, onValueChangeFinished = { onSave(value) })
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        modifier = Modifier.padding(end = 6.dp),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan, selectedLabelColor = Color.Black),
    )
}

@Composable
private fun ApiKeyField(label: String, secretName: String, onChanged: () -> Unit) {
    val secure = RobApp.instance.secure
    var stored by remember { mutableStateOf(secure.hasSecret(secretName)) }
    var value by remember { mutableStateOf("") }
    Text("$label: ${if (stored) "сохранён 🔒" else "не задан"}", color = Color.White, fontSize = 14.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value, { value = it.trim() }, label = { Text(if (stored) "Заменить ключ" else "Вставьте ключ") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.weight(1f),
        )
        TextButton(enabled = value.isNotEmpty(), onClick = { secure.putSecret(secretName, value); value = ""; stored = true; onChanged() }) { Text("Сохранить") }
    }
    if (stored) TextButton(onClick = { secure.putSecret(secretName, null); stored = false; onChanged() }) { Text("Удалить ключ") }
}

@Composable
private fun ModelField(label: String, current: String, onSave: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(enabled = value != current, onClick = { onSave(value) }) { Text("OK") }
    }
}
