package de.spardirekt.recipeveo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.domain.StudioRules
import de.spardirekt.recipeveo.domain.label
import de.spardirekt.recipeveo.ui.components.MetaRow
import de.spardirekt.recipeveo.ui.components.PrimaryButton
import de.spardirekt.recipeveo.ui.components.SectionHeader
import de.spardirekt.recipeveo.ui.components.StatChip
import de.spardirekt.recipeveo.ui.components.StudioCard

@Composable
fun HomeScreen(
    vm: StudioViewModel,
    onOpenSettings: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenResult: () -> Unit,
) {
    val home by vm.home.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(home.greeting, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(4.dp))
                Text(home.dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { StatChip("Готово", home.readyCount.toString()) }
            Column(Modifier.weight(1f)) { StatChip("Черновики", home.draftCount.toString()) }
            Column(Modifier.weight(1f)) { StatChip("Формат", "8.0s") }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Фото товара → точный 8-секундный рецепт для Veo 3.1. Ролик собирает Veo, не это приложение.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        val draft = home.draft
        PrimaryButton(
            text = if (draft != null && StudioRules.canGenerate(draft)) "Собрать рецепт" else "Открыть сборку",
            onClick = {
                if (draft != null) vm.open(draft.id)
                onOpenCreate()
            },
        )
        Spacer(Modifier.height(8.dp))
        SectionHeader("Последний рецепт", "Все") { onOpenRecipes() }
        val featured = home.featured
        if (featured?.result == null) {
            StudioCard(onClick = onOpenCreate) {
                Text("Пока нет готовых промптов", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Добавьте фото и соберите первый 8-секундный пакет.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            StudioCard(onClick = {
                vm.open(featured.id)
                onOpenResult()
            }) {
                MetaRow(featured.style.label(), featured.status.label())
                Spacer(Modifier.height(8.dp))
                Text(featured.result!!.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    featured.result!!.hashtags.joinToString("  "),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (home.recent.size > 1) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("В студии")
            home.recent.drop(1).forEach { project ->
                StudioCard(onClick = {
                    vm.open(project.id)
                    if (project.result != null) onOpenResult() else onOpenCreate()
                }) {
                    Text(
                        project.result?.title?.ifBlank { project.title }?.ifBlank { "Черновик" }
                            ?: project.title.ifBlank { "Черновик" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(project.style.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
