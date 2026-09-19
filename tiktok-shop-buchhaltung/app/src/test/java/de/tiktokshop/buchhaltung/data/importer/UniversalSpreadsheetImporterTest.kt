package de.tiktokshop.buchhaltung.data.importer

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.EntryType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * Deckt den generischen Excel/CSV-Import (§1-6,21 der Vereinfachungs-Vorgabe) ab: kein
 * bestimmter Sheet-Name/Dateiformat nötig, Einnahme/Ausgabe wird automatisch erkannt, ein
 * TikTok-Report wird weiterhin über den bestehenden [TikTokExcelParser] gelesen.
 */
class UniversalSpreadsheetImporterTest {

    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("tiktok_reports/$name")) {
            "Test-Fixture $name nicht gefunden"
        }

    @Test
    fun `generic German expense CSV is auto-detected as expenses`() {
        val csv = """
            Datum;Händler;Betrag;Kategorie
            03.01.2026;CapCut;9,99;Software
            15.02.2026;DHL;4,50;Versand
        """.trimIndent()

        val result = UniversalSpreadsheetImporter.analyze(csv.toByteArray(), "ausgaben.csv")

        assertThat(result.rows).hasSize(2)
        assertThat(result.rows.all { it.type == EntryType.EXPENSE }).isTrue()
        assertThat(result.rows.sumOf { it.amountCents }).isEqualTo(999L + 450L)
        assertThat(result.rows[0].date).isEqualTo(LocalDate.of(2026, 1, 3))
        assertThat(result.rows[0].merchant).isEqualTo("CapCut")
    }

    @Test
    fun `English expense CSV with Amount column is also recognized`() {
        val csv = "Date,Merchant,Amount\n2026-03-01,Canva,12.00\n"
        val result = UniversalSpreadsheetImporter.analyze(csv.toByteArray(), "expenses.csv")

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().type).isEqualTo(EntryType.EXPENSE)
        assertThat(result.rows.single().amountCents).isEqualTo(1200L)
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
