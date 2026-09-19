package de.tiktokshop.buchhaltung.domain

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import org.junit.Test
import java.time.LocalDate

/**
 * Deckt die Kern-Anforderung von "Eingefrorenen Betrag erfassen" ab: eine [FrozenBalanceEntry]
 * ist bereits Teil der angezeigten Einnahmen (Earned) und darf NIE eine zusätzliche Einnahme
 * erzeugen. [DashboardCalculator] nimmt bewusst keine FrozenBalanceEntry-Liste entgegen - diese
 * Tests belegen, dass Hinzufügen/Statuswechsel von Frozen-Balance-Einträgen weder
 * Einnahmen/Ausgaben noch das Vorläufige Ergebnis verändert, und ausschließlich in
 * [FrozenBalanceSummary] (Auszahlungsstatus-Block) sichtbar wird.
 */
class FrozenBalanceCalculatorTest {

    private val from = LocalDate.of(2026, 1, 1)
    private val to = LocalDate.of(2026, 12, 31)

    private fun income(amountCents: Long, date: LocalDate = LocalDate.of(2026, 6, 1)) = IncomeEntry(
        date = date,
        platform = "TikTok Shop",
        incomeType = "Provision",
        amountCents = amountCents,
        status = IncomeStatus.ACCRUED,
    )

    private fun expense(businessAmountCents: Long, date: LocalDate = LocalDate.of(2026, 6, 1)) = ExpenseEntry(
        date = date,
        category = ExpenseCategory.OTHER,
        grossAmountCents = businessAmountCents,
        businessAmountCents = businessAmountCents,
    )

    private fun frozenEntry(status: FrozenBalanceStatus, amountCents: Long = 70261L) = FrozenBalanceEntry(
        date = LocalDate.of(2026, 9, 19),
        amountCents = amountCents,
        platform = "TikTok Shop",
        status = status,
    )

    @Test
    fun `adding frozen 702,61 does not change income total`() {
        val incomes = listOf(income(197697L))
        val summaryWithoutFrozen = DashboardCalculator.calculate(incomes, emptyList(), from, to)

        // Ein FrozenBalanceEntry existiert jetzt zusätzlich - DashboardCalculator sieht ihn nie.
        FrozenBalanceCalculator.calculate(listOf(frozenEntry(FrozenBalanceStatus.FROZEN)))
        val summaryWithFrozen = DashboardCalculator.calculate(incomes, emptyList(), from, to)

        assertThat(summaryWithFrozen.displayedTotalCents).isEqualTo(197697L)
        assertThat(summaryWithFrozen.displayedTotalCents).isEqualTo(summaryWithoutFrozen.displayedTotalCents)
    }

    @Test
    fun `adding frozen does not change interim result`() {
        val incomes = listOf(income(197697L))
        val expenses = listOf(expense(82007L))

        val summaryWithoutFrozen = DashboardCalculator.calculate(incomes, expenses, from, to)
        FrozenBalanceCalculator.calculate(listOf(frozenEntry(FrozenBalanceStatus.FROZEN)))
        val summaryWithFrozen = DashboardCalculator.calculate(incomes, expenses, from, to)

        assertThat(summaryWithFrozen.earnedMinusExpensesCents).isEqualTo(115690L)
        assertThat(summaryWithFrozen.earnedMinusExpensesCents).isEqualTo(summaryWithoutFrozen.earnedMinusExpensesCents)
    }

    @Test
    fun `frozen total appears only in payout status`() {
        val incomes = listOf(income(197697L))
        val expenses = listOf(expense(82007L))
        val summary = DashboardCalculator.calculate(incomes, expenses, from, to)
        val frozenSummary = FrozenBalanceCalculator.calculate(listOf(frozenEntry(FrozenBalanceStatus.FROZEN)))

        // Der Frozen-Betrag steckt ausschließlich in FrozenBalanceSummary...
        assertThat(frozenSummary.frozenCents).isEqualTo(70261L)
        // ...und verändert keines der Einnahmen/Ausgaben/Ergebnis-Felder des Dashboards.
        assertThat(summary.displayedTotalCents).isEqualTo(197697L)
        assertThat(summary.expensesCents).isEqualTo(82007L)
        assertThat(summary.earnedMinusExpensesCents).isEqualTo(115690L)
    }

