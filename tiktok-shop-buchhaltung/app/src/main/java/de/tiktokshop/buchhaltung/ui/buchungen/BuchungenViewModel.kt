package de.tiktokshop.buchhaltung.ui.buchungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class BuchungFilter { ALLE, EINNAHMEN, AUSGABEN, FROZEN }

/** Eine Zeile in der "Buchungen"-Liste (§15) - vereinheitlicht Einnahme/Ausgabe für Anzeige/Suche. */
data class BuchungRow(
    val id: String,
    val date: LocalDate,
    val description: String,
    val category: String,
    /** Vorzeichenbehaftet: positiv = Einnahme, negativ = Ausgabe. */
    val amountCents: Cents,
    val type: EntryType,
    val isFrozen: Boolean,
)

data class BuchungenUiState(
    val filter: BuchungFilter = BuchungFilter.ALLE,
    val query: String = "",
    val rows: List<BuchungRow> = emptyList(),
)

/**
 * "Buchungen" (§15): eine gemeinsame, durchsuchbare Liste aller Einnahmen und Ausgaben mit
 * Filter-Chips (Alle/Einnahmen/Ausgaben/Frozen) statt getrennter Listen.
 */
class BuchungenViewModel(
    private val repository: LedgerRepository,
    initialFilter: BuchungFilter = BuchungFilter.ALLE,
) : ViewModel() {

    private val filter = MutableStateFlow(initialFilter)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<BuchungenUiState> = combine(
        repository.observeIncomes(),
        repository.observeExpenses(),
        filter,
        query,
    ) { incomes, expenses, currentFilter, currentQuery ->
        val incomeRows = incomes.map { income ->
            BuchungRow(
                id = income.id,
                date = income.date,
                description = income.payer ?: income.platform,
                category = income.incomeType,
                amountCents = income.amountCents,
                type = EntryType.INCOME,
                isFrozen = income.status == IncomeStatus.FROZEN,
            )
        }
        val expenseRows = expenses.map { expense ->
            BuchungRow(
                id = expense.id,
                date = expense.date,
                description = expense.merchant ?: "Ausgabe",
                category = expense.category.label,
                amountCents = -expense.grossAmountCents,
                type = EntryType.EXPENSE,
                isFrozen = false,
            )
        }
        val all = (incomeRows + expenseRows).sortedByDescending { it.date }

        val byFilter = when (currentFilter) {
            BuchungFilter.ALLE -> all
            BuchungFilter.EINNAHMEN -> all.filter { it.type == EntryType.INCOME }
            BuchungFilter.AUSGABEN -> all.filter { it.type == EntryType.EXPENSE }
            BuchungFilter.FROZEN -> all.filter { it.isFrozen }
        }
        val searched = if (currentQuery.isBlank()) {
            byFilter
        } else {
            byFilter.filter {
                it.description.contains(currentQuery, ignoreCase = true) || it.category.contains(currentQuery, ignoreCase = true)
            }
        }

        BuchungenUiState(currentFilter, currentQuery, searched)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BuchungenUiState(initialFilter))

    fun setFilter(newFilter: BuchungFilter) {
        filter.value = newFilter
    }

    fun setQuery(newQuery: String) {
        query.value = newQuery
    }
}
