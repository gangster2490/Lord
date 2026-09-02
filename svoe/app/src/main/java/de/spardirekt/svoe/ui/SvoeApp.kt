package de.spardirekt.svoe.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import de.spardirekt.svoe.SvoeViewModel
import de.spardirekt.svoe.ui.habits.HabitsScreen
import de.spardirekt.svoe.ui.home.HomeScreen
import de.spardirekt.svoe.ui.journal.JournalScreen
import de.spardirekt.svoe.ui.settings.SettingsScreen
import de.spardirekt.svoe.ui.tasks.TasksScreen
import de.spardirekt.svoe.ui.wallet.WalletScreen

private data class Tab(
    val route: String,
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector,
)

private val Tabs = listOf(
    Tab("home", "Сегодня", Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    Tab("tasks", "Задачи", Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle),
    Tab("habits", "Привычки", Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    Tab("journal", "Дневник", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    Tab("wallet", "Деньги", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
)

@Composable
fun SvoeApp(vm: SvoeViewModel) {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (current != "settings") {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
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
                            icon = {
                                Icon(if (selected) tab.filled else tab.outlined, contentDescription = tab.label)
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
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
                    onOpenTasks = { nav.navigate("tasks") },
                    onOpenJournal = { nav.navigate("journal") },
                    onOpenHabits = { nav.navigate("habits") },
                )
            }
            composable("tasks") { TasksScreen(vm) }
            composable("habits") { HabitsScreen(vm) }
            composable("journal") { JournalScreen(vm) }
            composable("wallet") { WalletScreen(vm) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
        }
    }
}
