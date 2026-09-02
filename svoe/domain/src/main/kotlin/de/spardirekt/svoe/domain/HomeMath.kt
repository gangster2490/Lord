package de.spardirekt.svoe.domain

data class HomeSnapshot(
    val greeting: String,
    val dateLabel: String,
    val openTasks: Int,
    val todayTaskTitles: List<TaskItem>,
    val habitsDone: Int,
    val habitsTotal: Int,
    val spentTodayMinor: Long,
    val todayMood: Int,
    val todayJournal: String,
)

object HomeMath {
    fun snapshot(state: LifeState, clock: AppClock): HomeSnapshot {
        val today = clock.today()
        val todayTasks = TaskMath.openToday(state.tasks, today)
        val entry = state.todayEntry(today)
        return HomeSnapshot(
            greeting = Copy.greeting(state.prefs.displayName),
            dateLabel = Copy.prettyDate(today),
            openTasks = TaskMath.openCount(state.tasks),
            todayTaskTitles = todayTasks.take(6),
            habitsDone = HabitMath.doneTodayCount(state.habits, state.checks, today),
            habitsTotal = HabitMath.activeCount(state.habits),
            spentTodayMinor = WalletMath.spentOn(state.txs, today),
            todayMood = entry?.mood ?: 0,
            todayJournal = entry?.body.orEmpty(),
        )
    }
}
