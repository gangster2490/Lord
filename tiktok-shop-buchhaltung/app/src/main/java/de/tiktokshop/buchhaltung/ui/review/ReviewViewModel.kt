package de.tiktokshop.buchhaltung.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface ReviewItem {
    val id: String
    val warnings: List<String>

    data class Income(val entry: IncomeEntry, override val warnings: List<String>) : ReviewItem {
        override val id: String = entry.id
    }

    data class Expense(val entry: ExpenseEntry, override val warnings: List<String>) : ReviewItem {
        override val id: String = entry.id
    }
}

/** "Belege prüfen": listet alle Einträge mit Warnungen gemäß UX_FLOW.md. */
class ReviewViewModel(repository: LedgerRepository) : ViewModel() {

    val items: StateFlow<List<ReviewItem>> = combine(
        repository.observeIncomes(),
        repository.observeExpenses(),
    ) { incomes, expenses ->
        val incomeItems = incomes.map { entry ->
            ReviewItem.Income(entry, warningsFor(entry))
        }
        val expenseItems = expenses.map { entry ->
            ReviewItem.Expense(entry, warningsFor(entry))
        }
        (incomeItems + expenseItems)
            .filter { it.warnings.isNotEmpty() }
            .sortedByDescending { it.warnings.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun warningsFor(entry: IncomeEntry): List<String> = buildList {
        if (!entry.confirmed) add("OCR unsicher / nicht bestätigt")
        if (entry.receiptUris.isEmpty()) add("Kein Beleg")
        if ((entry.ocrConfidence ?: 1f) < 0.75f) add("Bitte prüfen")
    }

    private fun warningsFor(entry: ExpenseEntry): List<String> = buildList {
        if (!entry.confirmed) add("OCR unsicher / nicht bestätigt")
        if (entry.receiptUris.isEmpty()) add("Kein Beleg")
        if (entry.merchant.isNullOrBlank()) add("Händler fehlt")
        if ((entry.ocrConfidence ?: 1f) < 0.75f) add("Bitte prüfen")
    }
}
