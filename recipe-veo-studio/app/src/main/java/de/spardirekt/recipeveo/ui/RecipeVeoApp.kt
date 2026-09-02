package de.spardirekt.recipeveo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.ui.create.CreateScreen
import de.spardirekt.recipeveo.ui.history.HistoryScreen
import de.spardirekt.recipeveo.ui.result.ResultScreen
import de.spardirekt.recipeveo.ui.settings.SettingsScreen

private data class Tab(val route: String, val label: String, val outlined: ImageVector, val filled: ImageVector)

private val Tabs = listOf(
    Tab("create", "Создать", Icons.Outlined.AddAPhoto, Icons.Rounded.AddAPhoto),
    Tab("history", "История", Icons.Outlined.History, Icons.Rounded.History),
    Tab("settings", "Ещё", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun RecipeVeoApp(vm: StudioViewModel) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "create"
    val openResult by vm.openResultId.collectAsStateWithLifecycle()
    LaunchedEffect(openResult) {
        val id = openResult ?: return@LaunchedEffect
        nav.navigate("result")
        vm.consumeResultNav()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (current != "result") {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.filled else tab.outlined, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "create",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("create") { CreateScreen(vm) }
            composable("history") {
                HistoryScreen(
                    vm = vm,
                    onOpenCreate = {
                        nav.navigate("create") {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenResult = { nav.navigate("result") },
                )
            }
            composable("result") { ResultScreen(vm, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
