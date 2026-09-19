package de.tiktokshop.buchhaltung.domain

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import java.time.LocalDate

/**
 * Aggregiert Dashboard-Kennzahlen für einen Zeitraum (UX_FLOW.md, PRODUCT_REQUIREMENTS.md 4.6).
 * "Vorläufiges Ergebnis" basiert bewusst auf tatsächlich ausgezahlten Beträgen minus
 * Ausgaben, nicht auf angezeigten Provisionen (TAX_LOGIC_DE.md: Verfügungsmacht zählt).
 */
data class DashboardSummary(
    val displayedTotalCents: Cents,
    val frozenCents: Cents,
    val availableCents: Cents,
    val paidOutCents: Cents,
    val expensesCents: Cents,
    val preliminaryResultCents: Cents,
    val receiptCount: Int,
    val missingOrUnconfirmedCount: Int,
)

object DashboardCalculator {

    fun calculate(
        incomes: List<IncomeEntry>,
        expenses: List<ExpenseEntry>,
        from: LocalDate,
        to: LocalDate,
    ): DashboardSummary {
        val incomesInRange = incomes.filter { it.date in from..to }
        val expensesInRange = expenses.filter { it.date in from..to }

        val displayedTotal = incomesInRange
            .filter { it.status != IncomeStatus.REVERSED }
            .sumOf { it.amountCents }

        val frozen = incomesInRange.filter { it.status == IncomeStatus.FROZEN }.sumOf { it.amountCents }
        val available = incomesInRange.filter { it.status == IncomeStatus.AVAILABLE }.sumOf { it.amountCents }
        val paidOut = incomesInRange
            .filter { it.status == IncomeStatus.PAID_OUT }
            .sumOf { it.paidOutAmountCents ?: it.amountCents }

        val expensesTotal = expensesInRange.sumOf { it.businessAmountCents }

        val receiptCount = incomesInRange.count { it.receiptUris.isNotEmpty() } +
            expensesInRange.count { it.receiptUris.isNotEmpty() }

        val missingOrUnconfirmed = incomesInRange.count { it.receiptUris.isEmpty() || !it.confirmed } +
            expensesInRange.count { it.receiptUris.isEmpty() || !it.confirmed }

        return DashboardSummary(
            displayedTotalCents = displayedTotal,
            frozenCents = frozen,
            availableCents = available,
            paidOutCents = paidOut,
            expensesCents = expensesTotal,
            preliminaryResultCents = paidOut - expensesTotal,
            receiptCount = receiptCount,
            missingOrUnconfirmedCount = missingOrUnconfirmed,
        )
    }
}
