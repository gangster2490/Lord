package de.spardirekt.recipeveo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.spardirekt.recipeveo.domain.ThemeMode
import de.spardirekt.recipeveo.ui.RecipeVeoApp
import de.spardirekt.recipeveo.ui.theme.RecipeVeoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RecipeVeoApplication
        setContent {
            val vm: StudioViewModel = viewModel(factory = StudioViewModel.Factory(app.store, app.photos))
            val state = vm.state.collectAsStateWithLifecycle().value
            RecipeVeoTheme(mode = state.prefs.theme) {
                val dark = when (state.prefs.theme) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as ComponentActivity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
                }
                RecipeVeoApp(vm)
            }
        }
    }
}
