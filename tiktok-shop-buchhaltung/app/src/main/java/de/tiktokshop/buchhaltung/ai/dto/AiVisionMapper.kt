package de.tiktokshop.buchhaltung.ai.dto

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.model.toCents
import de.tiktokshop.buchhaltung.scan.RecognitionSource
import de.tiktokshop.buchhaltung.scan.TransactionCandidate
import java.math.BigDecimal
import java.time.LocalDate

/** Wandelt die strikten AI-JSON-DTOs in [TransactionCandidate]s - erfindet dabei nie fehlende Werte. */
object AiVisionMapper {

    fun toDomain(dto: AiVisionTransactionDto): TransactionCandidate {
        val entryType = resolveEntryType(dto)
        val amountCents = dto.amount?.let { runCatching { BigDecimal.valueOf(it).toCents() }.getOrNull() }
        val date = dto.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val category = if (entryType == EntryType.EXPENSE) dto.category?.let(::resolveExpenseCategory) else null
        val incomeType = if (entryType == EntryType.INCOME) dto.category else null
        val status = if (entryType == EntryType.INCOME) dto.status?.let(::resolveIncomeStatus) else null

        return TransactionCandidate(
            entryType = entryType,
            date = date,
            merchant = dto.merchant?.trim()?.takeIf { it.isNotBlank() },
            incomeType = incomeType,
            category = category,
            amountCents = amountCents,
            currency = dto.currency ?: "EUR",
            businessUsePercent = dto.businessPercentage ?: 100,
            status = status,
            confidence = dto.confidence ?: 0.5f,
            source = RecognitionSource.AI_VISION,
            rawText = "",
        )
    }

    private fun resolveEntryType(dto: AiVisionTransactionDto): EntryType = when (dto.type?.lowercase()) {
        "income" -> EntryType.INCOME
        "expense" -> EntryType.EXPENSE
        else -> if (dto.status != null && resolveIncomeStatus(dto.status) != null) EntryType.INCOME else EntryType.EXPENSE
    }

    private fun resolveExpenseCategory(raw: String): ExpenseCategory? =
        ExpenseCategory.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }

    private fun resolveIncomeStatus(raw: String): IncomeStatus? =
        IncomeStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
}
