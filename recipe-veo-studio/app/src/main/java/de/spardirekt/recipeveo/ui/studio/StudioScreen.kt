package de.spardirekt.recipeveo.ui.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.recipeveo.StudioViewModel
import de.spardirekt.recipeveo.domain.Recipe
import de.spardirekt.recipeveo.domain.RecipeKind
import de.spardirekt.recipeveo.domain.label
import de.spardirekt.recipeveo.ui.components.ChoiceChip
import de.spardirekt.recipeveo.ui.components.EmptyHint
import de.spardirekt.recipeveo.ui.components.MetaRow
import de.spardirekt.recipeveo.ui.components.StudioCard
import de.spardirekt.recipeveo.ui.components.StudioField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    vm: StudioViewModel,
    onOpenEditor: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = kind?.let { runCatching { RecipeKind.valueOf(it) }.getOrNull() }
    val recipes = state.visible(query, filter)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Новый рецепт")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Recipe VEO Studio", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Рецепт на 8 секунд. Промпт собирается здесь, ролик — в Veo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                StudioField(query, { query = it }, "Поиск по студии")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Все", kind == null) { kind = null }
                    RecipeKind.entries.forEach { entry ->
                        ChoiceChip(entry.label(), kind == entry.name) { kind = entry.name }
                    }
                }
            }
            if (recipes.isEmpty()) {
                item {
                    EmptyHint("Пустая полка", "Соберите первый рецепт — четыре кадра и лок объекта.")
                }
            } else {
                items(recipes, key = { it.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        onEdit = { onOpenEditor(recipe.id) },
                        onResult = { if (recipe.compiled != null) onOpenResult(recipe.id) else onOpenEditor(recipe.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onEdit: () -> Unit, onResult: () -> Unit) {
    StudioCard(onClick = onEdit) {
        MetaRow(recipe.kind.label(), recipe.style.label())
        Spacer(Modifier.height(8.dp))
        Text(recipe.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            recipe.subject.ifBlank { "Объект ещё не задан" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (recipe.compiled != null) "Промпт готов" else "Черновик",
                color = if (recipe.compiled != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onResult) {
                Text(if (recipe.compiled != null) "Открыть" else "Собрать")
            }
        }
    }
}
