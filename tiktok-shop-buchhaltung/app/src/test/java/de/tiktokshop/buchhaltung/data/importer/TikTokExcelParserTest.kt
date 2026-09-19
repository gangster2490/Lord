package de.tiktokshop.buchhaltung.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Validiert den Parser gegen synthetische Fixtures, die strukturell und in ihren Eigenheiten
 * exakt echte TikTok-Shop-Earnings-Reports nachbilden (siehe
 * app/src/test/resources/tiktok_reports/ und dessen Erzeugungsskript) - anonymisiert, damit
 * keine echten Finanzdaten/Geschäftspartnernamen im Repository landen. Nachgebildete
 * Eigenheiten aus den ursprünglich analysierten echten Reports:
 * - Zeile 5 ist komplett leer (Sparse-Row-Fall im XML).
 * - Eine Transaction ID kann mehrfach mit unterschiedlichem "Type of earnings" auftreten
 *   (TXN-A-003: "Seller bonus" + "Standard commission" - zwei echte, unterschiedliche
 *   Einnahmen für denselben Verkauf).
 * - Eine Zeile kann ein leeres "Income"-Feld haben (TXN-A-007) und muss als Fehler gemeldet,
 *   nicht als 0 € erfunden werden.
 */
class TikTokExcelParserTest {

    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("tiktok_reports/$name")) {
            "Test-Fixture $name nicht gefunden"
        }

    private fun parseFixture(name: String) = resource(name).use { TikTokExcelParser.parse(it) }

    @Test
    fun `fixture A parses with correct row count, total and metadata`() {
        val result = parseFixture("fixture_month_a.xlsx")

        assertThat(result.rows).hasSize(8) // 9 Datenzeilen, 1 davon Fehler (leeres Income)
        assertThat(result.rows.sumOf { it.grossAmountCents }).isEqualTo(1806L) // 18,06 EUR
        assertThat(result.creatorName).isEqualTo("TEST CREATOR")
        assertThat(result.period).isEqualTo("2026 Test-A")
    }

    @Test
    fun `blank Income cell is reported as a row error, not invented as zero`() {
        val result = parseFixture("fixture_month_a.xlsx")
        assertThat(result.errors).containsExactly(
            TikTokEarningsRowError(14, "Ungültiger Betrag in Spalte 'Income': ''"),
        )
    }

    @Test
    fun `the same Transaction ID with two different earning types produces two distinct rows`() {
        val result = parseFixture("fixture_month_a.xlsx")
        val sharedIdRows = result.rows.filter { it.externalTransactionId == "TXN-A-003" }

        assertThat(sharedIdRows).hasSize(2)
        assertThat(sharedIdRows.map { it.earningType }).containsExactly("Seller bonus", "Standard commission")
        assertThat(sharedIdRows.map { it.grossAmountCents }).containsExactly(21L, 120L)
    }

    @Test
    fun `first row of fixture A maps fields correctly`() {
        val row = parseFixture("fixture_month_a.xlsx").rows.first()
        assertThat(row.transactionDate).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(row.externalTransactionId).isEqualTo("TXN-A-001")
        assertThat(row.earningType).isEqualTo("Standard commission")
        assertThat(row.incomeType).isEqualTo("Provision")
        assertThat(row.currency).isEqualTo("EUR")
        assertThat(row.grossAmountCents).isEqualTo(237L)
        assertThat(row.payer).isEqualTo("Fake Payer AG")
        assertThat(row.payerCountry).isEqualTo("DE")
    }

    @Test
    fun `earning types are mapped to income types correctly`() {
        val byEarningType = parseFixture("fixture_month_a.xlsx").rows.associateBy { it.earningType }

        assertThat(byEarningType.getValue("Standard commission").incomeType).isEqualTo("Provision")
        assertThat(byEarningType.getValue("Shop ads commission").incomeType).isEqualTo("Provision")
        assertThat(byEarningType.getValue("Seller bonus").incomeType).isEqualTo("Bonus")
        assertThat(byEarningType.getValue("Affiliate partner bonus").incomeType).isEqualTo("Bonus")
        assertThat(byEarningType.getValue("Rewards").incomeType).isEqualTo("Rewards")
    }

    @Test
    fun `fixture B has no Transaction ID overlap with fixture A`() {
        val idsA = parseFixture("fixture_month_a.xlsx").rows.map { it.externalTransactionId }.toSet()
        val idsB = parseFixture("fixture_month_b.xlsx").rows.map { it.externalTransactionId }.toSet()

        assertThat(idsA.intersect(idsB)).isEmpty()
        assertThat(parseFixture("fixture_month_b.xlsx").rows.sumOf { it.grossAmountCents }).isEqualTo(666L) // 6,66 EUR
    }
}
