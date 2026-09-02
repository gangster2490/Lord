package de.spardirekt.recipeveo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: StudioViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text("Студия", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(4.dp))
        Text("Всё локально. Ролик собирает Veo, не это приложение.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
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
            Text("Голос по умолчанию", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceLang.entries.forEach {
                    ChoiceChip(it.label(), state.prefs.defaultVoice == it) { vm.setDefaultVoice(it) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StudioCard {
            Text("Библиотека", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Стартовые рецепты можно вернуть. Очистка не трогает тему и голос.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Вернуть примеры", onClick = vm::restoreLibrary)
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
            text = { Text("Все рецепты и промпты будут удалены с этого телефона.") },
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
