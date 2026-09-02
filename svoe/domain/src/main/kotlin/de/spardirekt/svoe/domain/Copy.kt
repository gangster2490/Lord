package de.spardirekt.svoe.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Copy {
    private val ru = Locale("ru", "RU")
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", ru)
    private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", ru)

    fun greeting(name: String, time: LocalTime = LocalTime.now()): String {
        val hello = when (time.hour) {
            in 5..11 -> "Доброе утро"
            in 12..16 -> "Добрый день"
            in 17..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val who = name.trim()
        return if (who.isEmpty()) hello else "$hello, $who"
    }

    fun prettyDate(date: LocalDate): String =
        date.format(dateFmt).replaceFirstChar { it.titlecase(ru) }

    fun shortDate(date: LocalDate): String = date.format(shortDate)

    fun monthTitle(date: LocalDate): String =
        date.format(monthFmt).replaceFirstChar { it.titlecase(ru) }

    fun weekdayLetter(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EE", ru)).take(2).replaceFirstChar { it.titlecase(ru) }

    fun priorityLabel(priority: TaskPriority): String = when (priority) {
        TaskPriority.HIGH -> "Важно"
        TaskPriority.NORMAL -> "Обычно"
        TaskPriority.LOW -> "Позже"
    }

    fun moodLabel(mood: Int): String = when (mood) {
        1 -> "Тяжело"
        2 -> "Так себе"
        3 -> "Нормально"
        4 -> "Хорошо"
        5 -> "Отлично"
        else -> "Без оценки"
    }

    fun moodEmoji(mood: Int): String = when (mood) {
        1 -> "😞"
        2 -> "😐"
        3 -> "🙂"
        4 -> "😊"
        5 -> "🤩"
        else -> "·"
    }

    fun bucketTitle(bucket: TaskBucket): String = when (bucket) {
        TaskBucket.OVERDUE -> "Просрочено"
        TaskBucket.TODAY -> "Сегодня"
        TaskBucket.UPCOMING -> "Скоро"
        TaskBucket.LATER -> "Без срока"
        TaskBucket.DONE -> "Готово"
    }

    fun habitStreak(days: Int): String = when (days) {
        0 -> "ещё не начата"
        1 -> "1 день подряд"
        in 2..4 -> "$days дня подряд"
        else -> "$days дней подряд"
    }
}
