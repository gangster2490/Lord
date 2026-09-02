package de.spardirekt.recipeveo.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.domain.ProjectStatus
import de.spardirekt.recipeveo.domain.label
import de.spardirekt.recipeveo.ui.components.EmptyHint
import de.spardirekt.recipeveo.ui.components.MetaRow
import de.spardirekt.recipeveo.ui.components.StudioCard

@Composable
fun RecipesScreen(
    vm: StudioViewModel,
    onOpenCreate: () -> Unit,
    onOpenResult: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val rows = state.library()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Рецепты", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(4.dp))
            Text("Готовые 8-секундные пакеты и черновики с фото.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rows.isEmpty()) {
            item { EmptyHint("Пока пусто", "Соберите первый промпт на вкладке «Собрать».") }
        } else {
            items(rows, key = { it.id }) { project ->
                StudioCard(onClick = {
                    vm.open(project.id)
                    if (project.status == ProjectStatus.Ready && project.result != null) onOpenResult()
                    else onOpenCreate()
                }) {
                    MetaRow(project.status.label(), "${project.photos.size} фото · ${project.style.label()}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        project.result?.title?.ifBlank { project.title }?.ifBlank { "Без названия" }
                            ?: project.title.ifBlank { "Черновик" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (project.wish.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(project.wish, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { vm.delete(project.id) }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
