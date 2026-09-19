package de.tiktokshop.buchhaltung.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.domain.DashboardCalculator
import de.tiktokshop.buchhaltung.domain.DashboardSummary
import de.tiktokshop.buchhaltung.domain.FrozenBalanceCalculator
import de.tiktokshop.buchhaltung.domain.FrozenBalanceSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class PeriodFilter { MONTH, QUARTER, YEAR, CUSTOM }

data class DashboardUiState(
    val period: PeriodFilter = PeriodFilter.YEAR,
    val from: LocalDate = LocalDate.of(LocalDate.now().year, 1, 1),
    val to: LocalDate = LocalDate.of(LocalDate.now().year, 12, 31),
    val summary: DashboardSummary = DashboardCalculator.calculate(emptyList(), emptyList(), LocalDate.now(), LocalDate.now()),
    /** Eingefroren/Verfügbar/Ausgezahlt im Auszahlungsstatus-Block - siehe [FrozenBalanceCalculator]. */
    val frozenBalanceSummary: FrozenBalanceSummary = FrozenBalanceCalculator.calculate(emptyList()),
    /**
     * "Verfügbarer Überschuss" (earnedTotal - frozenAmount - expensesTotal) - eigener,
     * zusätzlicher Kennwert neben [DashboardSummary.earnedMinusExpensesCents] ("Vorläufiges
     * Ergebnis"), ersetzt diesen nicht.
     */
    val availableSurplusCents: Cents = FrozenBalanceCalculator.calculateAvailableSurplusCents(0L, 0L, 0L),
)

class DashboardViewModel(private val repository: LedgerRepository) : ViewModel() {

    // Default-Zeitraum ist das laufende Jahr (§9: "Monat/Quartal/Jahr, Default: Jahr") - eine
    // Provision, die im Januar erfasst wurde, soll nicht aus dem Dashboard "verschwinden", nur
    // weil der Monat gewechselt hat.
    private val range = MutableStateFlow(
        LocalDate.of(LocalDate.now().year, 1, 1) to LocalDate.of(LocalDate.now().year, 12, 31),
    )
    private val period = MutableStateFlow(PeriodFilter.YEAR)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeIncomes(),
        repository.observeExpenses(),
        repository.observeFrozenBalances(),
        range,
        period,
    ) { incomes, expenses, frozenBalances, currentRange, currentPeriod ->
        val summary = DashboardCalculator.calculate(incomes, expenses, currentRange.first, currentRange.second)
        val frozenBalanceSummary = FrozenBalanceCalculator.calculate(frozenBalances)
        DashboardUiState(
            period = currentPeriod,
            from = currentRange.first,
            to = currentRange.second,
            summary = summary,
            frozenBalanceSummary = frozenBalanceSummary,
            availableSurplusCents = FrozenBalanceCalculator.calculateAvailableSurplusCents(
                earnedTotalCents = summary.displayedTotalCents,
                frozenCents = frozenBalanceSummary.frozenCents,
                expensesCents = summary.expensesCents,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun selectPeriod(newPeriod: PeriodFilter) {
        period.value = newPeriod
        val today = LocalDate.now()
        range.value = when (newPeriod) {
            PeriodFilter.MONTH -> today.withDayOfMonth(1) to today.with(TemporalAdjusters.lastDayOfMonth())
            PeriodFilter.QUARTER -> {
                val quarterStartMonth = ((today.monthValue - 1) / 3) * 3 + 1
                val start = LocalDate.of(today.year, quarterStartMonth, 1)
                start to start.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth())
            }
            PeriodFilter.YEAR -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
            PeriodFilter.CUSTOM -> range.value
        }
    }

    fun selectCustomRange(from: LocalDate, to: LocalDate) {
        period.value = PeriodFilter.CUSTOM
        range.value = from to to
    }
}
