package de.tiktokshop.buchhaltung.ui.buchungen

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import org.junit.Test
import java.time.LocalDate

/**
 * Deckt die Dashboard-Payout-Status-Klicks ab: Earned/Eingefroren/Verfügbar/Ausgezahlt sollen
 * jeweils auf ihre eigene, korrekt gefilterte Buchungen-Ansicht führen statt (wie zuvor) alle
 * drei nicht-Frozen-Zeilen auf BuchungFilter.ALLE. Testet die reine Filterlogik direkt (ohne
 * Room/Robolectric), damit der Test schnell und deterministisch bleibt.
 */
class BuchungenViewModelTest {

    private fun income(status: IncomeStatus, amountCents: Long) = IncomeEntry(
        date = LocalDate.of(2026, 1, 15),
        platform = "TikTok Shop",
        incomeType = "Provision",
        amountCents = amountCents,
        status = status,
    )

    private fun expense(amountCents: Long) = ExpenseEntry(
        date = LocalDate.of(2026, 1, 15),
        category = ExpenseCategory.OTHER,
        grossAmountCents = amountCents,
        businessAmountCents = amountCents,
    )

    @Test
    fun `AVAILABLE filter returns only available incomes, not frozen or paid out`() {
        val rows = buchungRows(
            incomes = listOf(
                income(IncomeStatus.FROZEN, 70261),
                income(IncomeStatus.AVAILABLE, 12345),
                income(IncomeStatus.PAID_OUT, 99999),
            ),
            expenses = listOf(expense(500)),
        )

        val filtered = applyBuchungFilter(rows, BuchungFilter.AVAILABLE)

        assertThat(filtered.map { it.amountCents }).containsExactly(12345L)
        assertThat(filtered.map { it.incomeStatus }).containsExactly(IncomeStatus.AVAILABLE)
    }

    @Test
    fun `PAID_OUT filter returns only paid out incomes, never frozen (§ Eingefroren darf nicht als Ausgezahlt zaehlen)`() {
        val rows = buchungRows(
            incomes = listOf(
                income(IncomeStatus.FROZEN, 70261),
                income(IncomeStatus.PAID_OUT, 99999),
            ),
            expenses = emptyList(),
        )

        val filtered = applyBuchungFilter(rows, BuchungFilter.PAID_OUT)

        assertThat(filtered.map { it.amountCents }).containsExactly(99999L)
        assertThat(filtered.none { it.incomeStatus == IncomeStatus.FROZEN }).isTrue()
    }

    @Test
    fun `FROZEN filter is unaffected by the new AVAILABLE and PAID_OUT filters`() {
        val rows = buchungRows(
            incomes = listOf(
                income(IncomeStatus.FROZEN, 70261),
                income(IncomeStatus.AVAILABLE, 12345),
                income(IncomeStatus.PAID_OUT, 99999),
            ),
            expenses = emptyList(),
        )

        val filtered = applyBuchungFilter(rows, BuchungFilter.FROZEN)

        assertThat(filtered.map { it.amountCents }).containsExactly(70261L)
        assertThat(filtered.single().isFrozen).isTrue()
    }

    @Test
    fun `EINNAHMEN filter (used for the Earned row) includes incomes of every status`() {
        val rows = buchungRows(
            incomes = listOf(
                income(IncomeStatus.FROZEN, 70261),
                income(IncomeStatus.AVAILABLE, 12345),
                income(IncomeStatus.PAID_OUT, 99999),
            ),
            expenses = listOf(expense(500)),
        )

        val filtered = applyBuchungFilter(rows, BuchungFilter.EINNAHMEN)

        assertThat(filtered.map { it.amountCents }).containsExactly(70261L, 12345L, 99999L)
    }
}
