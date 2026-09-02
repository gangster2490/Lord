package de.spardirekt.svoe.domain

import java.time.LocalDate

enum class TaskBucket {
    OVERDUE,
    TODAY,
    UPCOMING,
    LATER,
    DONE,
}

object TaskMath {
    fun bucket(task: TaskItem, today: LocalDate): TaskBucket {
        if (task.isDone) return TaskBucket.DONE
        val due = task.dueEpochDay ?: return TaskBucket.LATER
        val todayDay = today.toEpochDay()
        return when {
            due < todayDay -> TaskBucket.OVERDUE
            due == todayDay -> TaskBucket.TODAY
            else -> TaskBucket.UPCOMING
        }
    }

    fun visible(tasks: List<TaskItem>, query: String, hideDone: Boolean): List<TaskItem> {
        val needle = query.trim().lowercase()
        return tasks.filter { task ->
            (!hideDone || !task.isDone) &&
                (needle.isEmpty() ||
                    task.title.lowercase().contains(needle) ||
                    task.notes.lowercase().contains(needle))
        }
    }

    fun openToday(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> =
        tasks.filter { !it.isDone && bucket(it, today) in setOf(TaskBucket.OVERDUE, TaskBucket.TODAY) }
            .sortedWith(compareBy<TaskItem> { it.priority.rank }.thenBy { it.createdAt })

    fun openCount(tasks: List<TaskItem>): Int = tasks.count { !it.isDone }

    private val TaskPriority.rank: Int
        get() = when (this) {
            TaskPriority.HIGH -> 0
            TaskPriority.NORMAL -> 1
            TaskPriority.LOW -> 2
        }
}
