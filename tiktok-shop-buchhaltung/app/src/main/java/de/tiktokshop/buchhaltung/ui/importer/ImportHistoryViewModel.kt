package de.tiktokshop.buchhaltung.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.model.ImportBatch
import de.tiktokshop.buchhaltung.data.repository.ImportRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** "Import-Verlauf" (§8/§29): Liste aller bisherigen Excel-Imports. */
class ImportHistoryViewModel(repository: ImportRepository) : ViewModel() {
    val batches: StateFlow<List<ImportBatch>> = repository.observeImportHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
