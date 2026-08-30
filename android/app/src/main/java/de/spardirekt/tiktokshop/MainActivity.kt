package de.spardirekt.tiktokshop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.spardirekt.tiktokshop.ui.creator.CreatorScreen
import de.spardirekt.tiktokshop.ui.creator.CreatorViewModel
import de.spardirekt.tiktokshop.ui.settings.SettingsScreen
import de.spardirekt.tiktokshop.ui.theme.Bg
import de.spardirekt.tiktokshop.ui.theme.Bg2
import de.spardirekt.tiktokshop.ui.theme.NeonGreen
import de.spardirekt.tiktokshop.ui.theme.TextMid
import de.spardirekt.tiktokshop.ui.theme.TextPrimary
import de.spardirekt.tiktokshop.ui.theme.TikTokShopTheme
import de.spardirekt.tiktokshop.ui.veo.VeoCleanerScreen
import de.spardirekt.tiktokshop.ui.veo.VeoViewModel

class MainActivity : ComponentActivity() {
    private val creatorViewModel: CreatorViewModel by viewModels()
    private val veoViewModel: VeoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TikTokShopTheme {
                ShopApp(creatorViewModel, veoViewModel)
            }
        }
    }
}

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Dest("creator", "Creator", Icons.Outlined.AutoAwesome),
    Dest("veo", "VEO Cleaner", Icons.Outlined.CleaningServices),
    Dest("settings", "Einstellungen", Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopApp(
    creatorViewModel: CreatorViewModel,
    veoViewModel: VeoViewModel,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "creator"

    Scaffold(
        containerColor = Bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (current) {
                            "veo" -> "VEO Product Photo Cleaner"
                            "settings" -> "Einstellungen"
                            else -> "⚡ TikTok Shop Creator"
                        },
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Bg,
                    titleContentColor = TextPrimary,
                ),
                actions = {
                    if (current == "creator") {
                        Text(
                            "SparDirekt DE",
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Bg2, contentColor = TextPrimary) {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonGreen,
                            indicatorColor = NeonGreen,
                            unselectedIconColor = TextMid,
                            unselectedTextColor = TextMid,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "creator",
            modifier = Modifier.padding(padding),
        ) {
            composable("creator") { CreatorScreen(creatorViewModel) }
            composable("veo") { VeoCleanerScreen(veoViewModel) }
            composable("settings") { SettingsScreen(creatorViewModel, veoViewModel) }
        }
    }
}
