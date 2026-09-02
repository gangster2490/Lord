package de.spardirekt.svoe.ui.tasks

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.spardirekt.svoe.SvoeViewModel
import de.spardirekt.svoe.domain.Copy
import de.spardirekt.svoe.domain.TaskBucket
import de.spardirekt.svoe.domain.TaskItem
import de.spardirekt.svoe.domain.TaskMath
import de.spardirekt.svoe.domain.TaskPriority
import de.spardirekt.svoe.domain.epochDayToDate
import de.spardirekt.svoe.ui.components.ChoiceChip
import de.spardirekt.svoe.ui.components.EmptyState
import de.spardirekt.svoe.ui.components.PrimaryButton
import de.spardirekt.svoe.ui.components.SvoeCard
import de.spardirekt.svoe.ui.components.SvoeField
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: SvoeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = vm.clock.today()
    var query by rememberSaveable { mutableStateOf("") }
    var hideDone by rememberSaveable { mutableStateOf(true) }
    var editor by remember { mutableStateOf<TaskItem?>(null) }
    var creating by remember { mutableStateOf(false) }

    val visible = TaskMath.visible(state.tasks, query, hideDone)
    val grouped = visible.groupBy { TaskMath.bucket(it, today) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Новая задача")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Задачи", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(12.dp))
                SvoeField(value = query, onValueChange = { query = it }, label = "Поиск")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(if (hideDone) "Скрыть готовые" else "Показать готовые", hideDone) {
                        hideDone = !hideDone
                    }
                    if (state.tasks.any { it.isDone }) {
                        ChoiceChip("Очистить готовые", false) { vm.clearCompletedTasks() }
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    EmptyState("✦", "Пока тихо", "Добавьте первое дело — оно останется на этом телефоне.")
                }
            } else {
                listOf(
                    TaskBucket.OVERDUE,
                    TaskBucket.TODAY,
                    TaskBucket.UPCOMING,
                    TaskBucket.LATER,
                    TaskBucket.DONE,
                ).forEach { bucket ->
                    val rows = grouped[bucket].orEmpty()
                    if (rows.isNotEmpty()) {
                        item {
                            Text(
                                Copy.bucketTitle(bucket),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            )
                        }
                        items(rows, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                onToggle = { vm.toggleTask(task.id) },
                                onDelete = { vm.deleteTask(task.id) },
                                onOpen = { editor = task },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editor != null) {
        TaskEditorSheet(
            initial = editor,
            today = today,
            onDismiss = { creating = false; editor = null },
            onSave = { title, notes, priority, due ->
                val current = editor
                if (current == null) vm.addTask(title, notes, priority, due)
                else vm.updateTask(current.id, title, notes, priority, due)
                creating = false
                editor = null
            },
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    SvoeCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.isDone,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val due = task.dueEpochDay?.let { Copy.shortDate(epochDayToDate(it)) }
                val meta = listOfNotNull(Copy.priorityLabel(task.priority), due).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorSheet(
    initial: TaskItem?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, String, TaskPriority, LocalDate?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var priority by remember { mutableStateOf(initial?.priority ?: TaskPriority.NORMAL) }
    var due by remember { mutableStateOf(initial?.dueEpochDay?.let { epochDayToDate(it) } ?: today) }
    var hasDue by remember { mutableStateOf(initial?.dueEpochDay != null || initial == null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(if (initial == null) "Новое дело" else "Дело", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            SvoeField(title, { title = it }, "Что сделать")
            Spacer(Modifier.height(10.dp))
            SvoeField(notes, { notes = it }, "Заметка", singleLine = false, minLines = 3)
            Spacer(Modifier.height(14.dp))
            Text("Важность", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { p ->
                    ChoiceChip(Copy.priorityLabel(p), priority == p) { priority = p }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Срок", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Сегодня", hasDue && due == today) {
                    hasDue = true
                    due = today
                }
                ChoiceChip("Завтра", hasDue && due == today.plusDays(1)) {
                    hasDue = true
                    due = today.plusDays(1)
                }
                ChoiceChip("Через неделю", hasDue && due == today.plusDays(7)) {
                    hasDue = true
                    due = today.plusDays(7)
                }
                ChoiceChip("Без срока", !hasDue) { hasDue = false }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Сохранить", onClick = { onSave(title, notes, priority, due.takeIf { hasDue }) }, enabled = title.isNotBlank())
            Spacer(Modifier.height(28.dp))
        }
    }
}
