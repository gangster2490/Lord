package de.spardirekt.svoe.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class LifeState(
    val version: Int = 1,
    val prefs: Prefs = Prefs(),
    val tasks: List<TaskItem> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val checks: List<HabitCheck> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val txs: List<MoneyTx> = emptyList(),
) {
    fun completeOnboarding(name: String, currencyCode: String): LifeState =
        copy(
            prefs = prefs.copy(
                displayName = name.trim(),
                currencyCode = currencyCode,
                onboardingDone = true,
            ),
        )

    fun updatePrefs(
        name: String = prefs.displayName,
        theme: ThemeMode = prefs.theme,
        currencyCode: String = prefs.currencyCode,
    ): LifeState = copy(
        prefs = prefs.copy(
            displayName = name.trim(),
            theme = theme,
            currencyCode = currencyCode,
        ),
    )

    fun addTask(
        title: String,
        notes: String = "",
        priority: TaskPriority = TaskPriority.NORMAL,
        dueEpochDay: Long? = null,
        clock: AppClock,
    ): LifeState {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return this
        val task = TaskItem(
            id = newId(),
            title = trimmed,
            notes = notes.trim(),
            priority = priority,
            dueEpochDay = dueEpochDay,
            createdAt = clock.nowMillis(),
        )
        return copy(tasks = listOf(task) + tasks)
    }

    fun toggleTask(id: String, clock: AppClock): LifeState = copy(
        tasks = tasks.map { task ->
            if (task.id != id) task
            else task.copy(
                isDone = !task.isDone,
                completedAt = if (!task.isDone) clock.nowMillis() else null,
            )
        },
    )

    fun updateTask(
        id: String,
        title: String,
        notes: String,
        priority: TaskPriority,
        dueEpochDay: Long?,
    ): LifeState {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return this
        return copy(
            tasks = tasks.map { task ->
                if (task.id != id) task
                else task.copy(
                    title = trimmed,
                    notes = notes.trim(),
                    priority = priority,
                    dueEpochDay = dueEpochDay,
                )
            },
        )
    }

    fun deleteTask(id: String): LifeState = copy(tasks = tasks.filterNot { it.id == id })

    fun clearCompletedTasks(): LifeState = copy(tasks = tasks.filterNot { it.isDone })

    fun addHabit(title: String, emoji: String, colorArgb: Int, clock: AppClock): LifeState {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return this
        val habit = Habit(
            id = newId(),
            title = trimmed,
            emoji = emoji.ifBlank { "✦" },
            colorArgb = colorArgb,
            createdAt = clock.nowMillis(),
        )
        return copy(habits = habits + habit)
    }

    fun toggleHabit(habitId: String, epochDay: Long): LifeState {
        val key = HabitCheck(habitId, epochDay)
        return if (checks.any { it == key }) {
            copy(checks = checks.filterNot { it == key })
        } else {
            copy(checks = checks + key)
        }
    }

    fun archiveHabit(id: String): LifeState = copy(
        habits = habits.map { if (it.id == id) it.copy(archived = true) else it },
    )

    fun restoreHabit(id: String): LifeState = copy(
        habits = habits.map { if (it.id == id) it.copy(archived = false) else it },
    )

    fun deleteHabit(id: String): LifeState = copy(
        habits = habits.filterNot { it.id == id },
        checks = checks.filterNot { it.habitId == id },
    )

    fun upsertJournal(epochDay: Long, mood: Int, body: String, clock: AppClock): LifeState {
        val safeMood = mood.coerceIn(0, 5)
        val trimmed = body.trim()
        val existing = journal.firstOrNull { it.epochDay == epochDay }
        if (safeMood == 0 && trimmed.isEmpty()) {
            return copy(journal = journal.filterNot { it.epochDay == epochDay })
        }
        val entry = JournalEntry(
            id = existing?.id ?: newId(),
            epochDay = epochDay,
            mood = safeMood,
            body = trimmed,
            updatedAt = clock.nowMillis(),
        )
        return copy(journal = listOf(entry) + journal.filterNot { it.epochDay == epochDay })
    }

    fun deleteJournal(id: String): LifeState = copy(journal = journal.filterNot { it.id == id })

    fun addTx(
        kind: MoneyKind,
        amountMinor: Long,
        category: SpendCategory,
        note: String,
        epochDay: Long,
        clock: AppClock,
    ): LifeState {
        if (amountMinor <= 0) return this
        val tx = MoneyTx(
            id = newId(),
            kind = kind,
            amountMinor = amountMinor,
            category = if (kind == MoneyKind.INCOME) SpendCategory.OTHER else category,
            note = note.trim(),
            epochDay = epochDay,
            createdAt = clock.nowMillis(),
        )
        return copy(txs = listOf(tx) + txs)
    }

    fun deleteTx(id: String): LifeState = copy(txs = txs.filterNot { it.id == id })

    fun wipePersonalData(): LifeState = LifeState(
        prefs = prefs.copy(onboardingDone = true),
    )

    companion object {
        val Empty = LifeState()
    }
}

fun LifeState.todayEntry(today: LocalDate): JournalEntry? =
    journal.firstOrNull { it.epochDay == today.toEpochDay() }
