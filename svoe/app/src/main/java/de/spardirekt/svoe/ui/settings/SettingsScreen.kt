package de.spardirekt.svoe.ui.settings

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
import de.spardirekt.svoe.BuildConfig
import de.spardirekt.svoe.SvoeViewModel
import de.spardirekt.svoe.domain.ThemeMode
import de.spardirekt.svoe.ui.components.ChoiceChip
import de.spardirekt.svoe.ui.components.PrimaryButton
import de.spardirekt.svoe.ui.components.SvoeCard
import de.spardirekt.svoe.ui.components.SvoeField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SvoeViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable(state.prefs.displayName) { mutableStateOf(state.prefs.displayName) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }

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
        SvoeCard {
            Text("Профиль", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            SvoeField(name, { name = it }, "Имя")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Сохранить имя", onClick = { vm.setName(name) })
        }
        Spacer(Modifier.height(12.dp))
        SvoeCard {
            Text("Тема", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Система", state.prefs.theme == ThemeMode.SYSTEM) { vm.setTheme(ThemeMode.SYSTEM) }
                ChoiceChip("Светлая", state.prefs.theme == ThemeMode.LIGHT) { vm.setTheme(ThemeMode.LIGHT) }
                ChoiceChip("Тёмная", state.prefs.theme == ThemeMode.DARK) { vm.setTheme(ThemeMode.DARK) }
            }
        }
        Spacer(Modifier.height(12.dp))
        SvoeCard {
            Text("Валюта", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("€ Евро", state.prefs.currencyCode == "EUR") { vm.setCurrency("EUR") }
                ChoiceChip("₽ Рубль", state.prefs.currencyCode == "RUB") { vm.setCurrency("RUB") }
                ChoiceChip("$ Доллар", state.prefs.currencyCode == "USD") { vm.setCurrency("USD") }
            }
        }
        Spacer(Modifier.height(12.dp))
        SvoeCard {
            Text("Данные", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Всё хранится локально в файле на этом телефоне. Облака нет, аккаунта нет.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Заполнить примерами", onClick = vm::seedExamples)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { confirmWipe = true }) {
                Text("Очистить все записи", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Своё  ·  ${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Очистить всё?") },
            text = { Text("Задачи, привычки, дневник и расходы будут удалены. Имя и тема останутся.") },
            confirmButton = {
                TextButton(onClick = { vm.wipe(); confirmWipe = false }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Отмена") }
            },
        )
    }
}
