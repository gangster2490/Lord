package de.tiktokshop.buchhaltung.ui.frozen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.ocr.GermanDateParser
import de.tiktokshop.buchhaltung.ui.common.rememberApp

/**
 * "Eingefrorenen Betrag erfassen": separater Auszahlungsstatus-Eintrag, KEINE zusätzliche
 * Einnahme. Für TikTok ist Plattform="TikTok Shop"/Status=FROZEN bereits vorausgewählt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrozenBalanceCaptureScreen(onSaved: () -> Unit, onCancel: () -> Unit) {
    val app = rememberApp()
    val viewModel: FrozenBalanceCaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FrozenBalanceCaptureViewModel(app.repository, app.receiptStorage) }
        },
    )
    val state by viewModel.state.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::onImageSelected) }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Eingefrorenen Betrag erfassen") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Dieser Betrag ist bereits Teil deiner Einnahmen (Earned) und wird hier nur als eingefroren/verfügbar/ausgezahlt markiert - er zählt nicht zusätzlich.")

            var dateText by remember { mutableStateOf(state.date.toString()) }
            OutlinedTextField(
                value = dateText,
                onValueChange = { value ->
                    dateText = value
                    GermanDateParser.parseOrNull(value)?.let(viewModel::updateDate)
                },
                label = { Text("Datum (JJJJ-MM-TT)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::updateAmountText,
                label = { Text("Betrag") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.currency,
                onValueChange = viewModel::updateCurrency,
                label = { Text("Währung") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.platform,
                onValueChange = viewModel::updatePlatform,
                label = { Text("Plattform") },
                modifier = Modifier.fillMaxWidth(),
            )

            FrozenStatusDropdown(selected = state.status, onSelect = viewModel::updateStatus)

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("Notiz") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.periodReference,
                onValueChange = viewModel::updatePeriodReference,
                label = { Text("Bezug zu Zeitraum / Import (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Source / Screenshot")
            Button(onClick = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Screenshot auswählen") }

            state.imageUri?.let { uri ->
                AsyncImage(model = uri, contentDescription = "Screenshot-Vorschau", modifier = Modifier.height(200.dp))
            }

            state.errorMessage?.let { message -> Text(message) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::confirmAndSave) { Text("Speichern") }
                Button(onClick = onCancel) { Text("Abbrechen") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FrozenStatusDropdown(selected: FrozenBalanceStatus, onSelect: (FrozenBalanceStatus) -> Unit) {
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
            FrozenBalanceStatus.entries.forEach { status ->
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
