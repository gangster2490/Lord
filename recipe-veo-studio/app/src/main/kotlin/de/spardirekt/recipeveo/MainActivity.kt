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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.ui.HomeScreen
import de.spardirekt.recipeveo.ui.RecipeTheme
import de.spardirekt.recipeveo.ui.ResultScreen

class MainActivity : ComponentActivity() {
    private val vm: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecipeTheme {
                val dish by vm.dish.collectAsStateWithLifecycle()
                val working by vm.working.collectAsStateWithLifecycle()
                val result by vm.result.collectAsStateWithLifecycle()
                val error by vm.error.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(error) {
                    error?.let {
                        snackbar.showSnackbar(it)
                        vm.consumeError()
                    }
                }
                val pack = result
                BackHandler(enabled = pack != null) { vm.backToInput() }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    if (pack == null) {
                        HomeScreen(
                            dish = dish,
                            working = working,
                            onDish = vm::setDish,
                            onCreate = vm::create,
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        ResultScreen(
                            pack = pack,
                            onBack = vm::backToInput,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }
}
