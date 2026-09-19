package de.tiktokshop.buchhaltung.domain

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import org.junit.Test
import java.time.LocalDate

class DashboardCalculatorTest {

    private val from = LocalDate.of(2026, 1, 1)
    private val to = LocalDate.of(2026, 12, 31)

    private fun income(status: IncomeStatus, amountCents: Long, paidOutAmountCents: Long? = null, date: LocalDate = LocalDate.of(2026, 6, 1)) =
        IncomeEntry(date = date, platform = "TikTok Shop", incomeType = "Provision", amountCents = amountCents, status = status, paidOutAmountCents = paidOutAmountCents)

    private fun expense(businessAmountCents: Long, date: LocalDate = LocalDate.of(2026, 6, 1)) =
        ExpenseEntry(date = date, category = ExpenseCategory.OTHER, grossAmountCents = businessAmountCents, businessAmountCents = businessAmountCents)

    @Test
    fun `frozen income is excluded from paid out and available totals`() {
        val incomes = listOf(income(IncomeStatus.FROZEN, 70261))
        val summary = DashboardCalculator.calculate(incomes, emptyList(), from, to)

        assertThat(summary.frozenCents).isEqualTo(70261L)
        assertThat(summary.paidOutCents).isEqualTo(0L)
        assertThat(summary.availableCents).isEqualTo(0L)
        // Angezeigte Provision enthält die Frozen-Provision weiterhin (sie wurde angezeigt).
        assertThat(summary.displayedTotalCents).isEqualTo(70261L)
    }

    @Test
    fun `reversed income is excluded from displayed total`() {
        val incomes = listOf(income(IncomeStatus.REVERSED, 5000))
        val summary = DashboardCalculator.calculate(incomes, emptyList(), from, to)
        assertThat(summary.displayedTotalCents).isEqualTo(0L)
    }

    @Test
    fun `preliminary result is paid out minus business expenses`() {
        val incomes = listOf(income(IncomeStatus.PAID_OUT, amountCents = 10000, paidOutAmountCents = 9500))
        val expenses = listOf(expense(4000))
        val summary = DashboardCalculator.calculate(incomes, expenses, from, to)

        assertThat(summary.paidOutCents).isEqualTo(9500L)
        assertThat(summary.expensesCents).isEqualTo(4000L)
        assertThat(summary.preliminaryResultCents).isEqualTo(5500L)
    }

    @Test
    fun `entries outside the range are ignored`() {
        val incomes = listOf(income(IncomeStatus.AVAILABLE, 1000, date = LocalDate.of(2025, 12, 31)))
        val summary = DashboardCalculator.calculate(incomes, emptyList(), from, to)
        assertThat(summary.availableCents).isEqualTo(0L)
    }
}
