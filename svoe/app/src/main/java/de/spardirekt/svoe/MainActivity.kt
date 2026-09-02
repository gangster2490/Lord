package de.spardirekt.svoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.spardirekt.svoe.domain.ThemeMode
import de.spardirekt.svoe.ui.SvoeApp
import de.spardirekt.svoe.ui.theme.Night
import de.spardirekt.svoe.ui.theme.Paper
import de.spardirekt.svoe.ui.theme.SvoeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as SvoeApplication).store
        setContent {
            val vm: SvoeViewModel = viewModel(factory = SvoeViewModel.Factory(store))
            val state = vm.state.collectAsStateWithLifecycle().value
            SvoeTheme(mode = state.prefs.theme) {
                val dark = when (state.prefs.theme) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as ComponentActivity).window
                    window.statusBarColor = (if (dark) Night else Paper).toArgb()
                    window.navigationBarColor = (if (dark) Night else Paper).toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
                }
                SvoeApp(vm)
            }
        }
    }
}
