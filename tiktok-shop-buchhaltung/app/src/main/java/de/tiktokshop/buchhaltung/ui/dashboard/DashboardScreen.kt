package de.tiktokshop.buchhaltung.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.ui.buchungen.BuchungFilter
import de.tiktokshop.buchhaltung.ui.common.asEuro
import de.tiktokshop.buchhaltung.ui.common.rememberApp

/**
 * Startseite (§9-14 der Vereinfachungs-Vorgabe): großer, nicht abgeschnittener Einnahmen/
 * Ausgaben/Ergebnis-Block statt eines Grids aus acht gleich großen Karten, eigener
 * Auszahlungsstatus-Block (Frozen != Ausgezahlt, nie mit Einnahmen/Ausgaben vermischt), und ein
 * kompaktes Aktionsmenü statt vieler gleich großer Buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onEinnahmeErfassen: () -> Unit,
    onAusgabeErfassen: () -> Unit,
    onBelegePruefen: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onImportEntry: () -> Unit,
    onImportHistory: () -> Unit,
    onBuchungen: (BuchungFilter) -> Unit,
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PeriodSelector(selected = state.period, onSelect = viewModel::selectPeriod)

            ResultBlock(
                einnahmenCents = state.summary.displayedTotalCents,
                ausgabenCents = state.summary.expensesCents,
                ergebnisCents = state.summary.earnedMinusExpensesCents,
                onEinnahmen = { onBuchungen(BuchungFilter.EINNAHMEN) },
                onAusgaben = { onBuchungen(BuchungFilter.AUSGABEN) },
            )

            PayoutStatusBlock(
                earnedCents = state.summary.displayedTotalCents,
                frozenCents = state.summary.frozenCents,
                availableCents = state.summary.availableCents,
                paidOutCents = state.summary.paidOutCents,
                onFrozenClick = { onBuchungen(BuchungFilter.FROZEN) },
                onOtherClick = { onBuchungen(BuchungFilter.ALLE) },
            )

            ActionMenu(
                onImportEntry = onImportEntry,
                onEinnahmeErfassen = onEinnahmeErfassen,
                onAusgabeErfassen = onAusgabeErfassen,
                onBuchungen = { onBuchungen(BuchungFilter.ALLE) },
                onBelegePruefen = onBelegePruefen,
                onImportHistory = onImportHistory,
                onExport = onExport,
                onBackup = onBackup,
            )
        }
    }
}

@Composable
private fun PeriodSelector(selected: PeriodFilter, onSelect: (PeriodFilter) -> Unit) {
    Column {
        Text("Zeitraum")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** Der große, nicht abgeschnittene Einnahmen/Ausgaben/Ergebnis-Block (§9-13). */
@Composable
private fun ResultBlock(
    einnahmenCents: Long,
    ausgabenCents: Long,
    ergebnisCents: Long,
    onEinnahmen: () -> Unit,
    onAusgaben: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigResultRow(label = "EINNAHMEN", valueCents = einnahmenCents, onClick = onEinnahmen)
            BigResultRow(label = "AUSGABEN", valueCents = ausgabenCents, onClick = onAusgaben)
            HorizontalDivider()
            BigResultRow(label = "VORLÄUFIGES ERGEBNIS", valueCents = ergebnisCents, emphasize = true)
            Text(
                "Vorläufiger Arbeitsstand – keine endgültige EÜR",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BigResultRow(label: String, valueCents: Long, emphasize: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            valueCents.asEuro(),
            fontSize = if (emphasize) 28.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

/** Separater Auszahlungsstatus-Block - wird NIE mit Einnahmen/Ausgaben vermischt (§13: Frozen != Ausgezahlt). */
@Composable
private fun PayoutStatusBlock(
    earnedCents: Long,
    frozenCents: Long,
    availableCents: Long,
    paidOutCents: Long,
    onFrozenClick: () -> Unit,
    onOtherClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TikTok-Auszahlungsstatus", style = MaterialTheme.typography.titleSmall)
            PayoutStatusRow("Earned (angezeigt)", earnedCents, onOtherClick)
            PayoutStatusRow("Eingefroren", frozenCents, onFrozenClick)
            PayoutStatusRow("Verfügbar", availableCents, onOtherClick)
            PayoutStatusRow("Ausgezahlt", paidOutCents, onOtherClick)
        }
    }
}

@Composable
private fun PayoutStatusRow(label: String, valueCents: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(valueCents.asEuro(), fontWeight = FontWeight.Medium)
    }
}

/** Kompaktes Aktionsmenü (§14): "+ Importieren"/"+ Manuell" prominent, alles andere als schmale Listenzeilen. */
@Composable
private fun ActionMenu(
    onImportEntry: () -> Unit,
    onEinnahmeErfassen: () -> Unit,
    onAusgabeErfassen: () -> Unit,
    onBuchungen: () -> Unit,
    onBelegePruefen: () -> Unit,
    onImportHistory: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onImportEntry, modifier = Modifier.fillMaxWidth()) {
            Text("+ Importieren")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onEinnahmeErfassen, modifier = Modifier.weight(1f)) { Text("+ Einnahme") }
            OutlinedButton(onClick = onAusgabeErfassen, modifier = Modifier.weight(1f)) { Text("+ Ausgabe") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                CompactMenuRow("Buchungen", onBuchungen)
                HorizontalDivider()
                CompactMenuRow("Belege prüfen", onBelegePruefen)
                HorizontalDivider()
                CompactMenuRow("Import-Verlauf", onImportHistory)
                HorizontalDivider()
                CompactMenuRow("Steuer-Arbeitsstand / Export", onExport)
                HorizontalDivider()
                CompactMenuRow("Backup", onBackup)
            }
        }
    }
}

@Composable
private fun CompactMenuRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label)
    }
}
