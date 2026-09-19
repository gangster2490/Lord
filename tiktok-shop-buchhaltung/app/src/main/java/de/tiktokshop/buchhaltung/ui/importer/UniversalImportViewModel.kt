package de.tiktokshop.buchhaltung.ui.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.data.importer.ColumnRole
import de.tiktokshop.buchhaltung.data.importer.ImportCandidateRow
import de.tiktokshop.buchhaltung.data.importer.ImportException
import de.tiktokshop.buchhaltung.data.importer.UniversalImportPreview
import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.repository.ImportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Eindeutiger Schlüssel für eine [ImportCandidateRow] innerhalb einer Import-Vorschau (für "Auswahl bearbeiten", §5). */
fun importRowKey(row: ImportCandidateRow): String = "${row.sourceSheet}:${row.sourceRowNumber}"

enum class ImportStep { PICK, LOADING, COLUMN_MAPPING, PREVIEW, DONE, ERROR }

data class UniversalImportUiState(
    val step: ImportStep = ImportStep.PICK,
    val filename: String? = null,
    val preview: UniversalImportPreview? = null,
    /** Zeilen, die der Nutzer im "Auswahl bearbeiten"-Modus abgewählt hat (§5). */
    val excludedRowKeys: Set<String> = emptySet(),
    val error: String? = null,
    val isCommitting: Boolean = false,
    val committedCount: Int = 0,
    val committedTotalCents: Cents = 0L,
)

/**
 * Steuert den universellen Import-Flow "Datei / Beleg importieren" -> Excel/CSV (§1-6): Datei
 * wählen -> analysieren -> je nach Ergebnis "Spalten zuordnen" (§4, wenn unsicher) ODER direkt
 * "Import prüfen" (§5) -> Bestätigen. Es wird NIE automatisch gespeichert.
 */
class UniversalImportViewModel(private val repository: ImportRepository) : ViewModel() {

    private val _state = MutableStateFlow(UniversalImportUiState())
    val state: StateFlow<UniversalImportUiState> = _state.asStateFlow()

    fun onFileSelected(context: Context, uri: Uri) {
        val filename = resolveFilename(context, uri)
        _state.value = UniversalImportUiState(step = ImportStep.LOADING, filename = filename)
        viewModelScope.launch {
            runCatching { repository.previewUniversal(uri, filename) }
                .onSuccess { preview ->
                    _state.value = _state.value.copy(
                        step = if (preview.needsColumnMapping) ImportStep.COLUMN_MAPPING else ImportStep.PREVIEW,
                        preview = preview,
                    )
                }
                .onFailure { error -> _state.value = _state.value.copy(step = ImportStep.ERROR, error = errorMessage(error)) }
        }
    }

    fun applyColumnMapping(sheetName: String, mapping: Map<ColumnRole, Int>) {
        val preview = _state.value.preview ?: return
        viewModelScope.launch {
            runCatching { repository.applyColumnMapping(preview, sheetName, mapping) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        step = if (updated.newRows.isEmpty() && updated.duplicateRows.isEmpty()) {
                            ImportStep.COLUMN_MAPPING
                        } else {
                            ImportStep.PREVIEW
                        },
                        preview = updated,
                    )
                }
                .onFailure { error -> _state.value = _state.value.copy(step = ImportStep.ERROR, error = errorMessage(error)) }
        }
    }

    /**
     * Flippt den Typ aller Zeilen, deren Einnahme/Ausgabe-Erkennung nur aus dem Vorzeichen
     * einer generischen "Betrag"-Spalte geraten wurde (§3: Nutzer kann überschreiben) - z. B.
     * wenn ein ganz normales Excel mit ausschließlich Einnahmen (alle Beträge positiv, kein
     * Minuszeichen) fälschlich als Ausgaben erkannt wurde.
     */
    fun setAmbiguousRowsType(newType: EntryType) {
        val preview = _state.value.preview ?: return
        viewModelScope.launch {
            runCatching { repository.setAmbiguousRowsType(preview, newType) }
                .onSuccess { updated -> _state.value = _state.value.copy(preview = updated) }
                .onFailure { error -> _state.value = _state.value.copy(step = ImportStep.ERROR, error = errorMessage(error)) }
        }
    }

    fun toggleRowExcluded(rowKey: String) {
        val current = _state.value.excludedRowKeys
        _state.value = _state.value.copy(
            excludedRowKeys = if (rowKey in current) current - rowKey else current + rowKey,
        )
    }

    fun confirmImport() {
        val preview = _state.value.preview ?: return
        val excluded = _state.value.excludedRowKeys
        val toCommit = if (excluded.isEmpty()) {
            preview
        } else {
            preview.copy(newRows = preview.newRows.filterNot { importRowKey(it) in excluded })
        }

        _state.value = _state.value.copy(isCommitting = true)
        viewModelScope.launch {
            runCatching { repository.commitUniversal(toCommit) }
                .onSuccess { batch ->
                    _state.value = _state.value.copy(
                        step = ImportStep.DONE,
                        isCommitting = false,
                        committedCount = batch.newTransactionCount,
                        committedTotalCents = batch.totalIncomeCents + batch.totalExpenseCents,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isCommitting = false, step = ImportStep.ERROR, error = errorMessage(error))
                }
        }
    }

    fun reset() {
        _state.value = UniversalImportUiState()
    }

    private fun errorMessage(error: Throwable): String =
        (error as? ImportException)?.message ?: "Unbekannter Fehler: ${error.message}"

    private fun resolveFilename(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "unbekannte_datei"
    }
}
