package de.tiktokshop.buchhaltung.data.importer

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.EntryType
import org.junit.Test
import java.time.LocalDate

/**
 * Deckt den generischen Excel/CSV-Import (§1-6,21 der Vereinfachungs-Vorgabe) ab: kein
 * bestimmter Sheet-Name/Dateiformat nötig, Einnahme/Ausgabe wird automatisch erkannt, ein
 * TikTok-Report wird weiterhin über den bestehenden [TikTokExcelParser] gelesen.
 *
 * WICHTIG: eine Tabelle mit expliziter "Händler"+"Kategorie"-Spalte, aber OHNE eigene
 * Income/Expense-Spalte, hat keinen zuverlässigen Hinweis auf den Buchungstyp - nur das
 * Vorzeichen des Betrags entscheidet (negativ = Ausgabe, sonst Einnahme, siehe
 * [ImportCandidateRow.isAmbiguousType]). Ein "ganz normales Excel" mit nur positiven Beträgen
 * (weder Einnahmen- noch Ausgaben-Spalte) wird deshalb standardmäßig als Einnahme erkannt -
 * das war vorher ein Bug (wurde immer als Ausgabe erkannt, siehe
 * `generic amount without a sign defaults to income, not always expense`).
 */
class UniversalSpreadsheetImporterTest {

    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("tiktok_reports/$name")) {
            "Test-Fixture $name nicht gefunden"
        }

    private fun genericResource(name: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("generic_reports/$name")) {
            "Test-Fixture $name nicht gefunden"
        }

    @Test
    fun `generic amount without a sign defaults to income, not always expense`() {
        // Regressionstest für den gemeldeten Bug: ein ganz normales Excel/CSV mit
        // ausschließlich Einnahmen (positive Beträge, keine Income/Expense-Spalte) wurde
        // vorher fälschlich IMMER als Ausgabe importiert.
        val csv = "Datum;Quelle;Betrag\n01.01.2026;TikTok Auszahlung;123,45\n"
        val result = UniversalSpreadsheetImporter.analyze(csv.toByteArray(), "einnahmen.csv")

        assertThat(result.rows).hasSize(1)
        val row = result.rows.single()
        assertThat(row.type).isEqualTo(EntryType.INCOME)
        assertThat(row.amountCents).isEqualTo(12345L)
        assertThat(row.isAmbiguousType).isTrue()
    }

    @Test
    fun `a negative generic amount is detected as an expense`() {
        val csv = """
            Datum;Händler;Betrag;Kategorie
            03.01.2026;CapCut;-9,99;Software
            15.02.2026;DHL;-4,50;Versand
        """.trimIndent()

        val result = UniversalSpreadsheetImporter.analyze(csv.toByteArray(), "ausgaben.csv")

        assertThat(result.rows).hasSize(2)
        assertThat(result.rows.all { it.type == EntryType.EXPENSE }).isTrue()
        assertThat(result.rows.all { it.isAmbiguousType }).isTrue()
        assertThat(result.rows.sumOf { it.amountCents }).isEqualTo(999L + 450L) // Beträge als Magnitude gespeichert
        assertThat(result.rows[0].date).isEqualTo(LocalDate.of(2026, 1, 3))
        assertThat(result.rows[0].merchant).isEqualTo("CapCut")
    }

    @Test
    fun `a mixed-sign generic amount column splits income and expense rows in the same file`() {
        // Realistischer Fall: ein Kontoauszug-artiges Excel mit nur EINER Betrag-Spalte, in der
        // Auszahlungen positiv und Gebühren/Abbuchungen negativ stehen - beide Typen müssen aus
        // derselben Datei korrekt getrennt werden, nichts darf verloren gehen (§3).
        val csv = """
            Datum;Beschreibung;Betrag
            01.01.2026;TikTok Auszahlung;150,00
            02.01.2026;CapCut Abo;-9,99
            03.01.2026;Bonuszahlung;25,50
            04.01.2026;PayPal Gebühr;-2,10
        """.trimIndent()

        val result = UniversalSpreadsheetImporter.analyze(csv.toByteArray(), "kontoauszug.csv")

        assertThat(result.rows).hasSize(4)
        val income = result.rows.filter { it.type == EntryType.INCOME }
        val expense = result.rows.filter { it.type == EntryType.EXPENSE }
        assertThat(income.map { it.amountCents }).containsExactly(15000L, 2550L)
        assertThat(expense.map { it.amountCents }).containsExactly(999L, 210L)
        assertThat(result.rows.all { it.isAmbiguousType }).isTrue()
    }

    @Test
    fun `a plain expense list with German dates and no sign is parsed correctly (type guessed as income until overridden)`() {
        val csv = """
            Datum;Händler;Betrag
            15.03.2026;Deutsche Bahn;45,90
            22.04.2026;Rossmann;12,30
        """.trimIndent()

        val result = UniversalSpreadsheetImporter.analyze(csv.toByteArray(), "reisekosten.csv")

        assertThat(result.rows).hasSize(2)
        assertThat(result.rows[0].date).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(result.rows[1].date).isEqualTo(LocalDate.of(2026, 4, 22))
        assertThat(result.rows.sumOf { it.amountCents }).isEqualTo(4590L + 1230L)
    }

    @Test
    fun `an explicit Expense column is unambiguous regardless of sign`() {
        val csv = "Date,Merchant,Income,Expense\n2026-03-01,Canva,,12.00\n"
        val result = UniversalSpreadsheetImporter.analyze(csv.toByteArray(), "expenses.csv")

        assertThat(result.rows).hasSize(1)
        val row = result.rows.single()
        assertThat(row.type).isEqualTo(EntryType.EXPENSE)
        assertThat(row.amountCents).isEqualTo(1200L)
        assertThat(row.isAmbiguousType).isFalse()
    }

    @Test
    fun `a plain generic-amount xlsx with only income rows imports as income (no TikTok format needed)`() {
        val bytes = genericResource("generic_einnahmen.xlsx").use { it.readBytes() }
        val result = UniversalSpreadsheetImporter.analyze(bytes, "generic_einnahmen.xlsx")

        assertThat(result.rows).hasSize(20)
        assertThat(result.rows.all { it.type == EntryType.INCOME }).isTrue()
        assertThat(result.rows.sumOf { it.amountCents }).isEqualTo(197697L) // 1.976,97 EUR
    }

    @Test
    fun `a plain generic-amount xlsx without a sign is recognized as expense via the sheet name Ausgaben`() {
        // Ohne Vorzeichen (reine Ausgabenliste, alle Beträge positiv) hilft der Sheet-Name als
        // zusätzliches Signal: ein Sheet namens "Ausgaben" wird direkt als Ausgabe erkannt,
        // ohne dass der Nutzer manuell umschalten muss. Bleibt trotzdem als Vermutung markiert
        // (isAmbiguousType), damit der Umschalter im Preview weiterhin verfügbar ist.
        val bytes = genericResource("generic_ausgaben.xlsx").use { it.readBytes() }
        val result = UniversalSpreadsheetImporter.analyze(bytes, "generic_ausgaben.xlsx")

        assertThat(result.rows).hasSize(15)
        assertThat(result.rows.all { it.type == EntryType.EXPENSE }).isTrue()
        assertThat(result.rows.sumOf { it.amountCents }).isEqualTo(82007L) // 820,07 EUR
        assertThat(result.rows.all { it.isAmbiguousType }).isTrue()
    }

    @Test
    fun `Ausgaben_2026_APK_Import xlsx with header row pushed down by title rows imports correctly`() {
        // Regressionstest für den gemeldeten Bug: das Sheet "Ausgaben_2026" hat 15 Titel-/
        // Legenden-/Leerzeilen VOR der echten Kopfzeile (Datum, Händler / Dienst, Betrag (€),
        // Währung, Kategorie, Geschäftlich (%), Status, Quelle, Beleg / Hinweis) - mit dem
        // alten 15-Zeilen-Suchfenster wurde die Kopfzeile nie gefunden und die Datei
        // fälschlich als "keine Tabellendaten" abgelehnt. Zusätzliches "Übersicht"-Sheet
        // testet, dass die Suche über ALLE Sheets robust bleibt (§9).
        //
        // Fixture ist synthetisch (echte Nutzerdatei nicht im Repository, siehe Datenschutz-
        // Hinweis in der README), aber strukturell identisch zur gemeldeten Datei nachgebaut:
        // gleicher Sheet-Name, gleiche Spalten, 63 Zeilen, Summe exakt 820,07 EUR.
        val bytes = genericResource("Ausgaben_2026_APK_Import.xlsx").use { it.readBytes() }
        val result = UniversalSpreadsheetImporter.analyze(bytes, "Ausgaben_2026_APK_Import.xlsx")

        val sheet = result.sheets.first { it.sheetName == "Ausgaben_2026" }
        assertThat(sheet.isConfident).isTrue()
        assertThat(sheet.headerRowIndex).isEqualTo(15)

        assertThat(result.rows).hasSize(63)
        assertThat(result.rows.all { it.type == EntryType.EXPENSE }).isTrue()
        assertThat(result.rows.sumOf { it.amountCents }).isEqualTo(82007L) // 820,07 EUR
        assertThat(result.rows.all { it.businessPercent == 100 }).isTrue()
        assertThat(result.rows.all { it.status != null && it.source != null }).isTrue()
    }

    @Test
    fun `a sheet with only an amount column but no date needs column mapping instead of erroring`() {
        val csv = "Notiz,Betrag\nfoo,12.00\n"
        val result = UniversalSpreadsheetImporter.analyze(csv.toByteArray(), "unklar.csv")

        assertThat(result.rows).isEmpty()
        assertThat(result.needsColumnMapping).isTrue()
    }

    @Test
    fun `a completely unreadable file throws instead of silently returning nothing`() {
        assertThrows(ImportException::class.java) {
            UniversalSpreadsheetImporter.analyze(ByteArray(0), "leer.csv")
        }
    }

    @Test
    fun `a file with completely unrecognized headers still offers column mapping instead of failing`() {
        // §1 der Bugmeldung: "Import fehlgeschlagen" darf NIE die erste Reaktion auf eine
        // unbekannte Kopfzeile sein - selbst wenn nicht eine einzige Spalte automatisch erkannt
        // wird, muss die Datei mit "Spalten zuordnen" (Rohkopf zur manuellen Zuordnung)
        // beantwortet werden, nicht mit einem Fehler.
        val result = UniversalSpreadsheetImporter.analyze("just,one\nline,here\n".toByteArray(), "kaumdaten.csv")

        assertThat(result.rows).isEmpty()
        assertThat(result.needsColumnMapping).isTrue()
        val sheet = result.sheets.single()
        assertThat(sheet.isConfident).isFalse()
        assertThat(sheet.columnMapping).isEmpty()
        assertThat(sheet.headers).containsExactly("just", "one")
    }

    @Test
    fun `a file where every sheet is completely blank still throws a clear error`() {
        assertThrows(ImportException::class.java) {
            UniversalSpreadsheetImporter.analyze(",\n,\n\n".toByteArray(), "leerzeilen.csv")
        }
    }

    @Test
    fun `TikTok-shaped xlsx is still parsed via the existing TikTokExcelParser fast path`() {
        val bytes = resource("fixture_month_a.xlsx").use { it.readBytes() }
        val result = UniversalSpreadsheetImporter.analyze(bytes, "fixture_month_a.xlsx")

        assertThat(result.rows).hasSize(8)
        assertThat(result.rows.sumOf { it.amountCents }).isEqualTo(1806L)
        assertThat(result.rows.all { it.type == EntryType.INCOME }).isTrue()

        // Geteilte Transaction ID mit zwei Earning-Types muss weiterhin als zwei Zeilen erhalten bleiben.
        val shared = result.rows.filter { it.externalTransactionId == "TXN-A-003" }
        assertThat(shared).hasSize(2)
        assertThat(shared.map { it.externalEarningType }).containsExactly("Seller bonus", "Standard commission")
    }

    private fun assertThrows(expected: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (expected.isInstance(t)) return
            throw AssertionError("Expected ${expected.name} but got ${t::class.java.name}", t)
        }
        throw AssertionError("Expected ${expected.name} but nothing was thrown")
    }
}
