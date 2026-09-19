package de.tiktokshop.buchhaltung.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.data.model.formatGerman
import de.tiktokshop.buchhaltung.ui.common.rememberApp
import java.time.format.DateTimeFormatter

private val XLSX_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream",
)

/** "TikTok Excel importieren" (§7): eine oder mehrere .xlsx-Dateien gleichzeitig wählbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExcelScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val viewModel: ImportExcelViewModel = viewModel(
        factory = viewModelFactory { initializer { ImportExcelViewModel(app.importRepository) } },
    )
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onFilesSelected(context, uris)
    }

    // Nie automatisch wegnavigieren, solange Fehler sichtbar sein müssen - der Nutzer bestätigt
    // explizit "Fertig", nachdem er das Ergebnis (Erfolg und/oder Fehler) gesehen hat.
    Scaffold(
        topBar = { TopAppBar(title = { Text("TikTok Excel importieren") }) },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.hasAnyImportable) {
                    Button(
                        onClick = viewModel::confirmAll,
                        enabled = !state.isCommitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isCommitting) "Importiere…" else "Import bestätigen")
                    }
                }
                if (state.allDone) {
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Wähle eine oder mehrere TikTok-Shop-Earnings-Report-Dateien (.xlsx). " +
                    "Bereits importierte Transaktionen (gleiche Transaction ID) werden automatisch erkannt und nicht doppelt gespeichert.",
            )

            Button(
                onClick = { filePicker.launch(XLSX_MIME_TYPES) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) {
                Text("Datei(en) auswählen")
            }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.files, key = { it.id }) { file ->
                    FilePreviewCard(file)
                }
            }

            if (state.files.isEmpty()) {
                TextButton(onClick = onCancel) { Text("Abbrechen") }
            }
        }
    }
}

@Composable
private fun FilePreviewCard(file: FileImportState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(file.filename)

            when {
                file.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    Text("Wird gelesen…")
                }
                file.error != null -> {
                    Text("Fehler: ${file.error}")
                }
                file.committed -> {
                    Text("Importiert: ${file.preview?.newTransactionCount ?: 0} neue Transaktionen gespeichert.")
                }
                file.preview != null -> {
                    val preview = file.preview
                    val range = preview.periodRange
                    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                    Text("${preview.newTransactionCount} neue Transaktionen")
                    Text("${preview.duplicateCount} bereits vorhanden")
                    Text("Gesamteinnahmen (neu): ${preview.totalNewIncomeCents.formatGerman()} €")
                    if (range != null) {
                        Text("Zeitraum: ${range.first.format(fmt)} - ${range.second.format(fmt)}")
                    }
                    if (preview.parseErrors.isNotEmpty()) {
                        Text("${preview.parseErrors.size} Zeile(n) übersprungen (fehlende Pflichtfelder):")
                        preview.parseErrors.take(5).forEach { error ->
                            Text("Zeile ${error.rowNumber}: ${error.reason}")
                        }
                    }
                    if (preview.existingSourceDocument != null) {
                        Text("Hinweis: Diese Datei wurde bereits einmal importiert.")
                    }
                }
            }
        }
    }
}
