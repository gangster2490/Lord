package de.tiktokshop.buchhaltung.ui.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.importer.ExcelImportPreview
import de.tiktokshop.buchhaltung.data.importer.ImportException
import de.tiktokshop.buchhaltung.data.repository.ImportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class FileImportState(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val filename: String,
    val isLoading: Boolean = true,
    val preview: ExcelImportPreview? = null,
    val error: String? = null,
    val committed: Boolean = false,
)

data class ImportExcelUiState(
    val files: List<FileImportState> = emptyList(),
    val isCommitting: Boolean = false,
) {
    val hasAnyImportable: Boolean get() = files.any { it.preview != null && it.error == null && !it.committed }
    val allDone: Boolean get() = files.isNotEmpty() && files.all { it.committed || it.error != null }
}

/**
 * Steuert "TikTok Excel importieren" (§7-9): mehrere Dateien gleichzeitig wählbar, pro Datei
 * erst eine Vorschau (X neu / X bereits vorhanden / Gesamteinnahmen / Zeitraum), gespeichert
 * wird erst nach expliziter Bestätigung.
 */
class ImportExcelViewModel(private val repository: ImportRepository) : ViewModel() {

    private val _state = MutableStateFlow(ImportExcelUiState())
    val state: StateFlow<ImportExcelUiState> = _state.asStateFlow()

    fun onFilesSelected(context: Context, uris: List<Uri>) {
        val newFiles = uris.map { uri -> FileImportState(uri = uri, filename = resolveFilename(context, uri)) }
        _state.value = _state.value.copy(files = _state.value.files + newFiles)
        newFiles.forEach { loadPreview(it.id, it.uri) }
    }

    private fun loadPreview(fileId: String, uri: Uri) {
        viewModelScope.launch {
            val filename = requireNotNull(_state.value.files.firstOrNull { it.id == fileId }).filename
            runCatching { repository.preview(uri, filename) }
                .onSuccess { preview -> updateFile(fileId) { it.copy(isLoading = false, preview = preview) } }
                .onFailure { error ->
                    val message = (error as? ImportException)?.message ?: "Unbekannter Fehler: ${error.message}"
                    updateFile(fileId) { it.copy(isLoading = false, error = message) }
                }
        }
    }

    fun confirmAll() {
        val current = _state.value
        val toCommit = current.files.filter { it.preview != null && it.error == null && !it.committed }
        if (toCommit.isEmpty()) return

        _state.value = current.copy(isCommitting = true)
        viewModelScope.launch {
            toCommit.forEach { file ->
                val preview = file.preview ?: return@forEach
                runCatching { repository.commit(preview) }
                    .onSuccess { updateFile(file.id) { it.copy(committed = true) } }
                    .onFailure { error -> updateFile(file.id) { it.copy(error = "Speichern fehlgeschlagen: ${error.message}") } }
            }
            _state.value = _state.value.copy(isCommitting = false)
        }
    }

    fun removeFile(fileId: String) {
        _state.value = _state.value.copy(files = _state.value.files.filterNot { it.id == fileId })
    }

    private fun updateFile(fileId: String, transform: (FileImportState) -> FileImportState) {
        _state.value = _state.value.copy(
            files = _state.value.files.map { if (it.id == fileId) transform(it) else it },
        )
    }

    private fun resolveFilename(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "unbekannte_datei.xlsx"
    }
}
