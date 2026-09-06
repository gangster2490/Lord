package de.spardirekt.clipforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
                Box(
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                ) {
                    ClipForgeApp(
                        state = state,
                        onEvent = viewModel::onEvent,
                    )
                }
            }
        }
    }
}
