package de.spardirekt.veoprompt.ultra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import de.spardirekt.veoprompt.ultra.ui.navigation.UltraNav
import de.spardirekt.veoprompt.ultra.ui.theme.UltraColors
import de.spardirekt.veoprompt.ultra.ui.theme.VeoPromptUltraTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeoPromptUltraTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true },
                    color = UltraColors.pearl
                ) {
                    UltraNav()
                }
            }
        }
    }
}
