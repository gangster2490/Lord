package de.spardirekt.svoe.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.svoe.SvoeViewModel
import de.spardirekt.svoe.domain.Copy
import de.spardirekt.svoe.domain.epochDayToDate
import de.spardirekt.svoe.ui.components.EmptyState
import de.spardirekt.svoe.ui.components.MoodRow
import de.spardirekt.svoe.ui.components.PrimaryButton
import de.spardirekt.svoe.ui.components.SvoeCard
import de.spardirekt.svoe.ui.components.SvoeField

@Composable
fun JournalScreen(vm: SvoeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = vm.clock.today()
    val todayEntry = state.journal.firstOrNull { it.epochDay == today.toEpochDay() }
    var body by rememberSaveable(todayEntry?.id, todayEntry?.updatedAt) { mutableStateOf(todayEntry?.body.orEmpty()) }
    var mood by rememberSaveable(todayEntry?.id, todayEntry?.mood) { mutableIntStateOf(todayEntry?.mood ?: 0) }
    val history = state.journal.filterNot { it.epochDay == today.toEpochDay() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Дневник", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(4.dp))
            Text(Copy.prettyDate(today), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SvoeCard {
                Text("Сегодня", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                MoodRow(selected = mood, onSelect = { mood = it })
                Spacer(Modifier.height(14.dp))
                SvoeField(
                    value = body,
                    onValueChange = { body = it },
                    label = "Что за день",
                    singleLine = false,
                    minLines = 5,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Сохранить день", onClick = { vm.upsertJournal(today, mood, body) })
            }
        }
        if (history.isEmpty() && todayEntry == null && body.isBlank()) {
            item {
                EmptyState("✎", "Страницы ещё чистые", "Настроение и пара фраз вечером — этого достаточно.")
            }
        }
        if (history.isNotEmpty()) {
            item { Text("Раньше", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            items(history, key = { it.id }) { entry ->
                SvoeCard {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                Copy.prettyDate(epochDayToDate(entry.epochDay)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (entry.mood in 1..5) {
                                Text(
                                    "${Copy.moodEmoji(entry.mood)}  ${Copy.moodLabel(entry.mood)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (entry.body.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(entry.body, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        IconButton(onClick = { vm.deleteJournal(entry.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                        }
                    }
                }
            }
        }
    }
}
