package com.rob.veocreator.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rob.veocreator.VeoCreatorApp
import com.rob.veocreator.data.prefs.SecureApiKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionTestState { IDLE, TESTING, SUCCESS, FAILED }

data class SettingsUiState(
    val maskedKey: String? = null,
    val hasKey: Boolean = false,
    val connectionTestState: ConnectionTestState = ConnectionTestState.IDLE,
    val connectionMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<VeoCreatorApp>()
    private val apiKeyStore get() = app.apiKeyStore

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val key = apiKeyStore.getApiKey()
        _uiState.update {
            it.copy(
                maskedKey = key?.let { k -> SecureApiKeyStore.mask(k) },
                hasKey = !key.isNullOrBlank()
            )
        }
    }

    fun saveKey(rawKey: String) {
        if (rawKey.isBlank()) return
        apiKeyStore.saveApiKey(rawKey)
        refresh()
        _uiState.update { it.copy(connectionTestState = ConnectionTestState.IDLE, connectionMessage = null) }
    }

    fun deleteKey() {
        apiKeyStore.deleteApiKey()
        refresh()
        _uiState.update { it.copy(connectionTestState = ConnectionTestState.IDLE, connectionMessage = null) }
    }

    fun testConnection() {
        val key = apiKeyStore.getApiKey() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(connectionTestState = ConnectionTestState.TESTING, connectionMessage = null) }
            val result = app.veoClient.testConnection(key)
            result.onSuccess {
                _uiState.update {
                    it.copy(connectionTestState = ConnectionTestState.SUCCESS, connectionMessage = "Connection successful.")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(connectionTestState = ConnectionTestState.FAILED, connectionMessage = error.message ?: "Connection failed.")
                }
            }
        }
    }
}
