package de.tiktokshop.buchhaltung.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.data.importer.ImportCandidateRow
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.formatGerman
import de.tiktokshop.buchhaltung.ui.common.rememberApp
import java.time.format.DateTimeFormatter

private val IMPORT_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    "text/comma-separated-values",
    "application/octet-stream",
    "*/*",
)

/**
 * "Datei / Beleg importieren" -> Excel/CSV (§1-6): EIN Screen für den gesamten Ablauf
 * (Dateiauswahl -> ggf. Spalten zuordnen -> Import prüfen -> Bestätigen), statt separater
 * TikTok-only-Screens. Nie automatisches Speichern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalImportScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val viewModel: UniversalImportViewModel = viewModel(
        factory = viewModelFactory { initializer { UniversalImportViewModel(app.importRepository) } },
    )
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFileSelected(context, it) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Excel / CSV importieren") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (state.step) {
                ImportStep.PICK -> PickContent(onPick = { filePicker.launch(IMPORT_MIME_TYPES) }, onCancel = onCancel)
                ImportStep.LOADING -> LoadingContent(state.filename)
                ImportStep.COLUMN_MAPPING -> {
                    val sheet = state.preview?.sheets?.firstOrNull { !it.isConfident }
                    if (sheet != null) {
                        ColumnMappingScreen(
                            sheet = sheet,
                            onPreview = { mapping -> viewModel.applyColumnMapping(sheet.sheetName, mapping) },
                            onCancel = { viewModel.reset(); onCancel() },
                        )
                    }
                }
                ImportStep.PREVIEW -> {
                    val preview = state.preview
                    if (preview != null) {
                        ImportPreviewContent(
                            preview = preview,
                            excludedRowKeys = state.excludedRowKeys,
                            isCommitting = state.isCommitting,
                            onToggleRow = viewModel::toggleRowExcluded,
                            onConfirm = viewModel::confirmImport,
                            onCancel = { viewModel.reset(); onCancel() },
                        )
                    }
                }
                ImportStep.DONE -> DoneContent(
                    count = state.committedCount,
                    totalCents = state.committedTotalCents,
                    onDone = { viewModel.reset(); onDone() },
                )
                ImportStep.ERROR -> ErrorContent(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.reset() },
                    onCancel = { viewModel.reset(); onCancel() },
                )
            }
        }
    }
}

@Composable
private fun PickContent(onPick: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Wähle eine Excel- (.xlsx) oder CSV-Datei. Die App erkennt automatisch, ob es sich " +
                "um einen TikTok-Earnings-Report, eine Ausgabentabelle oder eine andere Belegliste " +
                "handelt - ein bestimmtes Format oder Sheet-Name ist nicht nötig.",
        )
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Datei auswählen") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
    }
}

@Composable
private fun LoadingContent(filename: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("„${filename.orEmpty()}“ wird analysiert…", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ImportPreviewContent(
    preview: de.tiktokshop.buchhaltung.data.importer.UniversalImportPreview,
    excludedRowKeys: Set<String>,
    isCommitting: Boolean,
    onToggleRow: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var editSelection by remember { mutableStateOf(false) }
    val selectedCount = preview.newRows.size - excludedRowKeys.size

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Import prüfen", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${preview.newIncomeCount} Einnahmen erkannt")
                Text("${preview.newExpenseCount} Ausgaben erkannt")
                Text("Gesamtsumme Einnahmen (neu): ${preview.totalNewIncomeCents.formatGerman()} €")
                Text("Gesamtsumme Ausgaben (neu): ${preview.totalNewExpenseCents.formatGerman()} €")
                Text("${preview.duplicateCount} Duplikate (bereits vorhanden, werden übersprungen)")
                if (preview.parseErrors.isNotEmpty()) {
                    Text("${preview.parseErrors.size} fehlerhafte Zeile(n) (übersprungen)")
                }
                Text("$selectedCount von ${preview.newRows.size} neuen Einträgen ausgewählt")
                if (preview.existingSourceDocument != null) {
                    Text("Hinweis: Diese Datei wurde bereits einmal importiert.")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(preview.newRows.take(200), key = { importRowKey(it) }) { row ->
                ImportRowItem(
                    row = row,
                    showCheckbox = editSelection,
                    isSelected = importRowKey(row) !in excludedRowKeys,
                    onToggle = { onToggleRow(importRowKey(row)) },
                )
            }
        }

        Button(onClick = onConfirm, enabled = !isCommitting && selectedCount > 0, modifier = Modifier.fillMaxWidth()) {
            Text(if (isCommitting) "Importiere…" else "Alle importieren")
        }
        TextButton(onClick = { editSelection = !editSelection }, modifier = Modifier.fillMaxWidth()) {
            Text(if (editSelection) "Auswahl fertig" else "Auswahl bearbeiten")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
    }
}

@Composable
private fun ImportRowItem(row: ImportCandidateRow, showCheckbox: Boolean, isSelected: Boolean, onToggle: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val sign = if (row.type == EntryType.EXPENSE) "-" else "+"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showCheckbox) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${row.date.format(fmt)}  ${row.merchant ?: row.category ?: ""}")
                Text("$sign${row.amountCents.formatGerman()} ${row.currency}")
            }
        }
    }
}

@Composable
private fun DoneContent(count: Int, totalCents: Long, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Import abgeschlossen")
        Text("$count neue Einträge gespeichert (${totalCents.formatGerman()} €).")
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Import fehlgeschlagen")
        Text(message)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Andere Datei wählen") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
    }
}
