package de.spardirekt.clipforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.clipforge.ui.ClipForgeApp
import de.spardirekt.clipforge.ui.StudioViewModel
import de.spardirekt.clipforge.ui.theme.ClipForgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StudioViewModel by viewModels { StudioViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipForgeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ClipForgeApp(
                    state = state,
                    onEvent = viewModel::onEvent,
                )
            }
        }
    }
}
