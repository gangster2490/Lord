package de.tiktokshop.buchhaltung.ui.expense

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.formatGerman
import de.tiktokshop.buchhaltung.data.receipts.ReceiptStorage
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.domain.DuplicateDetector
import de.tiktokshop.buchhaltung.ocr.AmountParser
import de.tiktokshop.buchhaltung.ocr.ExpenseOcrExtractor
import de.tiktokshop.buchhaltung.ocr.TextRecognizerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpenseCaptureState(
    val imageUri: Uri? = null,
    val isRecognizing: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val merchant: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val grossAmountText: String = "",
    val businessUsePercent: Int = 100,
    val note: String = "",
    val rawText: String = "",
    val needsReview: Boolean = false,
    val duplicateWarning: String? = null,
    val saved: Boolean = false,
)

class ExpenseCaptureViewModel(
    private val repository: LedgerRepository,
    private val receiptStorage: ReceiptStorage,
    private val textRecognizer: TextRecognizerEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseCaptureState())
    val state: StateFlow<ExpenseCaptureState> = _state.asStateFlow()

    fun onImageSelected(uri: Uri) {
        _state.value = _state.value.copy(imageUri = uri, isRecognizing = true)
        viewModelScope.launch {
            val text = runCatching { textRecognizer.recognize(uri) }.getOrDefault("")
            val draft = ExpenseOcrExtractor.extract(text)
            _state.value = _state.value.copy(
                isRecognizing = false,
                date = draft.date ?: LocalDate.now(),
                merchant = draft.merchant ?: "",
                category = draft.suggestedCategory ?: ExpenseCategory.OTHER,
                grossAmountText = draft.grossAmountCents?.formatGerman() ?: "",
                rawText = draft.rawText,
                needsReview = draft.needsReview,
            )
        }
    }

    fun updateMerchant(value: String) { _state.value = _state.value.copy(merchant = value) }
    fun updateCategory(value: ExpenseCategory) { _state.value = _state.value.copy(category = value) }
    fun updateAmountText(value: String) { _state.value = _state.value.copy(grossAmountText = value) }
    fun updateBusinessUsePercent(value: Int) { _state.value = _state.value.copy(businessUsePercent = value.coerceIn(0, 100)) }
    fun updateNote(value: String) { _state.value = _state.value.copy(note = value) }
    fun updateDate(value: LocalDate) { _state.value = _state.value.copy(date = value) }

    fun confirmAndSave() {
        val current = _state.value
        val grossAmount = runCatching {
            AmountParser.parseSingleAmountToCents(current.grossAmountText)
        }.getOrNull()
        if (grossAmount == null) {
            _state.value = current.copy(duplicateWarning = "Bitte einen gültigen Betrag eingeben.")
            return
        }

        viewModelScope.launch {
            val existing = repository.getAllExpenses()
            val duplicate = DuplicateDetector.findExpenseDuplicate(
                date = current.date,
                grossAmountCents = grossAmount,
                merchant = current.merchant.ifBlank { null },
                imageHash = null,
                existing = existing,
            )
            if (duplicate != null && current.duplicateWarning == null) {
                _state.value = current.copy(
                    duplicateWarning = "Möglicherweise bereits erfasst (gleiches Datum/Betrag/Händler). Erneut bestätigen zum Speichern.",
                )
                return@launch
            }

            val receiptPath = current.imageUri?.let { receiptStorage.persistReceipt(it, current.date) }
            val imageHash = receiptPath?.let { receiptStorage.computeSha256(it) }
            val businessAmount: Cents = ExpenseEntry.computeBusinessAmountCents(grossAmount, current.businessUsePercent)

            repository.saveNewExpense(
                ExpenseEntry(
                    date = current.date,
                    merchant = current.merchant.ifBlank { null },
                    category = current.category,
                    grossAmountCents = grossAmount,
                    businessUsePercent = current.businessUsePercent,
                    businessAmountCents = businessAmount,
                    note = current.note.ifBlank { null },
                    receiptUris = listOfNotNull(receiptPath),
                    ocrRawText = current.rawText.ifBlank { null },
                    confirmed = true,
                    imageHash = imageHash,
                ),
            )
            _state.value = current.copy(saved = true)
        }
    }
}
