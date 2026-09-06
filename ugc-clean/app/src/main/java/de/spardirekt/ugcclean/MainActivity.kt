package de.spardirekt.ugcclean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.spardirekt.ugcclean.model.ProjectStatus
import de.spardirekt.ugcclean.ui.AppViewModel
import de.spardirekt.ugcclean.ui.Tab
import de.spardirekt.ugcclean.ui.create.CreateScreen
import de.spardirekt.ugcclean.ui.history.HistoryScreen
import de.spardirekt.ugcclean.ui.result.ResultScreen
import de.spardirekt.ugcclean.ui.settings.SettingsScreen
import de.spardirekt.ugcclean.ui.theme.Accent
import de.spardirekt.ugcclean.ui.theme.Background
import de.spardirekt.ugcclean.ui.theme.Surface
import de.spardirekt.ugcclean.ui.theme.TextDim
import de.spardirekt.ugcclean.ui.theme.TextMid
import de.spardirekt.ugcclean.ui.theme.UgcCleanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UgcCleanTheme {
                val vm: AppViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(state.toast) {
                    state.toast?.let {
                        snackbar.showSnackbar(it)
                        vm.consumeToast()
                    }
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize().background(Background),
                    containerColor = Background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar(containerColor = Surface, contentColor = TextMid) {
                            NavigationBarItem(
                                selected = state.tab == Tab.CREATE && !state.showResult,
                                onClick = { vm.selectTab(Tab.CREATE) },
                                icon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_create)) },
                                colors = navColors(),
                            )
                            NavigationBarItem(
                                selected = state.tab == Tab.HISTORY,
                                onClick = { vm.selectTab(Tab.HISTORY) },
                                icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_history)) },
                                colors = navColors(),
                            )
                            NavigationBarItem(
                                selected = state.tab == Tab.SETTINGS,
                                onClick = { vm.selectTab(Tab.SETTINGS) },
                                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_settings)) },
                                colors = navColors(),
                            )
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).background(Background)) {
                        when {
                            state.showResult && state.opened?.status == ProjectStatus.READY -> {
                                ResultScreen(
                                    project = state.opened!!,
                                    onCopied = vm::showToast,
                                    onSave = { vm.markSaved() },
                                    onNewProject = { vm.newProject() },
                                    onBack = { vm.closeResult() },
                                )
                            }
                            state.tab == Tab.HISTORY -> HistoryScreen(
                                projects = state.history,
                                onOpen = vm::openProject,
                                onDelete = vm::deleteProject,
                            )
                            state.tab == Tab.SETTINGS -> SettingsScreen(
                                provider = state.provider,
                                keyDraft = state.keyDraft,
                                masked = state.keyMasked,
                                onProvider = vm::setProvider,
                                onKeyChange = vm::setKeyDraft,
                                onToggleMask = vm::toggleKeyMask,
                                onSave = vm::saveKey,
                                onTest = vm::testKey,
                                onRemove = vm::removeKey,
                            )
                            else -> CreateScreen(
                                photos = state.photos,
                                language = state.language,
                                canStart = vm.canStart(),
                                running = state.active?.status == ProjectStatus.RUNNING,
                                stage = state.active?.stage,
                                percent = state.active?.progressPercent ?: 0,
                                error = state.active?.errorMessage,
                                onAddPhotos = vm::addPhotos,
                                onRemovePhoto = vm::removePhoto,
                                onClear = vm::clearPhotos,
                                onLanguage = vm::setLanguage,
                                onStart = vm::start,
                                onRetry = vm::retry,
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Accent,
    selectedTextColor = Accent,
    unselectedIconColor = TextDim,
    unselectedTextColor = TextDim,
    indicatorColor = Accent.copy(alpha = 0.14f),
)
