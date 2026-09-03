package de.spardirekt.recipeveo

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.recipeveo.domain.CulinaryAgent
import de.spardirekt.recipeveo.domain.CulinaryPackage
import de.spardirekt.recipeveo.domain.StudioRules
import de.spardirekt.recipeveo.network.OpenAiCulinaryAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudioViewModel(app: RecipeVeoApp) : AndroidViewModel(app) {
    private val keyStore = app.apiKeyStore

    private val _dish = MutableStateFlow("")
    val dish: StateFlow<String> = _dish.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _result = MutableStateFlow<CulinaryPackage?>(null)
    val result: StateFlow<CulinaryPackage?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Key stored in EncryptedSharedPreferences — observed by Settings screen. */
    private val _apiKey = MutableStateFlow(keyStore.getKey() ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    val hasKey: Boolean get() = keyStore.hasKey()

    fun setDish(value: String) {
        if (value.length <= StudioRules.MAX_DISH) _dish.value = value
    }

    fun saveApiKey(key: String) {
        keyStore.setKey(key)
        _apiKey.value = key.trim()
    }

    fun clearApiKey() {
        keyStore.clearKey()
        _apiKey.value = ""
    }

    fun create() {
        if (_working.value) return
        viewModelScope.launch {
            _working.value = true
            _error.value = null
            val key = keyStore.getKey()
            val outcome = if (!key.isNullOrBlank()) {
                runCatching { OpenAiCulinaryAgent.create(_dish.value, key) }
            } else {
                runCatching { CulinaryAgent.create(_dish.value) }
            }
            _working.value = false
            outcome.fold(
                onSuccess = { _result.value = it },
                onFailure = { _error.value = it.message ?: "Не получилось создать." },
            )
        }
    }

    fun backToInput() {
        _result.value = null
    }

    fun consumeError() {
        _error.value = null
    }
}
