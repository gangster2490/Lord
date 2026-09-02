package de.spardirekt.svoe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.spardirekt.svoe.domain.AppClock
import de.spardirekt.svoe.domain.HomeMath
import de.spardirekt.svoe.domain.HomeSnapshot
import de.spardirekt.svoe.domain.LifeState
import de.spardirekt.svoe.domain.LifeStore
import de.spardirekt.svoe.domain.MoneyKind
import de.spardirekt.svoe.domain.SeedData
import de.spardirekt.svoe.domain.SpendCategory
import de.spardirekt.svoe.domain.TaskPriority
import de.spardirekt.svoe.domain.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SvoeViewModel(
    private val store: LifeStore,
) : ViewModel() {
    val clock: AppClock = store.clock
    val ready: StateFlow<Boolean> = store.ready
    val state: StateFlow<LifeState> = store.state
    val home: StateFlow<HomeSnapshot> = store.state
        .map { HomeMath.snapshot(it, clock) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeMath.snapshot(store.state.value, clock),
        )

    init {
        viewModelScope.launch { store.hydrate() }
    }

    fun completeOnboarding(name: String, currency: String) = update { it.completeOnboarding(name, currency) }

    fun setName(name: String) = update { it.updatePrefs(name = name) }

    fun setTheme(mode: ThemeMode) = update { it.updatePrefs(theme = mode) }

    fun setCurrency(code: String) = update { it.updatePrefs(currencyCode = code) }

    fun addTask(title: String, notes: String, priority: TaskPriority, due: LocalDate?) =
        update { it.addTask(title, notes, priority, due?.toEpochDay(), clock) }

    fun toggleTask(id: String) = update { it.toggleTask(id, clock) }

    fun updateTask(id: String, title: String, notes: String, priority: TaskPriority, due: LocalDate?) =
        update { it.updateTask(id, title, notes, priority, due?.toEpochDay()) }

    fun deleteTask(id: String) = update { it.deleteTask(id) }

    fun clearCompletedTasks() = update { it.clearCompletedTasks() }

    fun addHabit(title: String, emoji: String, colorArgb: Int) =
        update { it.addHabit(title, emoji, colorArgb, clock) }

    fun toggleHabit(id: String, day: LocalDate = clock.today()) =
        update { it.toggleHabit(id, day.toEpochDay()) }

    fun archiveHabit(id: String) = update { it.archiveHabit(id) }

    fun restoreHabit(id: String) = update { it.restoreHabit(id) }

    fun deleteHabit(id: String) = update { it.deleteHabit(id) }

    fun upsertJournal(day: LocalDate, mood: Int, body: String) =
        update { it.upsertJournal(day.toEpochDay(), mood, body, clock) }

    fun setTodayMood(mood: Int) = update { state ->
        val today = clock.today().toEpochDay()
        val current = state.journal.firstOrNull { it.epochDay == today }
        state.upsertJournal(today, mood, current?.body.orEmpty(), clock)
    }

    fun deleteJournal(id: String) = update { it.deleteJournal(id) }

    fun addTx(kind: MoneyKind, amountMinor: Long, category: SpendCategory, note: String, day: LocalDate) =
        update { it.addTx(kind, amountMinor, category, note, day.toEpochDay(), clock) }

    fun deleteTx(id: String) = update { it.deleteTx(id) }

    fun seedExamples() = update { SeedData.populated(clock, it.prefs) }

    fun wipe() = update { it.wipePersonalData() }

    private fun update(transform: (LifeState) -> LifeState) {
        viewModelScope.launch { store.update(transform) }
    }

    class Factory(private val store: LifeStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SvoeViewModel(store) as T
    }
}
