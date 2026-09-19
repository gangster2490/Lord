package de.tiktokshop.buchhaltung.ui.frozen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FrozenBalanceListUiState(
    val filter: FrozenBalanceStatus? = null,
    val rows: List<FrozenBalanceEntry> = emptyList(),
    val totalCents: Cents = 0L,
)

/** Liste der separaten Auszahlungsstatus-Einträge (§ "Eingefrorenen Betrag erfassen"), filterbar nach Status. */
class FrozenBalanceListViewModel(
    private val repository: LedgerRepository,
    initialFilter: FrozenBalanceStatus? = null,
) : ViewModel() {

    private val filter = MutableStateFlow(initialFilter)

    val uiState: StateFlow<FrozenBalanceListUiState> = combine(
        repository.observeFrozenBalances(),
        filter,
    ) { all, currentFilter ->
        val filtered = (if (currentFilter == null) all else all.filter { it.status == currentFilter })
            .sortedByDescending { it.date }
        FrozenBalanceListUiState(currentFilter, filtered, filtered.sumOf { it.amountCents })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FrozenBalanceListUiState(initialFilter))

    fun setFilter(newFilter: FrozenBalanceStatus?) {
        filter.value = newFilter
    }
}
