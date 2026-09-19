package de.tiktokshop.buchhaltung.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tiktokshop.buchhaltung.export.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class BackupUiState(
    val lastBackupFile: File? = null,
    val restorePreview: BackupManager.RestorePreview? = null,
    val pendingRestoreUri: Uri? = null,
    val restoreDone: Boolean = false,
    val errorMessage: String? = null,
)

class BackupViewModel(private val backupManager: BackupManager) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun createBackup() {
        viewModelScope.launch {
            runCatching { backupManager.createBackup() }
                .onSuccess { file -> _state.value = _state.value.copy(lastBackupFile = file, errorMessage = null) }
                .onFailure { e -> _state.value = _state.value.copy(errorMessage = e.message) }
        }
    }

    fun selectBackupFileForRestore(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupManager.preview(uri) }
                .onSuccess { preview ->
                    _state.value = _state.value.copy(pendingRestoreUri = uri, restorePreview = preview, errorMessage = null)
                }
                .onFailure { e -> _state.value = _state.value.copy(errorMessage = e.message) }
        }
    }

    fun confirmRestore() {
        val uri = _state.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            runCatching { backupManager.restore(uri) }
                .onSuccess { _state.value = _state.value.copy(restoreDone = true, pendingRestoreUri = null, restorePreview = null) }
                .onFailure { e -> _state.value = _state.value.copy(errorMessage = e.message) }
        }
    }

    fun cancelRestore() {
        _state.value = _state.value.copy(pendingRestoreUri = null, restorePreview = null)
    }
}
