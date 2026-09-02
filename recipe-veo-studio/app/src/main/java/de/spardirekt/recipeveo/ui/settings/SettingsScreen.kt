package de.spardirekt.recipeveo.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.spardirekt.recipeveo.BuildConfig
import de.spardirekt.recipeveo.RecipeVeoApp
import de.spardirekt.recipeveo.model.AppMode
import de.spardirekt.recipeveo.model.VoiceLanguage
import de.spardirekt.recipeveo.network.OpenAiModelCatalog
import de.spardirekt.recipeveo.ui.components.ApiKeyDialog
import de.spardirekt.recipeveo.ui.components.AppHeader
import de.spardirekt.recipeveo.ui.components.ConfirmDestructiveDialog
import de.spardirekt.recipeveo.ui.components.DestructiveButton
import de.spardirekt.recipeveo.ui.components.GradientHeading
import de.spardirekt.recipeveo.ui.components.GradientPillButton
import de.spardirekt.recipeveo.ui.components.NavyCard
import de.spardirekt.recipeveo.ui.components.RadioOptionRow
import de.spardirekt.recipeveo.ui.components.SegmentedControl
import de.spardirekt.recipeveo.ui.components.SettingSwitchRow
import de.spardirekt.recipeveo.ui.components.StatusPill
import de.spardirekt.recipeveo.ui.components.VppTags
import de.spardirekt.recipeveo.ui.theme.LocalBottomBarInset
import de.spardirekt.recipeveo.ui.theme.LocalVppType
import de.spardirekt.recipeveo.ui.theme.VppColors
import de.spardirekt.recipeveo.ui.theme.VppDimens
import de.spardirekt.recipeveo.ui.theme.VppLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hasKey: Boolean = false,
    val testMessage: String = "",
    val showReplaceDialog: Boolean = false,
    val keyInput: String = "",
    val busy: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val apiKeys = RecipeVeoApp.instance.apiKeyStore
    private val settingsStore = RecipeVeoApp.instance.settingsStore
    private val repo = RecipeVeoApp.instance.projectRepository
    private val openAi = RecipeVeoApp.instance.openAiClient

    val settings = settingsStore.settings
    private val _ui = MutableStateFlow(SettingsUiState(hasKey = apiKeys.hasKey()))
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    fun refreshKeyStatus() {
        _ui.update { it.copy(hasKey = apiKeys.hasKey()) }
    }

    fun openReplace() {
        _ui.update { it.copy(showReplaceDialog = true, keyInput = "") }
    }

    fun dismissReplace() {
        _ui.update { it.copy(showReplaceDialog = false) }
    }

    fun setKeyInput(v: String) {
        _ui.update { it.copy(keyInput = v) }
    }

    fun saveKey() {
        val key = _ui.value.keyInput.trim()
        if (key.isBlank()) return
        apiKeys.saveKey(key)
        _ui.update { it.copy(showReplaceDialog = false, hasKey = true, testMessage = "API ключ сохранён") }
    }

    fun removeKey() {
        apiKeys.removeKey()
        _ui.update { it.copy(hasKey = false, testMessage = "API ключ удалён") }
    }

    fun testConnection() {
        viewModelScope.launch {
            val key = apiKeys.getKey()
            if (key.isNullOrBlank()) {
                _ui.update { it.copy(testMessage = "Сначала добавьте API ключ") }
                return@launch
            }
            _ui.update { it.copy(busy = true, testMessage = "Проверка…") }
            val result = openAi.testConnection(key)
            _ui.update {
                it.copy(
                    busy = false,
                    testMessage = result.fold(
                        onSuccess = { msg -> msg },
                        onFailure = { err ->
                            (err as? de.spardirekt.recipeveo.diagnostics.AppError)?.userMessage
                                ?: "Ошибка проверки"
                        }
                    )
                )
            }
        }
    }

    fun setVoice(v: VoiceLanguage) = viewModelScope.launch { settingsStore.setVoice(v) }
    fun setMode(m: AppMode) = viewModelScope.launch { settingsStore.setMode(m) }
    fun setTiktok(on: Boolean) = viewModelScope.launch { settingsStore.setTiktok(on) }
    fun setHistoryFormat(fmt: String) = viewModelScope.launch { settingsStore.setHistoryFormat(fmt) }
    fun setModel(model: String) = viewModelScope.launch { settingsStore.setModel(model) }
    fun setDebug(on: Boolean) = viewModelScope.launch { settingsStore.setDebugLogs(on) }
    fun setOutputLanguage(lang: String) = viewModelScope.launch { settingsStore.setOutputLanguage(lang) }
    fun clearHistory() = viewModelScope.launch {
        de.spardirekt.recipeveo.storage.ImageStore.deleteAll(getApplication())
        repo.clearAll()
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenCreate: () -> Unit
) {
    val sett by viewModel.settings.collectAsStateWithLifecycle(
        initialValue = de.spardirekt.recipeveo.storage.SettingsStore.AppSettings()
    )
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val type = LocalVppType.current
    var confirming by remember { mutableStateOf(DestructiveAction.None) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VppColors.backgroundLight, VppColors.backgroundGlow.copy(alpha = 0.45f), VppColors.backgroundLight)
                )
            )
            .testTag(VppTags.SETTINGS_SCREEN)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = VppDimens.screenPadding)
                .padding(
                    top = VppLayout.screenTopPadding,
                    bottom = LocalBottomBarInset.current + VppLayout.floatingContentGap
                )
        ) {
            AppHeader(onNewProject = onOpenCreate)
            Spacer(Modifier.height(18.dp))
            GradientHeading("Настройки")
            Spacer(Modifier.height(18.dp))

            Text("OpenAI API", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("OpenAI API", style = type.cardTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(10.dp))
                StatusPill(
                    text = if (ui.hasKey) "API ключ настроен ✓" else "API ключ не задан",
                    success = ui.hasKey
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (ui.hasKey) "API ключ сохранён" else "Добавьте ключ для генерации",
                    style = type.secondary.copy(color = VppColors.textMuted)
                )
                if (ui.testMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(ui.testMessage, style = type.secondary.copy(color = VppColors.textLight))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientPillButton(
                        text = if (ui.hasKey) "Заменить" else "Добавить",
                        onClick = viewModel::openReplace,
                        modifier = Modifier.weight(1f)
                    )
                    GradientPillButton(
                        text = "Проверить",
                        onClick = viewModel::testConnection,
                        enabled = !ui.busy,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (ui.hasKey) {
                    Spacer(Modifier.height(10.dp))
                    DestructiveButton(
                        text = "Удалить API ключ",
                        onClick = { confirming = DestructiveAction.RemoveKey },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Основные", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("Язык вывода", style = type.secondary.copy(color = VppColors.textMuted))
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("RU", "DE"),
                    selected = sett.outputLanguage,
                    onSelect = viewModel::setOutputLanguage
                )
                Spacer(Modifier.height(14.dp))
                Text("Режим генерации по умолчанию", style = type.secondary.copy(color = VppColors.textMuted))
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("Simple", "Advanced"),
                    selected = sett.defaultMode.name,
                    onSelect = { viewModel.setMode(AppMode.valueOf(it)) }
                )
                Spacer(Modifier.height(14.dp))
                Text("Озвучка по умолчанию", style = type.secondary.copy(color = VppColors.textMuted))
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("DE", "RU", "OFF"),
                    selected = sett.defaultVoice.name,
                    onSelect = { viewModel.setVoice(VoiceLanguage.valueOf(it)) }
                )
                Spacer(Modifier.height(14.dp))
                SettingSwitchRow(
                    label = "TikTok Shop Mode",
                    checked = sett.tiktokShopMode,
                    onCheckedChange = viewModel::setTiktok
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("Данные", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("Формат истории", style = type.secondary.copy(color = VppColors.textMuted))
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("full", "compact"),
                    selected = sett.historyFormat,
                    onSelect = viewModel::setHistoryFormat
                )
                Spacer(Modifier.height(14.dp))
                DestructiveButton(
                    text = "Очистить историю",
                    onClick = { confirming = DestructiveAction.ClearHistory },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("Диагностика", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("OpenAI Model", style = type.cardTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Только GPT-5.6: Sol, Terra или Luna. Старые GPT-4 модели больше не выбираются.",
                    style = type.secondary.copy(color = VppColors.textMuted, fontSize = 12.sp)
                )
                Spacer(Modifier.height(12.dp))
                OpenAiModelCatalog.options.forEach { option ->
                    val selected = sett.model == option.id
                    RadioOptionRow(
                        selected = selected,
                        onSelect = { viewModel.setModel(option.id) },
                        modifier = Modifier.padding(bottom = 8.dp),
                        trailing = {
                            when {
                                selected -> StatusPill("Выбран ✓", success = true)
                                option.recommended -> Text(
                                    "рекомендуется",
                                    style = type.secondary.copy(
                                        color = VppColors.accentPurple,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    ) {
                        Text(
                            option.label,
                            style = type.body.copy(
                                color = VppColors.textLight,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                        Text(
                            option.id,
                            style = type.secondary.copy(color = VppColors.textMuted, fontSize = 11.sp),
                            maxLines = 1
                        )
                        Text(
                            option.hint,
                            style = type.secondary.copy(color = VppColors.textMuted, fontSize = 11.sp),
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                SettingSwitchRow(
                    label = "Debug logs",
                    checked = sett.debugLogs,
                    onCheckedChange = viewModel::setDebug
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("О приложении", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = type.cardTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Private VEO 3.1 prompt generator · 3.2.2",
                    style = type.secondary.copy(color = VppColors.textMuted)
                )
            }
        }
    }

    if (ui.showReplaceDialog) {
        ApiKeyDialog(
            value = ui.keyInput,
            onValueChange = viewModel::setKeyInput,
            onSave = viewModel::saveKey,
            onCancel = viewModel::dismissReplace
        )
    }

    when (confirming) {
        DestructiveAction.None -> Unit
        DestructiveAction.RemoveKey -> ConfirmDestructiveDialog(
            title = "Удалить API ключ?",
            message = "Без ключа генерация не запустится. Ключ придётся ввести заново.",
            confirmLabel = "Удалить",
            onConfirm = viewModel::removeKey,
            onDismiss = { confirming = DestructiveAction.None }
        )
        DestructiveAction.ClearHistory -> ConfirmDestructiveDialog(
            title = "Очистить историю?",
            message = "Все проекты, готовые промпты и загруженные фото будут удалены безвозвратно.",
            confirmLabel = "Очистить",
            onConfirm = viewModel::clearHistory,
            onDismiss = { confirming = DestructiveAction.None }
        )
    }
}

private enum class DestructiveAction { None, RemoveKey, ClearHistory }
