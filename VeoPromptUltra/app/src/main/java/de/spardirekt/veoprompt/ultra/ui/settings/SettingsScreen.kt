package de.spardirekt.veoprompt.ultra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.veoprompt.ultra.compliance.TikTokShopPolicy
import de.spardirekt.veoprompt.ultra.config.ModelConfig
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import de.spardirekt.veoprompt.ultra.ui.components.Chip
import de.spardirekt.veoprompt.ultra.ui.components.GradientButton
import de.spardirekt.veoprompt.ultra.ui.components.PearlCard
import de.spardirekt.veoprompt.ultra.ui.components.SecondaryButton
import de.spardirekt.veoprompt.ultra.ui.theme.LocalBottomBarInset
import de.spardirekt.veoprompt.ultra.ui.theme.UltraColors

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apiInput by viewModel.apiInput.collectAsStateWithLifecycle()
    val testMessage by viewModel.testMessage.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val bottom = LocalBottomBarInset.current
    LaunchedEffect(settings) { viewModel.refreshDiagnostics() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(UltraColors.pearl)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Text("Настройки", fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = UltraColors.textOnLight)
        }
        item(key = "api") {
            PearlCard {
                Text("OpenAI API", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (diagnostics.hasApiKey) viewModel.maskedKey() else "Ключ не задан",
                    color = UltraColors.textMuted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiInput,
                    onValueChange = viewModel::setApiInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                GradientButton("Add / Replace", onClick = viewModel::addOrReplaceKey)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Test", onClick = viewModel::testKey)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Remove", onClick = viewModel::removeKey)
                if (testMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(testMessage, fontSize = 13.sp, color = UltraColors.violet)
                }
            }
        }
        item(key = "model") {
            PearlCard {
                Text("Model config", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelConfig.Profile.entries.forEach { profile ->
                        Chip(
                            label = profile.label,
                            selected = settings.model == profile.modelId,
                            onClick = { viewModel.setModel(profile) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(ModelConfig.profile(settings.model).hint, color = UltraColors.textMuted, fontSize = 12.sp)
            }
        }
        item(key = "voice") {
            PearlCard {
                Text("Default Voice", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceLanguage.entries.forEach { v ->
                        Chip(v.name, settings.defaultVoice == v) { viewModel.setVoice(v) }
                    }
                }
            }
        }
        item(key = "creative") {
            PearlCard {
                Text("Default Creative Mode", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                CreativeMode.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { mode ->
                            Chip(mode.uiLabel(), settings.defaultCreative == mode) { viewModel.setCreative(mode) }
                        }
                    }
                }
            }
        }
        item(key = "tiktok") {
            PearlCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TikTok Shop Mode", fontWeight = FontWeight.SemiBold)
                        Text("Добавляет #TikTokShop", color = UltraColors.textMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.tiktokShopMode,
                        onCheckedChange = viewModel::setTiktok,
                        colors = SwitchDefaults.colors(checkedTrackColor = UltraColors.violet)
                    )
                }
            }
        }
        item(key = "data") {
            PearlCard {
                Text("Data", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Удалить все проекты", onClick = viewModel::clearData)
            }
        }
        item(key = "diag") {
            PearlCard {
                Text("Diagnostics", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                DiagLine("app version", diagnostics.appVersion)
                DiagLine("pipeline stage", diagnostics.pipelineStage)
                DiagLine("last safe error", diagnostics.lastSafeError)
                DiagLine("model config", "${diagnostics.modelLabel} · ${diagnostics.modelId}")
                DiagLine("database", diagnostics.databaseStatus)
                DiagLine("compliance policy", diagnostics.compliancePolicy)
            }
        }
        item(key = "about") {
            PearlCard {
                Text("About", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Veo Prompt Ultra", fontWeight = FontWeight.Medium)
                Text("Приватное приложение для точных VEO 3.1 промптов. Видео не генерируется.", color = UltraColors.textMuted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "TikTok Shop Content Quality & Compliance Policy ${TikTokShopPolicy.VERSION}. Аудит хранится отдельно от veoPrompt.",
                    color = UltraColors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DiagLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = UltraColors.textMuted, fontSize = 11.sp)
        Text(value, fontSize = 13.sp)
    }
}
