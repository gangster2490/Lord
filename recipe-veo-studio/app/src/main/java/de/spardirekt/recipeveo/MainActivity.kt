package de.spardirekt.recipeveo

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
import de.spardirekt.recipeveo.ui.navigation.RecipeVeoNav
import de.spardirekt.recipeveo.ui.theme.RecipeVeoTheme
import de.spardirekt.recipeveo.ui.theme.VppColors

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecipeVeoTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        // Publishes Compose test tags as resource ids so device
                        // automation can address controls without matching
                        // translated labels or tapping coordinates.
                        .semantics { testTagsAsResourceId = true },
                    color = VppColors.backgroundLight
                ) {
                    RecipeVeoNav()
                }
            }
        }
    }
}
