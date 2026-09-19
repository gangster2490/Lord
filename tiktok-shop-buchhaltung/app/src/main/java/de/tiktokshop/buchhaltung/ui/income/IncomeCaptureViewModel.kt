package de.tiktokshop.buchhaltung.ui.income

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.model.formatGerman
import de.tiktokshop.buchhaltung.data.receipts.ReceiptStorage
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.domain.DuplicateDetector
import de.tiktokshop.buchhaltung.ocr.AmountParser
import de.tiktokshop.buchhaltung.ocr.IncomeOcrExtractor
import de.tiktokshop.buchhaltung.ocr.TextRecognizerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class IncomeCaptureState(
    val imageUri: Uri? = null,
    val isRecognizing: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val platform: String = "TikTok Shop",
    val incomeType: String = "Provision",
    val amountCents: Cents? = null,
    val amountText: String = "",
    val status: IncomeStatus = IncomeStatus.ACCRUED,
    val note: String = "",
    val rawText: String = "",
    val needsReview: Boolean = false,
    val duplicateWarning: String? = null,
    val saved: Boolean = false,
)

class IncomeCaptureViewModel(
    private val repository: LedgerRepository,
    private val receiptStorage: ReceiptStorage,
    private val textRecognizer: TextRecognizerEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(IncomeCaptureState())
    val state: StateFlow<IncomeCaptureState> = _state.asStateFlow()

    fun onImageSelected(uri: Uri) {
        _state.value = _state.value.copy(imageUri = uri, isRecognizing = true)
        viewModelScope.launch {
            val text = runCatching { textRecognizer.recognize(uri) }.getOrDefault("")
            val draft = IncomeOcrExtractor.extract(text)
            _state.value = _state.value.copy(
                isRecognizing = false,
                date = draft.date ?: LocalDate.now(),
                amountCents = draft.amountCents,
                amountText = draft.amountCents?.formatGerman() ?: "",
                status = draft.status,
                rawText = draft.rawText,
                needsReview = draft.needsReview,
            )
        }
    }

    fun updateAmountText(text: String) {
        _state.value = _state.value.copy(amountText = text)
    }

    fun updateDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
    }

    fun updateStatus(status: IncomeStatus) {
        _state.value = _state.value.copy(status = status)
    }

    fun updatePlatform(platform: String) {
        _state.value = _state.value.copy(platform = platform)
    }

    fun updateNote(note: String) {
        _state.value = _state.value.copy(note = note)
    }

    /** Speichert erst nach expliziter Bestätigung durch den Nutzer (OCR_RULES.md: kein stillschweigendes Speichern). */
    fun confirmAndSave() {
        val current = _state.value
        val amount = runCatching {
            AmountParser.parseSingleAmountToCents(current.amountText)
        }.getOrNull()
        if (amount == null) {
            _state.value = current.copy(duplicateWarning = "Bitte einen gültigen Betrag eingeben.")
            return
        }

        viewModelScope.launch {
            val existing = repository.getAllIncomes()
            val duplicate = DuplicateDetector.findIncomeDuplicate(
                date = current.date,
                amountCents = amount,
                platform = current.platform,
                imageHash = null,
                existing = existing,
            )
            if (duplicate != null && current.duplicateWarning == null) {
                _state.value = current.copy(
                    duplicateWarning = "Möglicherweise bereits erfasst (gleiches Datum/Betrag/Plattform). Erneut bestätigen zum Speichern.",
                )
                return@launch
            }

            val receiptPath = current.imageUri?.let { receiptStorage.persistReceipt(it, current.date) }
            val imageHash = receiptPath?.let { receiptStorage.computeSha256(it) }

            repository.saveNewIncome(
                IncomeEntry(
                    date = current.date,
                    platform = current.platform,
                    incomeType = current.incomeType,
                    amountCents = amount,
                    status = current.status,
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
