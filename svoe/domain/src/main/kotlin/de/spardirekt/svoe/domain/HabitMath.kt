package de.spardirekt.svoe.domain

import java.time.DayOfWeek
import java.time.LocalDate

data class WeekMark(
    val date: LocalDate,
    val done: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

object HabitMath {
    fun isChecked(checks: List<HabitCheck>, habitId: String, epochDay: Long): Boolean =
        checks.any { it.habitId == habitId && it.epochDay == epochDay }

    fun currentStreak(checks: List<HabitCheck>, habitId: String, today: LocalDate): Int {
        val doneDays = checks.asSequence()
            .filter { it.habitId == habitId }
            .map { it.epochDay }
            .toHashSet()
        var streak = 0
        var day = today.toEpochDay()
        while (day in doneDays) {
            streak++
            day--
        }
        return streak
    }

    fun weekDays(today: LocalDate): List<LocalDate> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (0L..6L).map { monday.plusDays(it) }
    }

    fun weekMarks(checks: List<HabitCheck>, habitId: String, today: LocalDate): List<WeekMark> {
        return weekDays(today).map { date ->
            WeekMark(
                date = date,
                done = isChecked(checks, habitId, date.toEpochDay()),
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
    }

    fun doneTodayCount(habits: List<Habit>, checks: List<HabitCheck>, today: LocalDate): Int {
        val day = today.toEpochDay()
        return habits.count { !it.archived && isChecked(checks, it.id, day) }
    }

    fun activeCount(habits: List<Habit>): Int = habits.count { !it.archived }
}
