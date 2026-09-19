package de.tiktokshop.buchhaltung.data.xlsx

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Validiert den XLSX-Reader gegen eine synthetische, aber strukturell identische Nachbildung
 * eines echten TikTok-Shop-Earnings-Reports (siehe app/src/test/resources/tiktok_reports/ -
 * anonymisiert: gleiche Struktur/Eigenheiten wie die echten Dateien, aber erfundene Beträge/
 * Namen, damit keine realen Finanzdaten im Repository landen). Insbesondere bildet sie nach,
 * dass Zeile 5 im echten Report komplett leer ist und deshalb im XML ganz fehlt (Sparse Rows).
 */
class XlsxReaderTest {

    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("tiktok_reports/$name")) {
            "Test-Fixture $name nicht gefunden"
        }

    @Test
    fun `lists both sheets of the fixture report`() {
        val sheetNames = resource("fixture_month_a.xlsx").use { XlsxReader.listSheetNames(it) }
        assertThat(sheetNames).containsExactly("Sheet1", "Fields explantion")
    }

    @Test
    fun `reads Sheet1 header row and first data row correctly despite a fully empty row 5`() {
        val rows = resource("fixture_month_a.xlsx").use { XlsxReader.readSheet(it, "Sheet1") }

        // Zeile 6 (Index 5) ist die echte Kopfzeile - Zeile 5 ist komplett leer und fehlt im
        // XML ganz (siehe Klassenkommentar); der Reader muss das per Row-Index ausgleichen.
        val header = rows[5]
        assertThat(header[0]).isEqualTo("Date (UTC+0)")
        assertThat(header[1]).isEqualTo("Transaction ID")
        assertThat(header[2]).isEqualTo("Type of earnings")
        assertThat(header[3]).isEqualTo("Currency")
        assertThat(header[4]).isEqualTo("Income")
        assertThat(header[5]).isEqualTo("Expense")
        assertThat(header[6]).isEqualTo("Payer")
        assertThat(header[7]).isEqualTo("Payer country")

        val firstDataRow = rows[6]
        assertThat(firstDataRow[0]).isEqualTo("2026-01-01")
        assertThat(firstDataRow[1]).isEqualTo("TXN-A-001")
        assertThat(firstDataRow[2]).isEqualTo("Standard commission")
        assertThat(firstDataRow[3]).isEqualTo("EUR")
        assertThat(firstDataRow[4]).isEqualTo("2.37")
        assertThat(firstDataRow[6]).isEqualTo("Fake Payer AG")
        assertThat(firstDataRow[7]).isEqualTo("DE")
    }

    @Test
    fun `falls back to the data sheet when the explanation sheet name is requested`() {
        val rows = resource("fixture_month_a.xlsx").use { XlsxReader.readSheet(it, "does-not-exist") }
        // Muss auf "Sheet1" zurückfallen, NICHT auf "Fields explantion".
        assertThat(rows[5][0]).isEqualTo("Date (UTC+0)")
    }

    @Test
    fun `column index parsing handles multi-letter references`() {
        assertThat(XlsxReader.columnIndexFromRef("A1")).isEqualTo(0)
        assertThat(XlsxReader.columnIndexFromRef("C7")).isEqualTo(2)
        assertThat(XlsxReader.columnIndexFromRef("Z1")).isEqualTo(25)
        assertThat(XlsxReader.columnIndexFromRef("AA1")).isEqualTo(26)
    }
}
