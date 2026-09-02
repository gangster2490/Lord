package de.spardirekt.svoe.domain

import java.time.YearMonth
import java.time.LocalDate

data class CategorySpend(
    val category: SpendCategory,
    val amountMinor: Long,
)

data class MonthSummary(
    val yearMonth: YearMonth,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val byCategory: List<CategorySpend>,
    val count: Int,
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

object WalletMath {
    fun monthSummary(txs: List<MoneyTx>, month: YearMonth): MonthSummary {
        val inMonth = txs.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
        val expenses = inMonth.filter { it.kind == MoneyKind.EXPENSE }
        val income = inMonth.filter { it.kind == MoneyKind.INCOME }
        val byCategory = expenses
            .groupBy { it.category }
            .map { (category, rows) -> CategorySpend(category, rows.sumOf { it.amountMinor }) }
            .sortedByDescending { it.amountMinor }
        return MonthSummary(
            yearMonth = month,
            expenseMinor = expenses.sumOf { it.amountMinor },
            incomeMinor = income.sumOf { it.amountMinor },
            byCategory = byCategory,
            count = inMonth.size,
        )
    }

    fun spentOn(txs: List<MoneyTx>, day: LocalDate): Long =
        txs.filter { it.kind == MoneyKind.EXPENSE && it.epochDay == day.toEpochDay() }
            .sumOf { it.amountMinor }

    fun parseAmountToMinor(raw: String): Long? {
        val cleaned = raw.trim().replace(',', '.').replace(" ", "")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0) return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }
}
