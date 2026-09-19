package de.tiktokshop.buchhaltung.ui.buchungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class BuchungFilter { ALLE, EINNAHMEN, AUSGABEN, FROZEN, AVAILABLE, PAID_OUT }

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
    /** Auszahlungsstatus der Einnahme (null bei Ausgaben) - für Verfügbar/Ausgezahlt-Filter. */
    val incomeStatus: IncomeStatus? = null,
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
        val all = buchungRows(incomes, expenses)
        val byFilter = applyBuchungFilter(all, currentFilter)
        val searched = applySearch(byFilter, currentQuery)

        BuchungenUiState(currentFilter, currentQuery, searched)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BuchungenUiState(initialFilter))

    fun setFilter(newFilter: BuchungFilter) {
        filter.value = newFilter
    }

    fun setQuery(newQuery: String) {
        query.value = newQuery
    }
}

/** Vereinheitlicht Einnahmen/Ausgaben zu [BuchungRow]s, neueste zuerst. */
internal fun buchungRows(incomes: List<IncomeEntry>, expenses: List<ExpenseEntry>): List<BuchungRow> {
    val incomeRows = incomes.map { income ->
        BuchungRow(
            id = income.id,
            date = income.date,
            description = income.payer ?: income.platform,
            category = income.incomeType,
            amountCents = income.amountCents,
            type = EntryType.INCOME,
            isFrozen = income.status == IncomeStatus.FROZEN,
            incomeStatus = income.status,
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
    return (incomeRows + expenseRows).sortedByDescending { it.date }
}

/**
 * Filtert die vereinheitlichten Buchungen für die vier Auszahlungsstatus-Zeilen im Dashboard
 * (Earned/Eingefroren/Verfügbar/Ausgezahlt): jede Zeile führt jetzt zu ihrer eigenen,
 * korrekt gefilterten Ansicht statt alle auf BuchungFilter.ALLE.
 */
internal fun applyBuchungFilter(rows: List<BuchungRow>, filter: BuchungFilter): List<BuchungRow> = when (filter) {
    BuchungFilter.ALLE -> rows
    BuchungFilter.EINNAHMEN -> rows.filter { it.type == EntryType.INCOME }
    BuchungFilter.AUSGABEN -> rows.filter { it.type == EntryType.EXPENSE }
    BuchungFilter.FROZEN -> rows.filter { it.isFrozen }
    BuchungFilter.AVAILABLE -> rows.filter { it.incomeStatus == IncomeStatus.AVAILABLE }
    BuchungFilter.PAID_OUT -> rows.filter { it.incomeStatus == IncomeStatus.PAID_OUT }
}

internal fun applySearch(rows: List<BuchungRow>, query: String): List<BuchungRow> = if (query.isBlank()) {
    rows
} else {
    rows.filter {
        it.description.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
    }
}
