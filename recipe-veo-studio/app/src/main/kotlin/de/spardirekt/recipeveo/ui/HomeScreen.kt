package de.spardirekt.recipeveo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.spardirekt.recipeveo.domain.StudioRules

@Composable
fun HomeScreen(
    dish: String,
    working: Boolean,
    hasKey: Boolean,
    onDish: (String) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recipe VEO Studio", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = onSettings) { Text("⚙") }
        }
        Text(
            if (hasKey) "Агент: OpenAI · название блюда → рецепт + промпт Veo 8с"
            else "Агент: офлайн · название блюда → рецепт + промпт Veo 8с",
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )
        OutlinedTextField(
            value = dish,
            onValueChange = onDish,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Введите название блюда") },
            placeholder = { Text("Плов, борщ…") },
            singleLine = true,
            enabled = !working,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCreate() }),
        )
        Button(
            onClick = onCreate,
            enabled = !working && StudioRules.canCreate(dish),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Создать")
        }
        if (working) {
            CircularProgressIndicator(Modifier.padding(top = 28.dp))
            Text(
                if (hasKey) "OpenAI пишет рецепт и промпт…" else "Агент пишет рецепт и промпт…",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
