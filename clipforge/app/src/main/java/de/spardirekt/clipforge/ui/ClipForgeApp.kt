package de.spardirekt.clipforge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import de.spardirekt.clipforge.ui.components.ConfirmDestructiveDialog
import de.spardirekt.clipforge.ui.create.StudioScreen
import de.spardirekt.clipforge.ui.history.HistoryScreen
import de.spardirekt.clipforge.ui.result.ResultScreen
import de.spardirekt.clipforge.ui.settings.SettingsScreen
import de.spardirekt.clipforge.ui.theme.Background
import de.spardirekt.clipforge.ui.theme.Magenta
import de.spardirekt.clipforge.ui.theme.Surface
import de.spardirekt.clipforge.ui.theme.TextDim
import de.spardirekt.clipforge.ui.theme.TextPrimary

@Composable
fun ClipForgeApp(
    state: StudioUiState,
    onEvent: (StudioEvent) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current
    LaunchedEffect(state.copiedLabel) {
        state.copiedLabel?.let { snackbar.showSnackbar(it) }
    }
    DisposableEffect(state.isGenerating) {
        view.keepScreenOn = state.isGenerating
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = state.showResult) {
        onEvent(StudioEvent.CloseResult)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.showResult) {
                NavigationBar(containerColor = Surface, contentColor = TextPrimary) {
                    NavigationBarItem(
                        selected = state.tab == Tab.STUDIO,
                        onClick = { onEvent(StudioEvent.OpenTab(Tab.STUDIO)) },
                        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = "Студия") },
                        label = { Text("Студия") },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = state.tab == Tab.ARCHIVE,
                        onClick = { onEvent(StudioEvent.OpenTab(Tab.ARCHIVE)) },
                        icon = { Icon(Icons.Outlined.Inventory2, contentDescription = "Архив") },
                        label = { Text("Архив") },
                        colors = navColors(),
                    )
                    NavigationBarItem(
                        selected = state.tab == Tab.SETTINGS,
                        onClick = { onEvent(StudioEvent.OpenTab(Tab.SETTINGS)) },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = "Настройки") },
                        label = { Text("Настройки") },
                        colors = navColors(),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.showResult && state.result != null -> ResultScreen(state, onEvent)
                state.tab == Tab.ARCHIVE -> HistoryScreen(state, onEvent)
                state.tab == Tab.SETTINGS -> SettingsScreen(state, onEvent)
                else -> StudioScreen(state, onEvent)
            }
        }
    }

    when (val pending = state.pendingConfirm) {
        PendingConfirm.NewProject -> ConfirmDestructiveDialog(
            title = "Новый проект?",
            message = "Фото и текущий пакет на экране сбросятся. Архив не трогаем.",
            confirmLabel = "Сбросить",
            onConfirm = { onEvent(StudioEvent.ConfirmPending) },
            onDismiss = { onEvent(StudioEvent.DismissConfirm) },
        )
        PendingConfirm.ClearKey -> ConfirmDestructiveDialog(
            title = "Удалить ключ?",
            message = "Ключ сотрётся с устройства. Без ключа или sk-demo генерация недоступна.",
            confirmLabel = "Удалить",
            onConfirm = { onEvent(StudioEvent.ConfirmPending) },
            onDismiss = { onEvent(StudioEvent.DismissConfirm) },
        )
        PendingConfirm.ClearArchive -> ConfirmDestructiveDialog(
            title = "Очистить архив?",
            message = "Все сохранённые пакеты, превью и фото товара будут удалены. Отменить нельзя.",
            confirmLabel = "Очистить",
            onConfirm = { onEvent(StudioEvent.ConfirmPending) },
            onDismiss = { onEvent(StudioEvent.DismissConfirm) },
        )
        is PendingConfirm.DeleteHistory -> ConfirmDestructiveDialog(
            title = "Удалить пакет?",
            message = "«${pending.name}» исчезнет из архива.",
            confirmLabel = "Удалить",
            onConfirm = { onEvent(StudioEvent.ConfirmPending) },
            onDismiss = { onEvent(StudioEvent.DismissConfirm) },
        )
        null -> Unit
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Magenta,
    selectedTextColor = Magenta,
    indicatorColor = Magenta.copy(alpha = 0.16f),
    unselectedIconColor = TextDim,
    unselectedTextColor = TextDim,
)
