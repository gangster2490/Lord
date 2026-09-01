package de.spardirekt.agents.pro

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
import de.spardirekt.agents.pro.ui.navigation.VeoPromptProNav
import de.spardirekt.agents.pro.ui.theme.VeoPromptProTheme
import de.spardirekt.agents.pro.ui.theme.VppColors

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeoPromptProTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        // Publishes Compose test tags as resource ids so device
                        // automation can address controls without matching
                        // translated labels or tapping coordinates.
                        .semantics { testTagsAsResourceId = true },
                    color = VppColors.backgroundLight
                ) {
                    VeoPromptProNav()
                }
            }
        }
    }
}
