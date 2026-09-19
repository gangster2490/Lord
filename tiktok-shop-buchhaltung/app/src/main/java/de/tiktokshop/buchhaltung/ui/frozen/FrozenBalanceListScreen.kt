package de.tiktokshop.buchhaltung.ui.frozen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.ui.common.asEuro
import de.tiktokshop.buchhaltung.ui.common.rememberApp
import java.time.format.DateTimeFormatter

/** Liste der Auszahlungsstatus-Einträge (§ "Eingefrorenen Betrag erfassen"), erreichbar über den Dashboard-Auszahlungsstatus-Block. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrozenBalanceListScreen(initialStatus: FrozenBalanceStatus?, onOpenEntry: (String) -> Unit, onCapture: () -> Unit) {
    val app = rememberApp()
    val viewModel: FrozenBalanceListViewModel = viewModel(
        factory = viewModelFactory { initializer { FrozenBalanceListViewModel(app.repository, initialStatus) } },
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Auszahlungsstatus") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCapture) {
                Icon(Icons.Filled.Add, contentDescription = "Eingefrorenen Betrag erfassen")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    null to "Alle",
                    FrozenBalanceStatus.FROZEN to "Eingefroren",
                    FrozenBalanceStatus.AVAILABLE to "Verfügbar",
                    FrozenBalanceStatus.PAID_OUT to "Ausgezahlt",
                    FrozenBalanceStatus.REVERSED to "Storniert",
                ).forEach { (status, label) ->
                    FilterChip(
                        selected = state.filter == status,
                        onClick = { viewModel.setFilter(status) },
                        label = { Text(label) },
                    )
                }
            }

            Text(
                "Summe: ${state.totalCents.asEuro()}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (state.rows.isEmpty()) {
                Text("Keine Einträge gefunden.")
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    FrozenBalanceRowItem(row = row, onClick = { onOpenEntry(row.id) })
                }
            }
        }
    }
}

private val ROW_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
private fun FrozenBalanceRowItem(row: FrozenBalanceEntry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("${row.date.format(ROW_DATE_FORMAT)}  ·  ${row.platform}")
                Text("${row.status.label}${row.note?.let { " · $it" } ?: ""}")
            }
            Text(row.amountCents.asEuro())
        }
    }
}
