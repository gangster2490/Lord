package de.tiktokshop.buchhaltung.data.db

import androidx.room.TypeConverter
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromIncomeStatus(value: IncomeStatus?): String? = value?.name

    @TypeConverter
    fun toIncomeStatus(value: String?): IncomeStatus? = value?.let(IncomeStatus::valueOf)

    @TypeConverter
    fun fromExpenseCategory(value: ExpenseCategory?): String? = value?.name

    @TypeConverter
    fun toExpenseCategory(value: String?): ExpenseCategory? = value?.let(ExpenseCategory::valueOf)

    /** Belegpfade werden als einfache, mit '|' getrennte Liste gespeichert (keine '|' in Pfaden). */
    @TypeConverter
    fun fromUriList(value: List<String>?): String = value.orEmpty().joinToString("|")

    @TypeConverter
    fun toUriList(value: String?): List<String> =
        value?.takeIf { it.isNotBlank() }?.split("|") ?: emptyList()
}
