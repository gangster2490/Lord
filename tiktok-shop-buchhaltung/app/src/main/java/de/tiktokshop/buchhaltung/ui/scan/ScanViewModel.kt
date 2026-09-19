package de.tiktokshop.buchhaltung.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.receipts.ReceiptStorage
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.data.repository.RuleLearningRepository
import de.tiktokshop.buchhaltung.scan.MultiTransactionExtractor
import de.tiktokshop.buchhaltung.scan.TransactionCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ScanUiState(
    val imageUri: Uri? = null,
    val isLoading: Boolean = false,
    /** true, sobald mindestens ein Erkennungsdurchlauf abgeschlossen ist - auch mit 0 Treffern. */
    val hasResult: Boolean = false,
    val candidates: List<CandidateUiState> = emptyList(),
    val usedAiFallback: Boolean = false,
    val warning: String? = null,
    val isSaving: Boolean = false,
    val savedCount: Int? = null,
) {
    val selectedCount: Int get() = candidates.count { it.selected }
}

data class CandidateUiState(
    val candidate: TransactionCandidate,
    val selected: Boolean = true,
)

/**
 * Hält den Zustand eines Scans über zwei Screens hinweg (Scan -> Review), damit die
 * erkannten Kandidaten nicht über die Nav-Route serialisiert werden müssen. Scoped auf den
 * "scan"-Nav-Subgraphen (siehe AppNavHost), verschwindet also automatisch bei Abbruch/Fertig.
 */
class ScanViewModel(
    private val extractor: MultiTransactionExtractor,
    private val repository: LedgerRepository,
    private val receiptStorage: ReceiptStorage,
    private val ruleLearningRepository: RuleLearningRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onImageSelected(uri: Uri) {
        _state.value = ScanUiState(imageUri = uri, isLoading = true)
        runExtraction(uri)
    }

    fun reRecognize() {
        val uri = _state.value.imageUri ?: return
        _state.value = _state.value.copy(isLoading = true, hasResult = false, candidates = emptyList(), warning = null)
        runExtraction(uri)
    }

    private fun runExtraction(uri: Uri) {
        viewModelScope.launch {
            val result = extractor.extract(uri)
            _state.value = _state.value.copy(
                isLoading = false,
                hasResult = true,
                candidates = result.candidates.map { CandidateUiState(it, selected = true) },
                usedAiFallback = result.usedAiFallback,
                warning = result.warning,
            )
        }
    }

    fun toggleSelected(id: String) {
        updateList { list -> list.map { if (it.candidate.id == id) it.copy(selected = !it.selected) else it } }
    }

    fun selectAll(select: Boolean) {
        updateList { list -> list.map { it.copy(selected = select) } }
    }

    fun updateCandidate(id: String, transform: (TransactionCandidate) -> TransactionCandidate) {
        updateList { list -> list.map { if (it.candidate.id == id) it.copy(candidate = transform(it.candidate)) else it } }
    }

    fun updateDate(id: String, date: LocalDate) = updateCandidate(id) { it.copy(date = date) }
    fun updateMerchant(id: String, merchant: String) = updateCandidate(id) { it.copy(merchant = merchant) }
    fun updateAmountCents(id: String, amountCents: Cents?) = updateCandidate(id) { it.copy(amountCents = amountCents) }
    fun updateCurrency(id: String, currency: String) = updateCandidate(id) { it.copy(currency = currency) }
    fun updateCategory(id: String, category: ExpenseCategory) = updateCandidate(id) { it.copy(category = category) }
    fun updateIncomeType(id: String, incomeType: String) = updateCandidate(id) { it.copy(incomeType = incomeType) }
    fun updateStatus(id: String, status: IncomeStatus) = updateCandidate(id) { it.copy(status = status) }
    fun updateBusinessUsePercent(id: String, percent: Int) =
        updateCandidate(id) { it.copy(businessUsePercent = percent.coerceIn(0, 100)) }

    /** Manuelle Korrektur, falls die Einnahme/Ausgabe-Heuristik danebenliegt. */
    fun updateEntryType(id: String, entryType: EntryType) = updateCandidate(id) { it.copy(entryType = entryType) }

    private inline fun updateList(transform: (List<CandidateUiState>) -> List<CandidateUiState>) {
        _state.value = _state.value.copy(candidates = transform(_state.value.candidates))
    }

    /**
     * Speichert alle ausgewählten Kandidaten als neue Buchungen. Der Originalbeleg (ein
     * Screenshot) wird nur einmal persistiert und mit allen daraus erzeugten Buchungen
     * verknüpft. Jede final bestätigte Händler-Zuordnung wird als Regel gelernt
     * (RuleLearningRepository), damit sie beim nächsten Scan automatisch vorgeschlagen wird.
     */
    fun saveSelected() {
        val current = _state.value
        val imageUri = current.imageUri
        val selected = current.candidates.filter { it.selected }
        if (selected.isEmpty()) return

        _state.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val receiptPath = imageUri?.let { uri ->
                val date = selected.firstOrNull()?.candidate?.date ?: LocalDate.now()
                receiptStorage.persistReceipt(uri, date)
            }
            val imageHash = receiptPath?.let { receiptStorage.computeSha256(it) }

            selected.forEach { item ->
                val candidate = item.candidate
                val date = candidate.date ?: LocalDate.now()
                val amount = candidate.amountCents ?: 0L

                when (candidate.entryType) {
                    EntryType.INCOME -> {
                        repository.saveNewIncome(
                            IncomeEntry(
                                date = date,
                                platform = candidate.merchant ?: "TikTok Shop",
                                incomeType = candidate.incomeType ?: "Provision",
                                amountCents = amount,
                                currency = candidate.currency,
                                status = candidate.status ?: IncomeStatus.ACCRUED,
                                note = null,
                                receiptUris = listOfNotNull(receiptPath),
                                ocrRawText = candidate.rawText.ifBlank { null },
                                confirmed = true,
                                imageHash = imageHash,
                            ),
                        )
                        ruleLearningRepository.learnIncome(candidate.merchant, candidate.incomeType, candidate.businessUsePercent)
                    }
                    EntryType.EXPENSE -> {
                        val category = candidate.category ?: ExpenseCategory.OTHER
                        repository.saveNewExpense(
                            ExpenseEntry(
                                date = date,
                                merchant = candidate.merchant,
                                category = category,
                                grossAmountCents = amount,
                                currency = candidate.currency,
                                businessUsePercent = candidate.businessUsePercent,
                                businessAmountCents = ExpenseEntry.computeBusinessAmountCents(amount, candidate.businessUsePercent),
                                receiptUris = listOfNotNull(receiptPath),
                                ocrRawText = candidate.rawText.ifBlank { null },
                                confirmed = true,
                                imageHash = imageHash,
                            ),
                        )
                        ruleLearningRepository.learnExpense(candidate.merchant, category, candidate.businessUsePercent)
                    }
                }
            }

            _state.value = _state.value.copy(isSaving = false, savedCount = selected.size)
        }
    }
}
