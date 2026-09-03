package de.spardirekt.recipeveo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.ui.HomeScreen
import de.spardirekt.recipeveo.ui.RecipeTheme
import de.spardirekt.recipeveo.ui.ResultScreen
import de.spardirekt.recipeveo.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: StudioViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StudioViewModel(application as RecipeVeoApp) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecipeTheme {
                val dish by vm.dish.collectAsStateWithLifecycle()
                val working by vm.working.collectAsStateWithLifecycle()
                val result by vm.result.collectAsStateWithLifecycle()
                val error by vm.error.collectAsStateWithLifecycle()
                val apiKey by vm.apiKey.collectAsStateWithLifecycle()
                val hasKey = apiKey.trim().startsWith("sk-")
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(error) {
                    error?.let {
                        snackbar.showSnackbar(it)
                        vm.consumeError()
                    }
                }

                BackHandler(enabled = result != null || showSettings) {
                    if (showSettings) showSettings = false else vm.backToInput()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    val pack = result
                    when {
                        showSettings -> SettingsScreen(
                            savedKey = apiKey,
                            onSave = { vm.saveApiKey(it); showSettings = false },
                            onClear = { vm.clearApiKey() },
                            onBack = { showSettings = false },
                            modifier = Modifier.padding(padding),
                        )
                        pack != null -> ResultScreen(
                            pack = pack,
                            onBack = vm::backToInput,
                            modifier = Modifier.padding(padding),
                        )
                        else -> HomeScreen(
                            dish = dish,
                            working = working,
                            hasKey = hasKey,
                            onDish = vm::setDish,
                            onCreate = vm::create,
                            onSettings = { showSettings = true },
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }
}
