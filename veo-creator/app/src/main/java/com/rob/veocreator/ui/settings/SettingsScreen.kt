package com.rob.veocreator.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rob.veocreator.ui.theme.VeoCard
import com.rob.veocreator.ui.theme.VeoTextSecondary
import com.rob.veocreator.ui.theme.VeoYellow

@Composable
fun SettingsScreen(
    onKeyChanged: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var inputKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(
            colors = CardDefaults.cardColors(containerColor = VeoCard),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)

                if (state.hasKey) {
                    Text(
                        state.maskedKey.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = VeoTextSecondary
                    )
                }

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    label = { Text(if (state.hasKey) "Enter new key to replace it" else "Paste your Gemini API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveKey(inputKey)
                            inputKey = ""
                            onKeyChanged()
                        },
                        enabled = inputKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = VeoYellow, contentColor = Color.Black)
                    ) { Text("Save") }

                    OutlinedButton(
                        onClick = {
                            viewModel.deleteKey()
                            onKeyChanged()
                        },
                        enabled = state.hasKey
                    ) { Text("Delete") }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.testConnection() }, enabled = state.hasKey) {
                        Text("Test Connection")
                    }
                    when (state.connectionTestState) {
                        ConnectionTestState.TESTING -> CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                        ConnectionTestState.SUCCESS -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        ConnectionTestState.FAILED -> Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        ConnectionTestState.IDLE -> Unit
                    }
                }

                state.connectionMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = VeoTextSecondary)
                }
            }
        }

        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
            context.startActivity(intent)
        }) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.width(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get Gemini API Key")
        }

        Text(
            "Your key is stored only on this device using encrypted storage. It is never sent anywhere " +
                "except Google's official Gemini API.",
            style = MaterialTheme.typography.bodyMedium,
            color = VeoTextSecondary
        )
    }
}
