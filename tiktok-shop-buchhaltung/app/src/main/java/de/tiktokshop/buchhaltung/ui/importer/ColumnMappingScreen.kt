package de.tiktokshop.buchhaltung.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tiktokshop.buchhaltung.data.importer.ColumnRole
import de.tiktokshop.buchhaltung.data.importer.SheetAnalysis

private val ROLE_LABELS = mapOf(
    ColumnRole.DATE to "Datum",
    ColumnRole.AMOUNT_INCOME to "Betrag (Einnahme)",
    ColumnRole.AMOUNT_EXPENSE to "Betrag (Ausgabe)",
    ColumnRole.AMOUNT_GENERIC to "Betrag",
    ColumnRole.MERCHANT to "Händler / Zahlungspartner",
    ColumnRole.CATEGORY to "Kategorie",
    ColumnRole.CURRENCY to "Währung",
    ColumnRole.TRANSACTION_ID to "Transaction ID",
    ColumnRole.BUSINESS_PERCENT to "Geschäftlich %",
    ColumnRole.STATUS to "Status",
    ColumnRole.SOURCE to "Quelle",
    ColumnRole.NOTE to "Notiz",
)

/**
 * "Spalten zuordnen" (§4): wird angezeigt, wenn die automatische Kopfzeilen-Erkennung nicht
 * sicher genug war (fehlt z. B. das Datum). Zeigt pro Rolle ein Dropdown mit den tatsächlichen
 * Spaltenüberschriften der Datei; "Vorschau" wendet die Zuordnung an und zeigt danach den
 * Import-Preview-Screen.
 */
@Composable
fun ColumnMappingScreen(
    sheet: SheetAnalysis,
    onPreview: (Map<ColumnRole, Int>) -> Unit,
    onCancel: () -> Unit,
) {
    var mapping by remember(sheet.sheetName) { mutableStateOf(sheet.columnMapping) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Spalten zuordnen", style = MaterialTheme.typography.titleLarge)
        Text(
            "Die Spalten in „${sheet.sheetName}“ konnten nicht eindeutig erkannt werden. " +
                "Bitte ordne mindestens Datum und Betrag manuell zu.",
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(ColumnRole.entries.toList()) { role ->
                RoleDropdown(
                    role = role,
                    headers = sheet.headers,
                    selectedIndex = mapping[role],
                    onSelect = { index ->
                        mapping = if (index == null) mapping - role else mapping + (role to index)
                    },
                )
            }
        }

        val canPreview = mapping.containsKey(ColumnRole.DATE) &&
            (mapping.containsKey(ColumnRole.AMOUNT_INCOME) || mapping.containsKey(ColumnRole.AMOUNT_EXPENSE) || mapping.containsKey(ColumnRole.AMOUNT_GENERIC))

        Button(onClick = { onPreview(mapping) }, enabled = canPreview, modifier = Modifier.fillMaxWidth()) {
            Text("Vorschau")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
    }
}

@Composable
private fun RoleDropdown(
    role: ColumnRole,
    headers: List<String>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = selectedIndex?.let { idx ->
        headers.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "Spalte ${idx + 1}"
    } ?: "– keine –"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(ROLE_LABELS.getValue(role))
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(displayValue)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("– keine –") }, onClick = { onSelect(null); expanded = false })
                    headers.forEachIndexed { index, header ->
                        DropdownMenuItem(
                            text = { Text(header.ifBlank { "Spalte ${index + 1}" }) },
                            onClick = { onSelect(index); expanded = false },
                        )
                    }
                }
            }
        }
    }
}
