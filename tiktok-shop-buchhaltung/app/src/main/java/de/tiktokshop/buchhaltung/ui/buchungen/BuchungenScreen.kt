package de.tiktokshop.buchhaltung.ui.buchungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import de.tiktokshop.buchhaltung.ui.common.asEuro
import de.tiktokshop.buchhaltung.ui.common.rememberApp
import java.time.format.DateTimeFormatter

/** "Buchungen" (§15): durchsuchbare, gefilterte Liste aller Einnahmen/Ausgaben. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuchungenScreen(initialFilter: BuchungFilter, onOpenIncome: (String) -> Unit, onOpenExpense: (String) -> Unit) {
    val app = rememberApp()
    val viewModel: BuchungenViewModel = viewModel(
        factory = viewModelFactory { initializer { BuchungenViewModel(app.repository, initialFilter) } },
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Buchungen") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Suche (Händler, Zahlungspartner, Kategorie)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    BuchungFilter.ALLE to "Alle",
                    BuchungFilter.EINNAHMEN to "Einnahmen",
                    BuchungFilter.AUSGABEN to "Ausgaben",
                    BuchungFilter.FROZEN to "Frozen",
                ).forEach { (filter, label) ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(label) },
                    )
                }
            }

            if (state.rows.isEmpty()) {
                Text("Keine Buchungen gefunden.")
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    BuchungRowItem(
                        row = row,
                        onClick = {
                            if (row.type == de.tiktokshop.buchhaltung.data.model.EntryType.INCOME) {
                                onOpenIncome(row.id)
                            } else {
                                onOpenExpense(row.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

private val ROW_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
private fun BuchungRowItem(row: BuchungRow, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("${row.date.format(ROW_DATE_FORMAT)}  ·  ${row.description}")
                Text(row.category)
            }
            Text(row.amountCents.asEuro())
        }
    }
}
