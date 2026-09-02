package de.spardirekt.recipeveo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.domain.Project
import de.spardirekt.recipeveo.ui.CreateScreen
import de.spardirekt.recipeveo.ui.HistoryScreen
import de.spardirekt.recipeveo.ui.RecipeTheme
import de.spardirekt.recipeveo.ui.ResultScreen

class MainActivity : ComponentActivity() {
    private val vm: StudioViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudioViewModel(application as RecipeVeoApp) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecipeTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                val screen by vm.screen.collectAsStateWithLifecycle()
                val error by vm.error.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                BackHandler(enabled = screen is StudioViewModel.Screen.Result) {
                    vm.backFromResult()
                }
                LaunchedEffect(error) {
                    error?.let {
                        snackbar.showSnackbar(it)
                        vm.consumeError()
                    }
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (screen !is StudioViewModel.Screen.Result) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = screen is StudioViewModel.Screen.Create,
                                    onClick = { vm.go(StudioViewModel.Screen.Create) },
                                    icon = { Text("1") },
                                    label = { Text("Создать") },
                                )
                                NavigationBarItem(
                                    selected = screen is StudioViewModel.Screen.History,
                                    onClick = { vm.go(StudioViewModel.Screen.History) },
                                    icon = { Text("2") },
                                    label = { Text("История") },
                                )
                            }
                        }
                    },
                ) { padding ->
                    when (val s = screen) {
                        StudioViewModel.Screen.Create -> CreateScreen(
                            project = state.active() ?: emptyProject,
                            onWish = vm::setWish,
                            onAddPhoto = vm::addPhoto,
                            onRemovePhoto = vm::removePhoto,
                            onGenerate = vm::generate,
                            modifier = Modifier.padding(padding),
                        )
                        StudioViewModel.Screen.History -> HistoryScreen(
                            projects = state.ready(),
                            onOpen = vm::openProject,
                            modifier = Modifier.padding(padding),
                        )
                        is StudioViewModel.Screen.Result -> {
                            val project = state.projects.firstOrNull { it.id == s.id } ?: emptyProject
                            ResultScreen(
                                project = project,
                                onBack = vm::backFromResult,
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val emptyProject = Project(id = "", createdAt = 0L, updatedAt = 0L)
    }
}
