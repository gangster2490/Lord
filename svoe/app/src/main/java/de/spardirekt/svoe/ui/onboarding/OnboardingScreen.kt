package de.spardirekt.svoe.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.spardirekt.svoe.ui.components.ChoiceChip
import de.spardirekt.svoe.ui.components.PrimaryButton
import de.spardirekt.svoe.ui.components.SvoeField

@Composable
fun OnboardingScreen(onDone: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("EUR") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Своё", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Личное пространство на телефоне: задачи, привычки, дневник и расходы. Всё остаётся только у вас.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        SvoeField(value = name, onValueChange = { name = it }, label = "Как к вам обращаться")
        Spacer(Modifier.height(20.dp))
        Text("Валюта", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ChoiceChip("€ Евро", currency == "EUR") { currency = "EUR" }
            ChoiceChip("₽ Рубль", currency == "RUB") { currency = "RUB" }
            ChoiceChip("$ Доллар", currency == "USD") { currency = "USD" }
        }
        Spacer(Modifier.height(36.dp))
        PrimaryButton("Начать", onClick = { onDone(name, currency) })
        Spacer(Modifier.height(12.dp))
        Text(
            "Имя можно не указывать — и всегда поменять в настройках.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
