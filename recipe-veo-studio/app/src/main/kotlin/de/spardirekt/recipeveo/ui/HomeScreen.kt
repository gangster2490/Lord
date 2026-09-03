package de.spardirekt.recipeveo.ui

import androidx.compose.foundation.clickable
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
            OutlinedButton(onClick = onSettings) { Text("Ключ") }
        }
        Text(
            if (hasKey) "Название блюда → OpenAI пишет рецепт и промпт Veo на 8 секунд."
            else "Сначала вставьте ключ OpenAI — без него агент не запускается.",
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
            keyboardActions = KeyboardActions(onDone = { if (hasKey) onCreate() else onSettings() }),
        )
        Button(
            onClick = onCreate,
            enabled = !working && hasKey && StudioRules.canCreate(dish),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Создать")
        }
        if (!hasKey) {
            Text(
                "Нажмите «Ключ», вставьте sk-… и сохраните.",
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable(onClick = onSettings),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (working) {
            CircularProgressIndicator(Modifier.padding(top = 28.dp))
            Text("OpenAI пишет рецепт и промпт…", modifier = Modifier.padding(top = 12.dp))
        }
    }
}
