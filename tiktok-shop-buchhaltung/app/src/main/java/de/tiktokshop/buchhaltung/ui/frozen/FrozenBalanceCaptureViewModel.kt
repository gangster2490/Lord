package de.tiktokshop.buchhaltung.ui.frozen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.data.receipts.ReceiptStorage
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.ocr.AmountParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FrozenBalanceCaptureState(
    val imageUri: Uri? = null,
    val date: LocalDate = LocalDate.now(),
    val amountText: String = "",
    val currency: String = "EUR",
    val platform: String = "TikTok Shop",
    val status: FrozenBalanceStatus = FrozenBalanceStatus.FROZEN,
    val note: String = "",
    val periodReference: String = "",
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

/**
 * Erfasst einen separaten Auszahlungsstatus-Eintrag ("Eingefrorenen Betrag erfassen").
 * WICHTIG: speichert NIE in income_entries - der Betrag ist bereits Teil einer bestehenden
 * Einnahme (Earned) und wird hier nur als aktuell zurückgehalten/verfügbar/ausgezahlt
 * klassifiziert (siehe [FrozenBalanceEntry]-Kommentar).
 */
class FrozenBalanceCaptureViewModel(
    private val repository: LedgerRepository,
    private val receiptStorage: ReceiptStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(FrozenBalanceCaptureState())
    val state: StateFlow<FrozenBalanceCaptureState> = _state.asStateFlow()

    fun onImageSelected(uri: Uri) {
        _state.value = _state.value.copy(imageUri = uri)
    }

    fun updateDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
    }

    fun updateAmountText(text: String) {
        _state.value = _state.value.copy(amountText = text)
    }

    fun updateCurrency(currency: String) {
        _state.value = _state.value.copy(currency = currency)
    }

    fun updatePlatform(platform: String) {
        _state.value = _state.value.copy(platform = platform)
    }

    fun updateStatus(status: FrozenBalanceStatus) {
        _state.value = _state.value.copy(status = status)
    }

    fun updateNote(note: String) {
        _state.value = _state.value.copy(note = note)
    }

    fun updatePeriodReference(value: String) {
        _state.value = _state.value.copy(periodReference = value)
    }

    fun confirmAndSave() {
        val current = _state.value
        val amount = runCatching { AmountParser.parseSingleAmountToCents(current.amountText) }.getOrNull()
        if (amount == null) {
            _state.value = current.copy(errorMessage = "Bitte einen gültigen Betrag eingeben.")
            return
        }

        viewModelScope.launch {
            val receiptPath = current.imageUri?.let { receiptStorage.persistReceipt(it, current.date) }
            repository.saveNewFrozenBalance(
                FrozenBalanceEntry(
                    date = current.date,
                    amountCents = amount,
                    currency = current.currency.ifBlank { "EUR" },
                    platform = current.platform.ifBlank { "TikTok Shop" },
                    status = current.status,
                    note = current.note.ifBlank { null },
                    receiptUris = listOfNotNull(receiptPath),
                    periodReference = current.periodReference.ifBlank { null },
                ),
            )
            _state.value = current.copy(saved = true)
        }
    }
}
