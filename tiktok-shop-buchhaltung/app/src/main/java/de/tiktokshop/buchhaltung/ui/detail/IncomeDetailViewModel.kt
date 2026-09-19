package de.tiktokshop.buchhaltung.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.model.StatusHistory
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class IncomeDetailUiState(
    val entry: IncomeEntry? = null,
    val history: List<StatusHistory> = emptyList(),
    val deleted: Boolean = false,
)

class IncomeDetailViewModel(private val repository: LedgerRepository, private val incomeId: String) : ViewModel() {

    val uiState: StateFlow<IncomeDetailUiState> = combine(
        repository.observeIncomes(),
        repository.observeStatusHistory(incomeId),
    ) { incomes, history ->
        IncomeDetailUiState(entry = incomes.firstOrNull { it.id == incomeId }, history = history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IncomeDetailUiState())

    fun updateStatus(newStatus: IncomeStatus) = updateEntry { it.copy(status = newStatus) }

    fun updatePayout(payoutDate: LocalDate?, paidOutAmountCents: Cents?) = updateEntry {
        it.copy(payoutDate = payoutDate, paidOutAmountCents = paidOutAmountCents)
    }

    fun updateNote(note: String) = updateEntry { it.copy(note = note.ifBlank { null }) }

    private fun updateEntry(transform: (IncomeEntry) -> IncomeEntry) {
        val previous = uiState.value.entry ?: return
        viewModelScope.launch { repository.updateIncome(previous, transform(previous)) }
    }

    fun delete() {
        val entry = uiState.value.entry ?: return
        viewModelScope.launch {
            repository.deleteIncome(entry)
        }
    }
}
