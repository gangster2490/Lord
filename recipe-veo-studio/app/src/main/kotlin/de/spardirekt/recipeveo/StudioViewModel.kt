package de.spardirekt.recipeveo

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.recipeveo.domain.CulinaryPackage
import de.spardirekt.recipeveo.domain.StudioRules
import de.spardirekt.recipeveo.network.OpenAiCulinaryAgent
import de.spardirekt.recipeveo.network.OpenAiException
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

    private val _apiKey = MutableStateFlow(keyStore.getKey().orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

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
            val key = keyStore.getKey()
            if (key.isNullOrBlank() || !key.trim().startsWith("sk-")) {
                _error.value = "Сначала вставьте ключ OpenAI в настройках."
                return@launch
            }
            _working.value = true
            _error.value = null
            val outcome = runCatching { OpenAiCulinaryAgent.create(_dish.value, key) }
            _working.value = false
            outcome.fold(
                onSuccess = { _result.value = it },
                onFailure = { _error.value = human(it) },
            )
        }
    }

    fun backToInput() {
        _result.value = null
    }

    fun consumeError() {
        _error.value = null
    }

    private fun human(error: Throwable): String {
        val cause = generateSequence(error) { it.cause }.firstOrNull { it is OpenAiException } ?: error
        val message = cause.message?.trim().orEmpty()
        return when {
            message.isNotBlank() && !message.contains("JSONObject") && !message.contains("org.json") -> message
            else -> "Не получилось создать. Проверьте ключ и сеть, затем нажмите «Создать» ещё раз."
        }
    }
}
