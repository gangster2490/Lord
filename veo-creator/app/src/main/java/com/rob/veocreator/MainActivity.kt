package com.rob.veocreator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rob.veocreator.ui.create.CreateScreen
import com.rob.veocreator.ui.history.HistoryScreen
import com.rob.veocreator.ui.settings.SettingsScreen
import com.rob.veocreator.ui.theme.VeoBackground
import com.rob.veocreator.ui.theme.VeoCreatorTheme
import com.rob.veocreator.ui.theme.VeoTextSecondary
import com.rob.veocreator.ui.theme.VeoYellow

private sealed class Tab(val route: String, val label: String) {
    data object Create : Tab("create", "Create")
    data object History : Tab("history", "History")
    data object Settings : Tab("settings", "Settings")
}

private val tabs = listOf(Tab.Create, Tab.History, Tab.Settings)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeoCreatorTheme {
                VeoCreatorAppRoot()
            }
        }
    }
}

@Composable
private fun VeoCreatorAppRoot() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = VeoBackground,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.Create -> Icons.Filled.AddCircle
                                    Tab.History -> Icons.Filled.History
                                    Tab.Settings -> Icons.Filled.Settings
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VeoYellow,
                            selectedTextColor = VeoYellow,
                            unselectedIconColor = VeoTextSecondary,
                            unselectedTextColor = VeoTextSecondary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Create.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Tab.Create.route) {
                CreateScreen(onOpenSettings = {
                    navController.navigate(Tab.Settings.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(Tab.History.route) { HistoryScreen() }
            composable(Tab.Settings.route) { SettingsScreen(onKeyChanged = {}) }
        }
    }
}
