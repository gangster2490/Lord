package de.tiktokshop.buchhaltung.data.db

import androidx.room.TypeConverter
import de.tiktokshop.buchhaltung.data.model.ActivityPhase
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.model.SourceDocumentType
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

    @TypeConverter
    fun fromActivityPhase(value: ActivityPhase?): String? = value?.name

    @TypeConverter
    fun toActivityPhase(value: String?): ActivityPhase? = value?.let(ActivityPhase::valueOf)

    @TypeConverter
    fun fromSourceDocumentType(value: SourceDocumentType?): String? = value?.name

    @TypeConverter
    fun toSourceDocumentType(value: String?): SourceDocumentType? = value?.let(SourceDocumentType::valueOf)

    @TypeConverter
    fun fromFrozenBalanceStatus(value: FrozenBalanceStatus?): String? = value?.name

    @TypeConverter
    fun toFrozenBalanceStatus(value: String?): FrozenBalanceStatus? = value?.let(FrozenBalanceStatus::valueOf)

    /** Belegpfade werden als einfache, mit '|' getrennte Liste gespeichert (keine '|' in Pfaden). */
    @TypeConverter
    fun fromUriList(value: List<String>?): String = value.orEmpty().joinToString("|")

    @TypeConverter
    fun toUriList(value: String?): List<String> =
        value?.takeIf { it.isNotBlank() }?.split("|") ?: emptyList()
}
