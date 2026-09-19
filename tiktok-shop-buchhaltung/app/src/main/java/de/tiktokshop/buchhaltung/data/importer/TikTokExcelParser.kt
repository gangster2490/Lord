package de.tiktokshop.buchhaltung.data.importer

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.toCents
import de.tiktokshop.buchhaltung.data.xlsx.XlsxParseException
import de.tiktokshop.buchhaltung.data.xlsx.XlsxReader
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Parst einen TikTok-Shop-Earnings-Report (.xlsx) in [TikTokEarningsRow]s (§7). Die Kopfzeile
 * liegt real nicht in Zeile 1 (dort steht ein Disclaimer, danach Metadaten wie "Date period"/
 * "Creator name"), sondern erst nach ein paar Metadatenzeilen - die Kopfzeile wird deshalb
 * dynamisch gesucht (anhand "Date (UTC+0)" + "Transaction ID"), Spalten werden über ihren
 * Namen statt über eine feste Position gelesen (die Anzahl der VAT-Spalten dazwischen variiert
 * zwischen Exporten). Beträge in der Income/Expense-Spalte sind reine Dezimalstrings mit Punkt
 * (z. B. "2.37") - kein deutsches Komma-Format, daher kein `ocr.AmountParser` nötig.
 */
object TikTokExcelParser {

    private const val COL_DATE = "Date (UTC+0)"
    private const val COL_TRANSACTION_ID = "Transaction ID"
    private const val COL_TYPE = "Type of earnings"
    private const val COL_CURRENCY = "Currency"
    private const val COL_INCOME = "Income"
    private const val COL_EXPENSE = "Expense"
    private const val COL_PAYER = "Payer"
    private const val COL_PAYER_COUNTRY = "Payer country"

    fun parse(input: InputStream): TikTokEarningsParseResult {
        val rawRows = XlsxReader.readSheet(input, "Sheet1")

        var period: String? = null
        var creatorName: String? = null
        for (row in rawRows.take(10)) {
            val label = row.getOrNull(0)?.trim() ?: continue
            if (label.startsWith("Date period")) period = row.getOrNull(1)?.trim()?.ifBlank { null }
            if (label == "Creator name") creatorName = row.getOrNull(1)?.trim()?.ifBlank { null }
        }

        val headerRowIndex = rawRows.indexOfFirst { row ->
            row.getOrNull(0)?.trim() == COL_DATE && row.getOrNull(1)?.trim() == COL_TRANSACTION_ID
        }
        if (headerRowIndex < 0) {
            throw XlsxParseException(
                "Kopfzeile mit '$COL_DATE' / '$COL_TRANSACTION_ID' nicht gefunden - ist das ein TikTok-Shop-Earnings-Report?",
            )
        }

        val header = rawRows[headerRowIndex]
        val columnIndex: Map<String, Int> = header.mapIndexedNotNull { idx, name ->
            name.trim().takeIf { it.isNotBlank() }?.let { it to idx }
        }.toMap()

        fun requireColumn(name: String): Int =
            columnIndex[name] ?: throw XlsxParseException("Erforderliche Spalte '$name' fehlt in der Kopfzeile.")

        val dateCol = requireColumn(COL_DATE)
        val idCol = requireColumn(COL_TRANSACTION_ID)
        val typeCol = requireColumn(COL_TYPE)
        val currencyCol = columnIndex[COL_CURRENCY]
        val incomeCol = requireColumn(COL_INCOME)
        val expenseCol = columnIndex[COL_EXPENSE]
        val payerCol = columnIndex[COL_PAYER]
        val payerCountryCol = columnIndex[COL_PAYER_COUNTRY]

        val rows = mutableListOf<TikTokEarningsRow>()
        val errors = mutableListOf<TikTokEarningsRowError>()

        for (i in headerRowIndex + 1 until rawRows.size) {
            val row = rawRows[i]
            val rowNumber = i + 1 // 1-basiert, wie in Excel angezeigt
            val dateText = row.getOrNull(dateCol)?.trim().orEmpty()
            if (dateText.isBlank()) continue // leere Zeile (z. B. Tabellenende)

            val date = parseIsoOrTikTokDate(dateText)
            if (date == null) {
                errors += TikTokEarningsRowError(rowNumber, "Ungültiges Datum: '$dateText'")
                continue
            }

            val transactionId = row.getOrNull(idCol)?.trim().orEmpty()
            if (transactionId.isBlank()) {
                errors += TikTokEarningsRowError(rowNumber, "Transaction ID fehlt")
                continue
            }

            val incomeText = row.getOrNull(incomeCol)?.trim().orEmpty()
            val incomeCents = parseDecimalCents(incomeText)
            if (incomeCents == null) {
                errors += TikTokEarningsRowError(rowNumber, "Ungültiger Betrag in Spalte 'Income': '$incomeText'")
                continue
            }

            val earningType = row.getOrNull(typeCol)?.trim().orEmpty()
            val expenseText = expenseCol?.let { row.getOrNull(it)?.trim() }.orEmpty()

            rows += TikTokEarningsRow(
                rowNumber = rowNumber,
                transactionDate = date,
                externalTransactionId = transactionId,
                earningType = earningType,
                incomeType = TikTokEarningTypeMapper.mapToIncomeType(earningType),
                currency = currencyCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } } ?: "EUR",
                grossAmountCents = incomeCents,
                platformExpenseCents = if (expenseText.isBlank()) null else parseDecimalCents(expenseText),
                payer = payerCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } },
                payerCountry = payerCountryCol?.let { row.getOrNull(it)?.trim()?.ifBlank { null } },
            )
        }

        return TikTokEarningsParseResult(period, creatorName, rows, errors)
    }

    private fun parseIsoOrTikTokDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text) }.getOrNull()

    private fun parseDecimalCents(text: String): Cents? {
        if (text.isBlank()) return null
        return runCatching { BigDecimal(text).toCents() }.getOrNull()
    }
}
