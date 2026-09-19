package de.tiktokshop.buchhaltung.ui.frozen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatusHistory
import de.tiktokshop.buchhaltung.data.receipts.ReceiptStorage
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.ocr.AmountParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FrozenBalanceDetailUiState(
    val entry: FrozenBalanceEntry? = null,
    val history: List<FrozenBalanceStatusHistory> = emptyList(),
)

class FrozenBalanceDetailViewModel(
    private val repository: LedgerRepository,
    private val receiptStorage: ReceiptStorage,
    private val entryId: String,
) : ViewModel() {

    val uiState: StateFlow<FrozenBalanceDetailUiState> = combine(
        repository.observeFrozenBalances(),
        repository.observeFrozenBalanceHistory(entryId),
    ) { all, history ->
        FrozenBalanceDetailUiState(entry = all.firstOrNull { it.id == entryId }, history = history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FrozenBalanceDetailUiState())

    fun updateStatus(newStatus: FrozenBalanceStatus) = updateEntry { it.copy(status = newStatus) }

    fun updateDate(date: LocalDate) = updateEntry { it.copy(date = date) }

    fun updateAmountText(text: String) {
        val amount = runCatching { AmountParser.parseSingleAmountToCents(text) }.getOrNull() ?: return
        updateEntry { it.copy(amountCents = amount) }
    }

    fun updateCurrency(currency: String) = updateEntry { it.copy(currency = currency.ifBlank { "EUR" }) }

    fun updatePlatform(platform: String) = updateEntry { it.copy(platform = platform.ifBlank { "TikTok Shop" }) }

    fun updateNote(note: String) = updateEntry { it.copy(note = note.ifBlank { null }) }

    fun updatePeriodReference(value: String) = updateEntry { it.copy(periodReference = value.ifBlank { null }) }

    fun attachScreenshot(uri: Uri) {
        val previous = uiState.value.entry ?: return
        viewModelScope.launch {
            val path = receiptStorage.persistReceipt(uri, previous.date)
            repository.updateFrozenBalance(previous, previous.copy(receiptUris = previous.receiptUris + path))
        }
    }

    private fun updateEntry(transform: (FrozenBalanceEntry) -> FrozenBalanceEntry) {
        val previous = uiState.value.entry ?: return
        viewModelScope.launch { repository.updateFrozenBalance(previous, transform(previous)) }
    }

    fun delete() {
        val entry = uiState.value.entry ?: return
        viewModelScope.launch { repository.deleteFrozenBalance(entry) }
    }
}
