package de.spardirekt.recipeveo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import de.spardirekt.recipeveo.ui.home.HomeScreen
import de.spardirekt.recipeveo.ui.recipes.RecipesScreen
import de.spardirekt.recipeveo.ui.result.ResultScreen
import de.spardirekt.recipeveo.ui.settings.SettingsScreen
import de.spardirekt.recipeveo.ui.styles.StylesScreen

private data class Tab(val route: String, val label: String, val outlined: ImageVector, val filled: ImageVector)

private val Tabs = listOf(
    Tab("home", "Студия", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab("create", "Собрать", Icons.Outlined.MovieFilter, Icons.Rounded.MovieFilter),
    Tab("recipes", "Рецепты", Icons.Outlined.PhotoLibrary, Icons.Rounded.PhotoLibrary),
    Tab("styles", "Стили", Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
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
    val current = backStack?.destination?.route ?: "home"
    val openResult by vm.openResultId.collectAsStateWithLifecycle()
    LaunchedEffect(openResult) {
        val id = openResult ?: return@LaunchedEffect
        nav.navigate("result")
        vm.consumeResultNav()
    }

    fun goTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val hideBar = current == "result" || current == "settings"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { goTab(tab.route) },
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
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenCreate = { goTab("create") },
                    onOpenRecipes = { goTab("recipes") },
                    onOpenResult = { nav.navigate("result") },
                )
            }
            composable("create") { CreateScreen(vm) }
            composable("recipes") {
                RecipesScreen(
                    vm = vm,
                    onOpenCreate = { goTab("create") },
                    onOpenResult = { nav.navigate("result") },
                )
            }
            composable("styles") {
                StylesScreen(
                    vm = vm,
                    onApply = { goTab("create") },
                )
            }
            composable("result") { ResultScreen(vm, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
        }
    }
}
