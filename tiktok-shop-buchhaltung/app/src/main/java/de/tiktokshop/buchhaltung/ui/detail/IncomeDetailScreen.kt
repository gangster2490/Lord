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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.ui.common.asEuro
import de.tiktokshop.buchhaltung.ui.common.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeDetailScreen(incomeId: String, onDeleted: () -> Unit, onBack: () -> Unit) {
    val app = rememberApp()
    val viewModel: IncomeDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { IncomeDetailViewModel(app.repository, incomeId) } },
    )
    val state by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einnahme-Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        val entry = state.entry
        if (entry == null) {
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
            Text("Datum: ${entry.date}")
            Text("Plattform: ${entry.platform}")
            Text("Typ: ${entry.incomeType}")
            Text("Angezeigte Provision: ${entry.amountCents.asEuro()}")

            StatusDropdown(selected = entry.status, onSelect = viewModel::updateStatus)

            entry.payoutDate?.let { Text("Ausgezahlt am: $it") }
            entry.paidOutAmountCents?.let { Text("Tatsächlich ausgezahlt: ${it.asEuro()}") }

            OutlinedTextField(
                value = entry.note.orEmpty(),
                onValueChange = viewModel::updateNote,
                label = { Text("Notiz") },
                modifier = Modifier.fillMaxWidth(),
            )

            entry.receiptUris.firstOrNull()?.let { path ->
                Text("Originalbeleg:")
                AsyncImage(model = path, contentDescription = "Originalbeleg", modifier = Modifier.height(220.dp))
            }

            if (state.history.isNotEmpty()) {
                Text("Statushistorie:")
                state.history.forEach { h ->
                    Text("${h.changedAt} · ${h.oldStatus?.label ?: "—"} → ${h.newStatus.label}")
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(selected: IncomeStatus, onSelect: (IncomeStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IncomeStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.label) },
                    onClick = {
                        onSelect(status)
                        expanded = false
                    },
                )
            }
        }
    }
}
