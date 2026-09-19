package de.tiktokshop.buchhaltung.export

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import org.junit.Test
import java.time.LocalDate

class ExportFormatterTest {

    // TC07 - Export: Zeitraum 01.05.2026-31.12.2026 enthält nur Buchungen des Zeitraums.
    @Test
    fun `csv export only includes entries within the requested range`() {
        val inRange = IncomeEntry(
            date = LocalDate.of(2026, 6, 1),
            platform = "TikTok Shop",
            incomeType = "Provision",
            amountCents = 70261,
            status = IncomeStatus.FROZEN,
        )
        val outOfRange = IncomeEntry(
            date = LocalDate.of(2026, 1, 1),
            platform = "TikTok Shop",
            incomeType = "Provision",
            amountCents = 5000,
            status = IncomeStatus.AVAILABLE,
        )

        val csv = ExportFormatter.buildGeneralCsv(
            incomes = listOf(inRange, outOfRange),
            expenses = emptyList(),
            from = LocalDate.of(2026, 5, 1),
            to = LocalDate.of(2026, 12, 31),
        )

        assertThat(csv).contains("702,61")
        assertThat(csv).doesNotContain("50,00")
        assertThat(csv.lines()).hasSize(2) // Header + 1 Zeile
    }

    @Test
    fun `general csv formats amounts with German decimal separator and correct status`() {
        val income = IncomeEntry(
            date = LocalDate.of(2026, 9, 19),
            platform = "TikTok Shop",
            incomeType = "Provision",
            amountCents = 70261,
            status = IncomeStatus.FROZEN,
            note = "Provision eingefroren, Abheben nicht möglich",
        )
        val expense = ExpenseEntry(
            date = LocalDate.of(2026, 9, 20),
            merchant = "Beispiel Shop",
            category = ExpenseCategory.VIDEO_PRODUCTS,
            grossAmountCents = 4999,
            businessAmountCents = 4999,
            confirmed = true,
        )

        val csv = ExportFormatter.buildGeneralCsv(
            incomes = listOf(income),
            expenses = listOf(expense),
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 12, 31),
        )

        val lines = csv.lines()
        assertThat(lines[1]).isEqualTo(
            "2026-09-19;EINNAHME;TikTok Shop;Provision;702,61;100;702,61;FROZEN;;Provision eingefroren, Abheben nicht möglich",
        )
        assertThat(lines[2]).isEqualTo(
            "2026-09-20;AUSGABE;Beispiel Shop;VIDEO_PRODUCTS;49,99;100;49,99;BESTAETIGT;;",
        )
    }

    @Test
    fun `tiktok csv reflects frozen status without a payout date`() {
        val income = IncomeEntry(
            date = LocalDate.of(2026, 9, 19),
            platform = "TikTok Shop",
            incomeType = "Provision",
            amountCents = 70261,
            status = IncomeStatus.FROZEN,
        )
        val csv = ExportFormatter.buildTikTokCsv(listOf(income), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertThat(csv.lines()[1]).isEqualTo("2026-09-19;702,61;FROZEN;;;")
    }
}
