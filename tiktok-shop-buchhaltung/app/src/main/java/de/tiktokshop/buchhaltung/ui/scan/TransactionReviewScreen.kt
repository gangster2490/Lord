package de.tiktokshop.buchhaltung.ui.scan

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.model.formatGerman
import de.tiktokshop.buchhaltung.ocr.AmountParser
import de.tiktokshop.buchhaltung.ocr.GermanDateParser

/**
 * "X Transaktionen erkannt" - zeigt alle aus einem Screenshot erkannten Kandidaten zur
 * Bestätigung an. Nichts wird gespeichert, bevor der Nutzer hier explizit "Auswahl
 * speichern" antippt (OCR_RULES.md). Jede hier bestätigte Händler-Zuordnung wird beim
 * Speichern als Regel gelernt (RuleLearningRepository).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(viewModel: ScanViewModel, onSaved: () -> Unit, onCancel: () -> Unit) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.savedCount) {
        if (state.savedCount != null) onSaved()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("${state.candidates.size} Transaktionen erkannt") }) },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = viewModel::saveSelected,
                    enabled = state.selectedCount > 0 && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isSaving) "Speichern…" else "Auswahl speichern (${state.selectedCount})")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.usedAiFallback) {
                Text(
                    "Lokale Erkennung war unsicher - Ergebnis stammt vom AI-Vision-Fallback. Bitte besonders sorgfältig prüfen.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            state.warning?.let { warning ->
                Text(warning, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { viewModel.selectAll(true) }) { Text("Alle auswählen") }
                OutlinedButton(onClick = { viewModel.selectAll(false) }) { Text("Keine") }
                OutlinedButton(onClick = viewModel::reRecognize) { Text("Erneut erkennen") }
                TextButton(onClick = onCancel) { Text("Abbrechen") }
            }

            if (state.candidates.isEmpty() && !state.isLoading) {
                Text(
                    "Keine Transaktionen erkannt. Versuche es mit einem schärferen Screenshot oder erfasse manuell.",
                    modifier = Modifier.padding(16.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.candidates, key = { it.candidate.id }) { item ->
                    CandidateCard(item = item, viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateCard(item: CandidateUiState, viewModel: ScanViewModel) {
    val candidate = item.candidate

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = item.selected, onCheckedChange = { viewModel.toggleSelected(candidate.id) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(candidate.merchant ?: "Unbekannte Quelle")
                    Text("Confidence: ${(candidate.confidence * 100).toInt()}%" + if (candidate.needsReview) " · Bitte prüfen" else "")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = candidate.entryType == EntryType.INCOME,
                    onClick = { viewModel.updateEntryType(candidate.id, EntryType.INCOME) },
                    label = { Text("Einnahme") },
                )
                FilterChip(
                    selected = candidate.entryType == EntryType.EXPENSE,
                    onClick = { viewModel.updateEntryType(candidate.id, EntryType.EXPENSE) },
                    label = { Text("Ausgabe") },
                )
            }

            var dateText by remember(candidate.id) { mutableStateOf(candidate.date?.toString().orEmpty()) }
            OutlinedTextField(
                value = dateText,
                onValueChange = { value ->
                    dateText = value
                    GermanDateParser.parseOrNull(value)?.let { viewModel.updateDate(candidate.id, it) }
                },
                label = { Text("Datum (JJJJ-MM-TT)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = candidate.merchant.orEmpty(),
                onValueChange = { viewModel.updateMerchant(candidate.id, it) },
                label = { Text("Händler / Quelle") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var amountText by remember(candidate.id) {
                    mutableStateOf(candidate.amountCents?.formatGerman().orEmpty())
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        amountText = value
                        val parsed = runCatching { AmountParser.parseSingleAmountToCents(value) }.getOrNull()
                        viewModel.updateAmountCents(candidate.id, parsed)
                    },
                    label = { Text("Betrag") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = candidate.currency,
                    onValueChange = { viewModel.updateCurrency(candidate.id, it) },
                    label = { Text("Währung") },
                    modifier = Modifier.weight(0.6f),
                )
            }

            when (candidate.entryType) {
                EntryType.EXPENSE -> CategoryDropdown(
                    selected = candidate.category,
                    onSelect = { viewModel.updateCategory(candidate.id, it) },
                )
                EntryType.INCOME -> {
                    OutlinedTextField(
                        value = candidate.incomeType.orEmpty(),
                        onValueChange = { viewModel.updateIncomeType(candidate.id, it) },
                        label = { Text("Einnahmeart (Provision/Bonus/Rewards)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StatusDropdown(selected = candidate.status, onSelect = { viewModel.updateStatus(candidate.id, it) })
                }
            }

            Column {
                Text("Geschäftlicher Anteil: ${candidate.businessUsePercent}%")
                Slider(
                    value = candidate.businessUsePercent.toFloat(),
                    onValueChange = { viewModel.updateBusinessUsePercent(candidate.id, it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 19,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(selected: ExpenseCategory?, onSelect: (ExpenseCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: "Bitte wählen",
            onValueChange = {},
            readOnly = true,
            label = { Text("Kategorie") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExpenseCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(selected: IncomeStatus?, onSelect: (IncomeStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: "Bitte wählen",
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
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
