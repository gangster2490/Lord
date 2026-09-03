package de.spardirekt.recipeveo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.recipeveo.domain.CulinaryAgent
import de.spardirekt.recipeveo.domain.CulinaryPackage
import de.spardirekt.recipeveo.domain.StudioRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioViewModel : ViewModel() {
    private val _dish = MutableStateFlow("")
    val dish: StateFlow<String> = _dish.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _result = MutableStateFlow<CulinaryPackage?>(null)
    val result: StateFlow<CulinaryPackage?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setDish(value: String) {
        if (value.length <= StudioRules.MAX_DISH) _dish.value = value
    }

    fun create() {
        if (_working.value) return
        viewModelScope.launch {
            _working.value = true
            _error.value = null
            delay(700)
            val outcome = withContext(Dispatchers.Default) {
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
