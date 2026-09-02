package de.spardirekt.svoe.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.svoe.SvoeViewModel
import de.spardirekt.svoe.domain.Copy
import de.spardirekt.svoe.domain.Habit
import de.spardirekt.svoe.domain.HabitMath
import de.spardirekt.svoe.domain.SeedData
import de.spardirekt.svoe.domain.WeekMark
import de.spardirekt.svoe.ui.components.ChoiceChip
import de.spardirekt.svoe.ui.components.EmptyState
import de.spardirekt.svoe.ui.components.PrimaryButton
import de.spardirekt.svoe.ui.components.SvoeCard
import de.spardirekt.svoe.ui.components.SvoeField

private val Palette = listOf(
    SeedData.WATER_COLOR,
    SeedData.WALK_COLOR,
    SeedData.READ_COLOR,
    0xFF6B4C9A.toInt(),
    0xFFB23A48.toInt(),
)

private val Emojis = listOf("💧", "🚶", "📖", "🧘", "💤", "🥗", "✍️", "🧹")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(vm: SvoeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = vm.clock.today()
    var creating by remember { mutableStateOf(false) }
    val active = state.habits.filter { !it.archived }
    val archived = state.habits.filter { it.archived }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Новая привычка")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Привычки", style = MaterialTheme.typography.displayLarge) }
            if (active.isEmpty()) {
                item { EmptyState("🌿", "Ни одной привычки", "Отмечайте день касанием кружка. Серия считается подряд с сегодня.") }
            }
            items(active, key = { it.id }) { habit ->
                HabitCard(
                    habit = habit,
                    marks = HabitMath.weekMarks(state.checks, habit.id, today),
                    streak = HabitMath.currentStreak(state.checks, habit.id, today),
                    onToggleDay = { mark -> if (!mark.isFuture) vm.toggleHabit(habit.id, mark.date) },
                    onArchive = { vm.archiveHabit(habit.id) },
                    onDelete = { vm.deleteHabit(habit.id) },
                )
            }
            if (archived.isNotEmpty()) {
                item {
                    Text("Архив", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                }
                items(archived, key = { it.id }) { habit ->
                    SvoeCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${habit.emoji}  ${habit.title}", modifier = Modifier.weight(1f))
                            Text(
                                "Вернуть",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { vm.restoreHabit(habit.id) }.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        HabitEditorSheet(onDismiss = { creating = false }, onSave = { title, emoji, color ->
            vm.addHabit(title, emoji, color)
            creating = false
        })
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    marks: List<WeekMark>,
    streak: Int,
    onToggleDay: (WeekMark) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SvoeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(habit.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.title, style = MaterialTheme.typography.titleLarge)
                Text(Copy.habitStreak(streak), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("В архив") }, onClick = { menu = false; onArchive() })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            marks.forEach { mark ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Copy.weekdayLetter(mark.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val fill = Color(habit.colorArgb)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    mark.done -> fill
                                    mark.isToday -> fill.copy(alpha = 0.18f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .then(
                                if (mark.isToday) Modifier.border(1.5.dp, fill, CircleShape) else Modifier,
                            )
                            .clickable(enabled = !mark.isFuture) { onToggleDay(mark) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HabitEditorSheet(
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("💧") }
    var color by remember { mutableIntStateOf(Palette.first()) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Новая привычка", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            SvoeField(title, { title = it }, "Название")
            Spacer(Modifier.height(14.dp))
            Text("Значок", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Emojis.forEach { e ->
                    ChoiceChip(e, emoji == e) { emoji = e }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Цвет", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .then(if (color == argb) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                            .clickable { color = argb },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Добавить", onClick = { onSave(title, emoji, color) }, enabled = title.isNotBlank())
            Spacer(Modifier.height(28.dp))
        }
    }
}
