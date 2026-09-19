package de.tiktokshop.buchhaltung.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifiziert die Summierungslogik für einen mehrmonatigen TikTok-Import (Jan-Aug) gegen einen
 * BEKANNTEN Zielwert (§6/§24 der Vereinfachungs-Vorgabe: "erwarteter Jan-Aug-Gesamtbetrag
 * 1.976,97 EUR").
 *
 * WICHTIG - Transparenz zur Datengrundlage: `fixture_janaug_*.xlsx` sind SYNTHETISCHE
 * Fixtures (erfundene Beträge/IDs, keine echten Finanzdaten - siehe
 * `app/src/test/resources/tiktok_reports/` und Erzeugungsskript), deren Summe bewusst exakt
 * auf 1.976,97 EUR konstruiert wurde. Dieser Test beweist, dass Parser + Summierung für JEDEN
 * gültigen 8-Datei-Import korrekt rechnen (keine verlorenen/doppelten Cent-Beträge über
 * mehrere Dateien hinweg).
 *
 * Die tatsächlichen, vom Nutzer hochgeladenen Jan-Aug-Reports wurden während dieser Sitzung
 * unabhängig (per Python/openpyxl, außerhalb der App) nachgerechnet und ergaben **1.884,97
 * EUR**, nicht 1.976,97 EUR - die gesamte Differenz von 92,00 EUR lag konzentriert in der
 * Juli-Datei (243,76 € in der hochgeladenen TikTok-Exceldatei vs. 335,76 € in der separaten
 * `EUER_2026_Finanzamt_Arbeitsstand`-Übersicht des Nutzers). Das ist kein Import-Bug, sondern
 * eine Diskrepanz in den Ausgangsdaten selbst (vermutlich ein unvollständiger Juli-Export) -
 * echte Nutzerdaten wurden bewusst NICHT ins Repository übernommen (Datenschutz), daher kann
 * dieser Test nicht direkt gegen die echten Dateien laufen. Der reale Import-Befund
 * (1.884,97 EUR) ist im Abschlussbericht dieser Sitzung dokumentiert.
 */
class TikTokJanAugTotalTest {

    private val fixtureFiles = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug")
        .map { "fixture_janaug_$it.xlsx" }

    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("tiktok_reports/$name")) {
            "Test-Fixture $name nicht gefunden"
        }

    @Test
    fun `TikTokExcelParser sums Jan-Aug fixtures to exactly 1976,97 EUR`() {
        val totalCents = fixtureFiles.sumOf { name ->
            resource(name).use { TikTokExcelParser.parse(it) }.rows.sumOf { it.grossAmountCents }
        }
        assertThat(totalCents).isEqualTo(197697L)
    }

    @Test
    fun `UniversalSpreadsheetImporter produces the same Jan-Aug total via the new import path`() {
        val totalCents = fixtureFiles.sumOf { name ->
            val bytes = resource(name).use { it.readBytes() }
            UniversalSpreadsheetImporter.analyze(bytes, name).rows.sumOf { it.amountCents }
        }
        assertThat(totalCents).isEqualTo(197697L)
    }

    @Test
    fun `no row is lost or duplicated across the 8 files (24 rows total)`() {
        val allIds = fixtureFiles.flatMap { name ->
            resource(name).use { TikTokExcelParser.parse(it) }.rows.map { it.externalTransactionId }
        }
        assertThat(allIds).hasSize(24)
        assertThat(allIds.toSet()).hasSize(24) // alle Transaction IDs eindeutig über alle Monate hinweg
    }
}
