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
    fun `a plain generic-amount xlsx with only expense rows still needs the sign override in practice`() {
        // Ohne Vorzeichen (reine Ausgabenliste, alle Beträge positiv) erkennt die
        // Vorzeichen-Heuristik die Datei zunächst als Einnahme - das ist erwartet und wird im
        // Import-Preview über den Einnahmen/Ausgaben-Umschalter korrigiert (siehe
        // UniversalImportRepositoryTest."setAmbiguousRowsType flips a plain positive-amount
        // file from income to expense").
        val bytes = genericResource("generic_ausgaben.xlsx").use { it.readBytes() }
        val result = UniversalSpreadsheetImporter.analyze(bytes, "generic_ausgaben.xlsx")

        assertThat(result.rows).hasSize(15)
        assertThat(result.rows.sumOf { it.amountCents }).isEqualTo(82007L) // 820,07 EUR
        assertThat(result.rows.all { it.isAmbiguousType }).isTrue()
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
    fun `a file with no tabular data at all throws a clear error`() {
        assertThrows(ImportException::class.java) {
            UniversalSpreadsheetImporter.analyze("just,one\nline,here\n".toByteArray(), "kaumdaten.csv")
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
