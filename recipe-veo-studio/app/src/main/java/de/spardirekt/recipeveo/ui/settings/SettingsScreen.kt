package de.spardirekt.recipeveo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.BuildConfig
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.domain.ThemeMode
import de.spardirekt.recipeveo.domain.VoiceLang
import de.spardirekt.recipeveo.domain.label
import de.spardirekt.recipeveo.ui.components.ChoiceChip
import de.spardirekt.recipeveo.ui.components.PrimaryButton
import de.spardirekt.recipeveo.ui.components.StudioCard
import de.spardirekt.recipeveo.ui.components.StudioField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: StudioViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable(state.prefs.displayName) { mutableStateOf(state.prefs.displayName) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
            }
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))
        StudioCard {
            Text("Студия", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            StudioField(name, { name = it }, "Имя")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Сохранить имя", onClick = { vm.setName(name) })
        }
        Spacer(Modifier.height(12.dp))
        StudioCard {
            Text("Тема", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Система", state.prefs.theme == ThemeMode.SYSTEM) { vm.setTheme(ThemeMode.SYSTEM) }
                ChoiceChip("Светлая", state.prefs.theme == ThemeMode.LIGHT) { vm.setTheme(ThemeMode.LIGHT) }
                ChoiceChip("Тёмная", state.prefs.theme == ThemeMode.DARK) { vm.setTheme(ThemeMode.DARK) }
            }
        }
        Spacer(Modifier.height(12.dp))
        StudioCard {
            Text("Голос новых проектов", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceLang.entries.forEach {
                    ChoiceChip(it.label(), state.prefs.defaultVoice == it) { vm.setDefaultVoice(it) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StudioCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TikTok Shop по умолчанию", style = MaterialTheme.typography.titleMedium)
                    Text("Для новых черновиков", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.prefs.tiktokShop, onCheckedChange = vm::setDefaultTiktok)
            }
        }
        Spacer(Modifier.height(12.dp))
        StudioCard {
            Text("Библиотека", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Три демо-рецепта можно вернуть. Очистка не трогает имя и тему.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Вернуть демо", onClick = vm::restoreDemo)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmClear = true }) {
                Text("Очистить студию", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Recipe VEO Studio  ·  ${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить студию?") },
            text = { Text("Все проекты и промпты будут удалены с этого телефона.") },
            confirmButton = {
                TextButton(onClick = { vm.clearLibrary(); confirmClear = false }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            },
        )
    }
}
