package de.tiktokshop.buchhaltung.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import de.tiktokshop.buchhaltung.ui.common.asEuro
import de.tiktokshop.buchhaltung.ui.common.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(expenseId: String, onDeleted: () -> Unit, onBack: () -> Unit) {
    val app = rememberApp()
    val viewModel: ExpenseDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { ExpenseDetailViewModel(app.repository, expenseId) } },
    )
    val entry by viewModel.entry.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var businessUsePercent by remember(entry?.id) { mutableStateOf(entry?.businessUsePercent ?: 100) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ausgabe-Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        val current = entry
        if (current == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Eintrag nicht gefunden.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Belegdatum: ${current.date}")
            Text("Händler: ${current.merchant ?: "—"}")
            Text("Kategorie: ${current.category.label}")
            Text("Bruttobetrag: ${current.grossAmountCents.asEuro()}")

            Column {
                Text("Geschäftlicher Anteil: $businessUsePercent%")
                Slider(
                    value = businessUsePercent.toFloat(),
                    onValueChange = { businessUsePercent = it.toInt() },
                    onValueChangeFinished = { viewModel.updateBusinessUsePercent(businessUsePercent) },
                    valueRange = 0f..100f,
                    steps = 19,
                )
            }
            Text("Anrechenbarer Betrag: ${current.businessAmountCents.asEuro()}")

            OutlinedTextField(
                value = current.note.orEmpty(),
                onValueChange = viewModel::updateNote,
                label = { Text("Notiz") },
                modifier = Modifier.fillMaxWidth(),
            )

            current.receiptUris.firstOrNull()?.let { path ->
                Text("Originalbeleg:")
                AsyncImage(model = path, contentDescription = "Originalbeleg", modifier = Modifier.height(220.dp))
            }

            Button(onClick = { showDeleteConfirm = true }) { Text("Löschen") }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eintrag löschen?") },
            text = { Text("Diese Aktion kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                    onDeleted()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") } },
        )
    }
}
