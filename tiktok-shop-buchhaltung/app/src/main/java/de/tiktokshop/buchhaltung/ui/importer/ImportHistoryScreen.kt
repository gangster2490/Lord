package de.tiktokshop.buchhaltung.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.data.model.formatGerman
import de.tiktokshop.buchhaltung.ui.common.rememberApp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHistoryScreen() {
    val app = rememberApp()
    val viewModel: ImportHistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { ImportHistoryViewModel(app.importRepository) } },
    )
    val batches by viewModel.batches.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Import-Verlauf") }) }) { padding ->
        if (batches.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Noch keine Excel-Importe.")
            }
            return@Scaffold
        }

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(batches, key = { it.id }) { batch ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val typeLabel = when {
                            batch.totalIncomeCents > 0 && batch.totalExpenseCents > 0 -> "Einnahmen + Ausgaben"
                            batch.totalIncomeCents > 0 -> "Einnahmen"
                            batch.totalExpenseCents > 0 -> "Ausgaben"
                            else -> "Import"
                        }
                        Text("${batch.filename}  ·  $typeLabel")
                        Text("Zeitraum: ${batch.month ?: "unbekannt"}")
                        Text("Importiert am: ${batch.importDate.atZone(ZoneId.systemDefault()).format(formatter)}")
                        Text("${batch.newTransactionCount} neu, ${batch.duplicateCount} bereits vorhanden (${batch.rowCount} gesamt)")
                        if (batch.totalIncomeCents > 0) {
                            Text("Gesamteinnahmen (neu): ${batch.totalIncomeCents.formatGerman()} €")
                        }
                        if (batch.totalExpenseCents > 0) {
                            Text("Gesamtausgaben (neu): ${batch.totalExpenseCents.formatGerman()} €")
                        }
                    }
                }
            }
        }
    }
}