    @Test
    fun `changing FROZEN to AVAILABLE moves amount between status totals`() {
        val beforeChange = FrozenBalanceCalculator.calculate(listOf(frozenEntry(FrozenBalanceStatus.FROZEN)))
        assertThat(beforeChange.frozenCents).isEqualTo(70261L)
        assertThat(beforeChange.availableCents).isEqualTo(0L)

        val afterChange = FrozenBalanceCalculator.calculate(listOf(frozenEntry(FrozenBalanceStatus.AVAILABLE)))
        assertThat(afterChange.frozenCents).isEqualTo(0L)
        assertThat(afterChange.availableCents).isEqualTo(70261L)
        assertThat(afterChange.paidOutCents).isEqualTo(0L)
    }

    @Test
    fun `changing AVAILABLE to PAID_OUT moves amount correctly`() {
        val beforeChange = FrozenBalanceCalculator.calculate(listOf(frozenEntry(FrozenBalanceStatus.AVAILABLE)))
        assertThat(beforeChange.availableCents).isEqualTo(70261L)
        assertThat(beforeChange.paidOutCents).isEqualTo(0L)

        val afterChange = FrozenBalanceCalculator.calculate(listOf(frozenEntry(FrozenBalanceStatus.PAID_OUT)))
        assertThat(afterChange.availableCents).isEqualTo(0L)
        assertThat(afterChange.paidOutCents).isEqualTo(70261L)
        assertThat(afterChange.frozenCents).isEqualTo(0L)
    }

    @Test
    fun `multiple frozen entries are summed and REVERSED entries never count`() {
        val entries = listOf(
            frozenEntry(FrozenBalanceStatus.FROZEN, amountCents = 70261L),
            frozenEntry(FrozenBalanceStatus.FROZEN, amountCents = 5000L),
            frozenEntry(FrozenBalanceStatus.REVERSED, amountCents = 9999L),
        )
        val summary = FrozenBalanceCalculator.calculate(entries)

        assertThat(summary.frozenCents).isEqualTo(75261L)
        assertThat(summary.availableCents).isEqualTo(0L)
        assertThat(summary.paidOutCents).isEqualTo(0L)
    }

    @Test
    fun `available surplus is earnedTotal minus frozenAmount minus expensesTotal`() {
        // 1.976,97 - 702,61 - 820,07 = 454,29 EUR
        val surplusCents = FrozenBalanceCalculator.calculateAvailableSurplusCents(
            earnedTotalCents = 197697L,
            frozenCents = 70261L,
            expensesCents = 82007L,
        )

        assertThat(surplusCents).isEqualTo(45429L)
    }

    @Test
    fun `available surplus does not replace the interim result - both are independent`() {
        val incomes = listOf(income(197697L))
        val expenses = listOf(expense(82007L))
        val summary = DashboardCalculator.calculate(incomes, expenses, from, to)
        val frozenSummary = FrozenBalanceCalculator.calculate(listOf(frozenEntry(FrozenBalanceStatus.FROZEN)))

        val surplusCents = FrozenBalanceCalculator.calculateAvailableSurplusCents(
            earnedTotalCents = summary.displayedTotalCents,
            frozenCents = frozenSummary.frozenCents,
            expensesCents = summary.expensesCents,
        )

        // Vorläufiges Ergebnis bleibt unverändert 1.156,90 EUR (earnedTotal - expensesTotal)...
        assertThat(summary.earnedMinusExpensesCents).isEqualTo(115690L)
        // ...während der Verfügbare Überschuss zusätzlich den Frozen-Betrag abzieht: 454,29 EUR.
        assertThat(surplusCents).isEqualTo(45429L)
    }
}
