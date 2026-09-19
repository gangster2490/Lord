package de.tiktokshop.buchhaltung.ui.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import de.tiktokshop.buchhaltung.export.ExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExportUiState(
    val from: LocalDate = LocalDate.now().withDayOfYear(1),
    val to: LocalDate = LocalDate.now(),
    val lastExportUri: Uri? = null,
)

class ExportViewModel(private val repository: LedgerRepository, private val exportManager: ExportManager) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun setRange(from: LocalDate, to: LocalDate) {
        _state.value = _state.value.copy(from = from, to = to)
    }

    fun exportCsv() = export { incomes, expenses -> exportManager.exportCsv(incomes, expenses, _state.value.from, _state.value.to) }

    fun exportTikTokCsv() = export { incomes, _ -> exportManager.exportTikTokCsv(incomes, _state.value.from, _state.value.to) }

    fun exportZip() = export { incomes, expenses -> exportManager.exportZipWithReceipts(incomes, expenses, _state.value.from, _state.value.to) }

    private fun export(action: (incomes: List<de.tiktokshop.buchhaltung.data.model.IncomeEntry>, expenses: List<de.tiktokshop.buchhaltung.data.model.ExpenseEntry>) -> Uri) {
        viewModelScope.launch {
            val incomes = repository.getAllIncomes()
            val expenses = repository.getAllExpenses()
            val uri = action(incomes, expenses)
            _state.value = _state.value.copy(lastExportUri = uri)
        }
    }
}
