package de.tiktokshop.buchhaltung.ui.frozen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import de.tiktokshop.buchhaltung.ocr.GermanDateParser
import de.tiktokshop.buchhaltung.ui.common.asEuro
import de.tiktokshop.buchhaltung.ui.common.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrozenBalanceDetailScreen(entryId: String, onDeleted: () -> Unit, onBack: () -> Unit) {
    val app = rememberApp()
    val viewModel: FrozenBalanceDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FrozenBalanceDetailViewModel(app.repository, app.receiptStorage, entryId) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::attachScreenshot) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auszahlungsstatus-Eintrag") },
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
            Text("Dieser Betrag ist bereits Teil deiner Einnahmen (Earned) - eine Statusänderung erzeugt keine zusätzliche Einnahme.")

            var dateText by remember(entry.id) { mutableStateOf(entry.date.toString()) }
            OutlinedTextField(
                value = dateText,
                onValueChange = { value ->
                    dateText = value
                    GermanDateParser.parseOrNull(value)?.let(viewModel::updateDate)
                },
                label = { Text("Datum (JJJJ-MM-TT)") },
                modifier = Modifier.fillMaxWidth(),
            )

            var amountText by remember(entry.id) { mutableStateOf(entry.amountCents.asEuro()) }
            OutlinedTextField(
                value = amountText,
                onValueChange = { value ->
                    amountText = value
                    viewModel.updateAmountText(value)
                },
                label = { Text("Betrag") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = entry.currency,
                onValueChange = viewModel::updateCurrency,
                label = { Text("Währung") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = entry.platform,
                onValueChange = viewModel::updatePlatform,
                label = { Text("Plattform") },
                modifier = Modifier.fillMaxWidth(),
            )

            FrozenStatusDropdown(selected = entry.status, onSelect = viewModel::updateStatus)

            OutlinedTextField(
                value = entry.note.orEmpty(),
                onValueChange = viewModel::updateNote,
                label = { Text("Notiz") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = entry.periodReference.orEmpty(),
                onValueChange = viewModel::updatePeriodReference,
                label = { Text("Bezug zu Zeitraum / Import (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            entry.receiptUris.firstOrNull()?.let { path ->
                Text("Screenshot:")
                AsyncImage(model = path, contentDescription = "Screenshot", modifier = Modifier.height(220.dp))
            }
            Button(onClick = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Screenshot anhängen") }

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
            text = { Text("Diese Aktion kann nicht rückgängig gemacht werden. Die zugrunde liegende Einnahme bleibt unverändert erhalten.") },
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
