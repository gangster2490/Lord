package de.tiktokshop.buchhaltung.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.ui.common.asEuro
import de.tiktokshop.buchhaltung.ui.common.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onEinnahmeErfassen: () -> Unit,
    onAusgabeErfassen: () -> Unit,
    onBelegePruefen: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
) {
    val app = rememberApp()
    val viewModel: DashboardViewModel = viewModel(
        factory = viewModelFactory { initializer { DashboardViewModel(app.repository) } },
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("TikTok Shop Buchhaltung") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodSelector(selected = state.period, onSelect = viewModel::selectPeriod)

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                val cards = listOf(
                    "Angezeigt" to state.summary.displayedTotalCents.asEuro(),
                    "Eingefroren" to state.summary.frozenCents.asEuro(),
                    "Verfügbar" to state.summary.availableCents.asEuro(),
                    "Ausgezahlt" to state.summary.paidOutCents.asEuro(),
                    "Ausgaben" to state.summary.expensesCents.asEuro(),
                    "Vorläufiges Ergebnis" to state.summary.preliminaryResultCents.asEuro(),
                    "Belege" to state.summary.receiptCount.toString(),
                    "Ungeprüft" to state.summary.missingOrUnconfirmedCount.toString(),
                )
                items(cards) { (label, value) ->
                    DashboardCard(label = label, value = value)
                }
            }

            Button(onClick = onEinnahmeErfassen, modifier = Modifier.fillMaxWidth()) {
                Text("Einnahme erfassen")
            }
            Button(onClick = onAusgabeErfassen, modifier = Modifier.fillMaxWidth()) {
                Text("Ausgabe erfassen")
            }
            Button(onClick = onBelegePruefen, modifier = Modifier.fillMaxWidth()) {
                Text("Belege prüfen")
            }
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("Export")
            }
            Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Backup")
            }
        }
    }
}

@Composable
private fun PeriodSelector(selected: PeriodFilter, onSelect: (PeriodFilter) -> Unit) {
    Column {
        Text("Zeitraum")
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PeriodFilter.MONTH to "Monat",
                PeriodFilter.QUARTER to "Quartal",
                PeriodFilter.YEAR to "Jahr",
            ).forEach { (filter, label) ->
                FilterChip(
                    selected = selected == filter,
                    onClick = { onSelect(filter) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun DashboardCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label)
            Text(value)
        }
    }
}
