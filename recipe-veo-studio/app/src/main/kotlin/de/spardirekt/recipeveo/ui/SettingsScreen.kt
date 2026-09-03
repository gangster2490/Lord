package de.spardirekt.recipeveo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    savedKey: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf(savedKey) }
    var visible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Text("Вставьте OpenAI API key. Без ключа приложение работает в офлайн-режиме.")
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("OpenAI API key") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Text(
                    if (visible) "скрыть" else "показать",
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .let { m ->
                            m.then(
                                Modifier.padding(0.dp)
                            )
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onSave(draft) },
                enabled = draft.startsWith("sk-") && draft.length > 10,
                modifier = Modifier.weight(1f),
            ) { Text("Сохранить") }
            OutlinedButton(
                onClick = { draft = ""; onClear() },
                enabled = savedKey.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Удалить ключ") }
        }
        if (savedKey.isNotBlank()) {
            Text(
                "Ключ сохранён. Агент будет использовать OpenAI.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                "Ключ не задан. Используется встроенный офлайн-агент.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}
