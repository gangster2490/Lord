package de.spardirekt.veoprompt.ultra.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.spardirekt.veoprompt.ultra.ui.create.CreateScreen
import de.spardirekt.veoprompt.ultra.ui.create.CreateViewModel
import de.spardirekt.veoprompt.ultra.ui.generation.GenerationScreen
import de.spardirekt.veoprompt.ultra.ui.history.HistoryScreen
import de.spardirekt.veoprompt.ultra.ui.history.HistoryViewModel
import de.spardirekt.veoprompt.ultra.ui.result.ResultScreen
import de.spardirekt.veoprompt.ultra.ui.result.ResultViewModel
import de.spardirekt.veoprompt.ultra.ui.settings.SettingsScreen
import de.spardirekt.veoprompt.ultra.ui.settings.SettingsViewModel
import de.spardirekt.veoprompt.ultra.ui.theme.LocalBottomBarInset
import de.spardirekt.veoprompt.ultra.ui.theme.UltraColors

object Routes {
    const val CREATE = "create?projectId={projectId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RESULT = "result/{projectId}"
    const val GENERATION = "generation/{projectId}"
    const val NEW_PROJECT = "__new__"
    fun create(projectId: String = "") = "create?projectId=$projectId"
    fun createFresh() = create(NEW_PROJECT)
    fun result(id: String) = "result/$id"
    fun generation(id: String) = "generation/$id"
}

@Composable
fun UltraNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route?.startsWith("create") == true ||
        route == Routes.HISTORY ||
        route == Routes.SETTINGS

    var measuredBarHeight by remember { mutableStateOf(0.dp) }
    val bottomInset = if (showBottomBar) measuredBarHeight else 0.dp

    CompositionLocalProvider(LocalBottomBarInset provides bottomInset) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UltraColors.pearl)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.CREATE,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable(
                    route = Routes.CREATE,
                    arguments = listOf(
                        navArgument("projectId") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { entry ->
                    val vm: CreateViewModel = viewModel()
                    val requestedId = entry.arguments?.getString("projectId").orEmpty()
                    LaunchedEffect(requestedId) {
                        if (requestedId.isBlank()) return@LaunchedEffect
                        vm.handleNavProject(requestedId)
                        if (requestedId == Routes.NEW_PROJECT) {
                            navController.navigate(Routes.create()) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    CreateScreen(
                        viewModel = vm,
                        onOpenResult = { id ->
                            navController.navigate(Routes.result(id)) { launchSingleTop = true }
                        },
                        onOpenGeneration = { id ->
                            navController.navigate(Routes.generation(id)) { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.HISTORY) {
                    val vm: HistoryViewModel = viewModel()
                    HistoryScreen(
                        viewModel = vm,
                        onOpenResult = { id -> navController.navigate(Routes.result(id)) },
                        onOpenGeneration = { id -> navController.navigate(Routes.generation(id)) },
                        onOpenCreate = { id ->
                            navController.navigate(Routes.create(id)) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Routes.SETTINGS) {
                    val vm: SettingsViewModel = viewModel()
                    SettingsScreen(viewModel = vm)
                }
                composable(
                    route = Routes.GENERATION,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("projectId").orEmpty()
                    val vm: CreateViewModel = viewModel()
                    GenerationScreen(
                        projectId = id,
                        createViewModel = vm,
                        onOpenResult = { pid ->
                            navController.navigate(Routes.result(pid)) {
                                popUpTo(Routes.GENERATION) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Routes.RESULT,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("projectId").orEmpty()
                    val vm: ResultViewModel = viewModel()
                    ResultScreen(
                        projectId = id,
                        viewModel = vm,
                        onBackToCreate = {
                            navController.navigate(Routes.createFresh()) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            if (showBottomBar) {
                BottomBar(
                    currentRoute = route,
                    onSelect = { target ->
                        navController.navigate(target) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onHeightChanged = { measuredBarHeight = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(UltraColors.bottomBar)
            .onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) }
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomItem("Создать", Icons.Outlined.AddCircleOutline, currentRoute?.startsWith("create") == true) {
            onSelect(Routes.create())
        }
        BottomItem("История", Icons.Outlined.History, currentRoute == Routes.HISTORY) {
            onSelect(Routes.HISTORY)
        }
        BottomItem("Настройки", Icons.Outlined.Settings, currentRoute == Routes.SETTINGS) {
            onSelect(Routes.SETTINGS)
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) UltraColors.navPill else Color.Transparent
    val fg = if (isSelected) UltraColors.midnight else UltraColors.textMuted
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = fg, fontSize = 11.sp)
    }
}
