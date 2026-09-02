package de.spardirekt.svoe.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
}

@Serializable
enum class MoneyKind {
    EXPENSE,
    INCOME,
}

@Serializable
enum class SpendCategory {
    FOOD,
    CAFE,
    TRANSPORT,
    HOME,
    HEALTH,
    SHOPPING,
    SUBSCRIPTIONS,
    OTHER,
}

@Serializable
data class Prefs(
    val displayName: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val currencyCode: String = "EUR",
    val onboardingDone: Boolean = false,
)

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val notes: String = "",
    val isDone: Boolean = false,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val dueEpochDay: Long? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
)

@Serializable
data class Habit(
    val id: String,
    val title: String,
    val emoji: String,
    val colorArgb: Int,
    val createdAt: Long,
    val archived: Boolean = false,
)

@Serializable
data class HabitCheck(
    val habitId: String,
    val epochDay: Long,
)

@Serializable
data class JournalEntry(
    val id: String,
    val epochDay: Long,
    val mood: Int = 0,
    val body: String = "",
    val updatedAt: Long,
)

@Serializable
data class MoneyTx(
    val id: String,
    val kind: MoneyKind,
    val amountMinor: Long,
    val category: SpendCategory = SpendCategory.OTHER,
    val note: String = "",
    val epochDay: Long,
    val createdAt: Long,
)

fun newId(): String = UUID.randomUUID().toString()

fun LocalDate.toEpochDayLong(): Long = toEpochDay()

fun epochDayToDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
