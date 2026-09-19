package de.tiktokshop.buchhaltung.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "Datei / Beleg importieren" (§1): EIN Einstiegspunkt statt separater Buttons pro Format.
 * Bietet Excel/CSV, Foto/Screenshot, Kamera und manuelle Erfassung an - die App erkennt danach
 * selbst, ob es sich um eine Einnahme, Ausgabe, einen TikTok-Report oder einen Beleg handelt.
 * Foto/Screenshot und Kamera führen zum bestehenden Scan-Screen, der beide Aufnahmewege bereits
 * anbietet (kein doppelter Kamera-Code).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportEntryScreen(
    onExcelCsv: () -> Unit,
    onFotoScreenshot: () -> Unit,
    onKamera: () -> Unit,
    onManuellEinnahme: () -> Unit,
    onManuellAusgabe: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Datei / Beleg importieren") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Wie möchtest du erfassen? Die App erkennt automatisch, ob es sich um eine Einnahme oder Ausgabe handelt.")

            Button(onClick = onExcelCsv, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Excel / CSV importieren")
            }
            Button(onClick = onFotoScreenshot, modifier = Modifier.fillMaxWidth()) {
                Text("Foto / Screenshot importieren")
            }
            Button(onClick = onKamera, modifier = Modifier.fillMaxWidth()) {
                Text("Mit Kamera fotografieren")
            }
            Button(onClick = onManuellEinnahme, modifier = Modifier.fillMaxWidth()) {
                Text("Einnahme manuell erfassen")
            }
            Button(onClick = onManuellAusgabe, modifier = Modifier.fillMaxWidth()) {
                Text("Ausgabe manuell erfassen")
            }
        }
    }
}
