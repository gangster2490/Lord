package de.tiktokshop.buchhaltung.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseDetailViewModel(private val repository: LedgerRepository, private val expenseId: String) : ViewModel() {

    val entry: StateFlow<ExpenseEntry?> = repository.observeExpenses()
        .map { list -> list.firstOrNull { it.id == expenseId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateBusinessUsePercent(percent: Int) = updateEntry {
        it.copy(
            businessUsePercent = percent,
            businessAmountCents = ExpenseEntry.computeBusinessAmountCents(it.grossAmountCents, percent),
        )
    }

    fun updateNote(note: String) = updateEntry { it.copy(note = note.ifBlank { null }) }

    private fun updateEntry(transform: (ExpenseEntry) -> ExpenseEntry) {
        val current = entry.value ?: return
        viewModelScope.launch { repository.updateExpense(transform(current)) }
    }

    fun delete() {
        val current = entry.value ?: return
        viewModelScope.launch { repository.deleteExpense(current) }
    }
}
