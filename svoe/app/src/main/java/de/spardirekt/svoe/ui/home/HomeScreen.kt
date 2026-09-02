package de.spardirekt.svoe.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import de.spardirekt.svoe.SvoeViewModel
import de.spardirekt.svoe.domain.Copy
import de.spardirekt.svoe.domain.HabitMath
import de.spardirekt.svoe.domain.MoneyFormat
import de.spardirekt.svoe.ui.components.MoodRow
import de.spardirekt.svoe.ui.components.SectionHeader
import de.spardirekt.svoe.ui.components.SvoeCard
import de.spardirekt.svoe.ui.components.StatChip

@Composable
fun HomeScreen(
    vm: SvoeViewModel,
    onOpenSettings: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenHabits: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val today = vm.clock.today()
    val currency = state.prefs.currencyCode

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
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BoxStat(Modifier.weight(1f), "Дела", home.openTasks.toString())
            BoxStat(
                Modifier.weight(1f),
                "Привычки",
                if (home.habitsTotal == 0) "—" else "${home.habitsDone}/${home.habitsTotal}",
            )
            BoxStat(
                Modifier.weight(1f),
                "Сегодня",
                if (home.spentTodayMinor == 0L) "0" else MoneyFormat.formatExpense(home.spentTodayMinor, currency),
            )
        }
        Spacer(Modifier.height(22.dp))
        SvoeCard {
            Text("Как день", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            MoodRow(selected = home.todayMood, onSelect = vm::setTodayMood)
        }
        Spacer(Modifier.height(8.dp))
        SectionHeader("Сегодняшние задачи", "Все") { onOpenTasks() }
        if (home.todayTaskTitles.isEmpty()) {
            SvoeCard(onClick = onOpenTasks) {
                Text("На сегодня ничего не горит", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Добавьте дело со сроком на сегодня — оно появится здесь.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            SvoeCard {
                home.todayTaskTitles.forEach { task ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = task.isDone,
                            onCheckedChange = { vm.toggleTask(task.id) },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (task.priority.name == "HIGH") {
                                Text(
                                    Copy.priorityLabel(task.priority),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionHeader("Привычки", "Все") { onOpenHabits() }
        val active = state.habits.filter { !it.archived }
        if (active.isEmpty()) {
            SvoeCard(onClick = onOpenHabits) {
                Text("Пока без привычек", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Вода, прогулка, чтение — начните с одной.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            SvoeCard {
                active.take(4).forEach { habit ->
                    val streak = HabitMath.currentStreak(state.checks, habit.id, today)
                    val done = HabitMath.isChecked(state.checks, habit.id, today.toEpochDay())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggleHabit(habit.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(habit.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(habit.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                Copy.habitStreak(streak),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (done) "есть" else "отметить",
                            color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionHeader("Дневник", "Открыть") { onOpenJournal() }
        SvoeCard(onClick = onOpenJournal) {
            if (home.todayJournal.isBlank()) {
                Text("Сегодня ещё не писали", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Пара предложений вечером — и день не растворится.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text("Сегодня", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(home.todayJournal, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BoxStat(modifier: Modifier, label: String, value: String) {
    Column(modifier = modifier) {
        StatChip(label, value)
    }
}
