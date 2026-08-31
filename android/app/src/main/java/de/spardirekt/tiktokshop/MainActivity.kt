package de.spardirekt.tiktokshop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.tiktokshop.ui.CreatorScreen
import de.spardirekt.tiktokshop.ui.CreatorViewModel
import de.spardirekt.tiktokshop.ui.theme.TikTokShopTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CreatorViewModel by viewModels { CreatorViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TikTokShopTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CreatorScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                )
            }
        }
    }
}
