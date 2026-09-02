package de.spardirekt.svoe.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CopyTest {
    @Test
    fun morningGreetingIncludesName() {
        assertThat(Copy.greeting("Роберт", LocalTime.of(8, 0))).isEqualTo("Доброе утро, Роберт")
    }

    @Test
    fun emptyNameDropsComma() {
        assertThat(Copy.greeting("  ", LocalTime.of(19, 0))).isEqualTo("Добрый вечер")
    }

    @Test
    fun lateNightGreeting() {
        assertThat(Copy.greeting("Анна", LocalTime.of(2, 10))).isEqualTo("Доброй ночи, Анна")
    }
}

class HabitMathTest {
    private val today = LocalDate.of(2026, 9, 2)

    @Test
    fun streakCountsConsecutiveDaysBackFromToday() {
        val habit = "h1"
        val checks = listOf(
            HabitCheck(habit, today.toEpochDay()),
            HabitCheck(habit, today.minusDays(1).toEpochDay()),
            HabitCheck(habit, today.minusDays(2).toEpochDay()),
            HabitCheck(habit, today.minusDays(4).toEpochDay()),
        )
        assertThat(HabitMath.currentStreak(checks, habit, today)).isEqualTo(3)
    }

    @Test
    fun brokenTodayMeansZeroStreak() {
        val checks = listOf(HabitCheck("h1", today.minusDays(1).toEpochDay()))
        assertThat(HabitMath.currentStreak(checks, "h1", today)).isEqualTo(0)
    }

    @Test
    fun weekMarksAlignToMonday() {
        val marks = HabitMath.weekMarks(emptyList(), "h1", today)
        assertThat(marks).hasSize(7)
        assertThat(marks.first().date.dayOfWeek.name).isEqualTo("MONDAY")
        assertThat(marks.last().date.dayOfWeek.name).isEqualTo("SUNDAY")
        assertThat(marks.single { it.isToday }.date).isEqualTo(today)
    }
}

class TaskMathTest {
    private val today = LocalDate.of(2026, 9, 2)
    private val clock = FixedAppClock(today)

    private fun task(
        title: String,
        done: Boolean = false,
        due: LocalDate? = today,
        priority: TaskPriority = TaskPriority.NORMAL,
    ) = TaskItem(
        id = title,
        title = title,
        isDone = done,
        priority = priority,
        dueEpochDay = due?.toEpochDay(),
        createdAt = clock.nowMillis(),
    )

    @Test
    fun overdueOpenTasksComeFirstOnHome() {
        val tasks = listOf(
            task("later", due = today.plusDays(3)),
            task("overdue", due = today.minusDays(1), priority = TaskPriority.LOW),
            task("done", done = true),
            task("today", priority = TaskPriority.HIGH),
        )
        val open = TaskMath.openToday(tasks, today)
        assertThat(open.map { it.title }).containsExactly("today", "overdue").inOrder()
    }

    @Test
    fun searchIsCaseInsensitiveAndCanHideDone() {
        val tasks = listOf(
            task("Купить молоко"),
            task("Купить хлеб", done = true),
            task("Позвонить"),
        )
        val found = TaskMath.visible(tasks, "купить", hideDone = true)
        assertThat(found.map { it.title }).containsExactly("Купить молоко")
    }
}

class WalletMathTest {
    private val day = LocalDate.of(2026, 9, 2)
    private val clock = FixedAppClock(day)

    @Test
    fun monthSummarySplitsIncomeAndExpense() {
        var state = LifeState.Empty
        state = state.addTx(MoneyKind.EXPENSE, 500_00, SpendCategory.FOOD, "еда", day.toEpochDay(), clock)
        state = state.addTx(MoneyKind.EXPENSE, 200_00, SpendCategory.CAFE, "кофе", day.toEpochDay(), clock)
        state = state.addTx(MoneyKind.INCOME, 10_000_00, SpendCategory.OTHER, "оклад", day.toEpochDay(), clock)
        state = state.addTx(
            MoneyKind.EXPENSE,
            80_00,
            SpendCategory.FOOD,
            "август",
            day.minusMonths(1).toEpochDay(),
            clock,
        )
        val summary = WalletMath.monthSummary(state.txs, java.time.YearMonth.of(2026, 9))
        assertThat(summary.expenseMinor).isEqualTo(700_00)
        assertThat(summary.incomeMinor).isEqualTo(10_000_00)
        assertThat(summary.netMinor).isEqualTo(9_300_00)
        assertThat(summary.byCategory.map { it.category }).containsExactly(
            SpendCategory.FOOD,
            SpendCategory.CAFE,
        ).inOrder()
        assertThat(WalletMath.spentOn(state.txs, day)).isEqualTo(700_00)
    }

    @Test
    fun parseAmountAcceptsCommaAndDot() {
        assertThat(WalletMath.parseAmountToMinor("12,50")).isEqualTo(1250)
        assertThat(WalletMath.parseAmountToMinor("12.5")).isEqualTo(1250)
        assertThat(WalletMath.parseAmountToMinor("0")).isNull()
        assertThat(WalletMath.parseAmountToMinor("abc")).isNull()
        assertThat(WalletMath.parseAmountToMinor(" 1 200 ")).isEqualTo(120000)
    }
}

class LifeStateReducerTest {
    private val clock = FixedAppClock(LocalDate.of(2026, 9, 2), 1_000L)

    @Test
    fun blankTaskIsIgnored() {
        val next = LifeState.Empty.addTask("   ", clock = clock)
        assertThat(next.tasks).isEmpty()
    }

    @Test
    fun toggleHabitIsIdempotentPair() {
        var state = LifeState.Empty.addHabit("Вода", "💧", 1, clock)
        val id = state.habits.single().id
        val day = clock.today().toEpochDay()
        state = state.toggleHabit(id, day)
        assertThat(HabitMath.isChecked(state.checks, id, day)).isTrue()
        state = state.toggleHabit(id, day)
        assertThat(state.checks).isEmpty()
    }

    @Test
    fun emptyJournalWithZeroMoodRemovesTheDay() {
        var state = LifeState.Empty.upsertJournal(clock.today().toEpochDay(), 4, "текст", clock)
        assertThat(state.journal).hasSize(1)
        state = state.upsertJournal(clock.today().toEpochDay(), 0, "  ", clock)
        assertThat(state.journal).isEmpty()
    }

    @Test
    fun wipeKeepsOnboardingButDropsContent() {
        val seeded = SeedData.populated(clock, Prefs(displayName = "Роберт", onboardingDone = true, currencyCode = "EUR"))
        val wiped = seeded.wipePersonalData()
        assertThat(wiped.tasks).isEmpty()
        assertThat(wiped.habits).isEmpty()
        assertThat(wiped.prefs.displayName).isEqualTo("Роберт")
        assertThat(wiped.prefs.onboardingDone).isTrue()
    }
}
