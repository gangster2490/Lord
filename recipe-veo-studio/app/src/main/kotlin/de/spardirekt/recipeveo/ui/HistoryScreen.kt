package de.spardirekt.recipeveo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.spardirekt.recipeveo.domain.Project

@Composable
fun HistoryScreen(
    projects: List<Project>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("История", style = MaterialTheme.typography.headlineSmall)
        if (projects.isEmpty()) {
            Text("Пока пусто. Соберите первый промпт на вкладке «Создать».")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { project ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(project.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(project.prompt?.title ?: project.wish.ifBlank { "Без названия" })
                            Text("${project.photos.size} фото")
                        }
                    }
                }
            }
        }
    }
}
