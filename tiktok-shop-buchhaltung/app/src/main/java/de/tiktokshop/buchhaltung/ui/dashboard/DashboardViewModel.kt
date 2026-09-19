package de.tiktokshop.buchhaltung.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.domain.DashboardCalculator
import de.tiktokshop.buchhaltung.domain.DashboardSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class PeriodFilter { MONTH, QUARTER, YEAR, CUSTOM }

data class DashboardUiState(
    val period: PeriodFilter = PeriodFilter.MONTH,
    val from: LocalDate = LocalDate.now().withDayOfMonth(1),
    val to: LocalDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()),
    val summary: DashboardSummary = DashboardCalculator.calculate(emptyList(), emptyList(), LocalDate.now(), LocalDate.now()),
)

class DashboardViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val range = MutableStateFlow(
        LocalDate.now().withDayOfMonth(1) to LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()),
    )
    private val period = MutableStateFlow(PeriodFilter.MONTH)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeIncomes(),
        repository.observeExpenses(),
        range,
        period,
    ) { incomes, expenses, currentRange, currentPeriod ->
        DashboardUiState(
            period = currentPeriod,
            from = currentRange.first,
            to = currentRange.second,
            summary = DashboardCalculator.calculate(incomes, expenses, currentRange.first, currentRange.second),
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
