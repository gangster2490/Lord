package de.tiktokshop.buchhaltung.export

import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.formatGerman
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Baut die CSV-Exporte gemäß CLAUDE_MASTER_PROMPT.md / sample_transactions.csv.
 * Reine, Android-unabhängige Logik, damit Formatierung/Filterung ohne Instrumentierung
 * testbar ist (TC03, TC07).
 */
object ExportFormatter {

    const val GENERAL_HEADER =
        "Datum;Typ;Plattform_Haendler;Kategorie;Bruttobetrag;Geschaeftsanteil;Anrechenbarer_Betrag;Status;Belegdatei;Notiz"

    const val TIKTOK_HEADER =
        "Datum;Provision_angezeigt;Status;Ausgezahlt_am;Ausgezahlter_Betrag;Beleg"

    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

    fun buildGeneralCsv(
        incomes: List<IncomeEntry>,
        expenses: List<ExpenseEntry>,
        from: LocalDate,
        to: LocalDate,
    ): String {
        val rows = mutableListOf(GENERAL_HEADER)

        incomes.filter { it.date in from..to }
            .sortedBy { it.date }
            .forEach { entry ->
                rows += listOf(
                    entry.date.format(DATE_FMT),
                    "EINNAHME",
                    entry.platform,
                    entry.incomeType,
                    entry.amountCents.formatGerman(),
                    "100",
                    entry.amountCents.formatGerman(),
                    entry.status.name,
                    entry.receiptUris.joinToString("|") { File(it).name },
                    entry.note.orEmpty(),
                ).joinToString(";") { csvField(it) }
            }

        expenses.filter { it.date in from..to }
            .sortedBy { it.date }
            .forEach { entry ->
                rows += listOf(
                    entry.date.format(DATE_FMT),
                    "AUSGABE",
                    entry.merchant.orEmpty(),
                    entry.category.name,
                    entry.grossAmountCents.formatGerman(),
                    entry.businessUsePercent.toString(),
                    entry.businessAmountCents.formatGerman(),
                    if (entry.confirmed) "BESTAETIGT" else "UNBESTAETIGT",
                    entry.receiptUris.joinToString("|") { File(it).name },
                    entry.note.orEmpty(),
                ).joinToString(";") { csvField(it) }
            }

        return rows.joinToString("\n")
    }

    fun buildTikTokCsv(incomes: List<IncomeEntry>, from: LocalDate, to: LocalDate): String {
        val rows = mutableListOf(TIKTOK_HEADER)
        incomes.filter { it.date in from..to }
            .sortedBy { it.date }
            .forEach { entry ->
                rows += listOf(
                    entry.date.format(DATE_FMT),
                    entry.amountCents.formatGerman(),
                    entry.status.name,
                    entry.payoutDate?.format(DATE_FMT).orEmpty(),
                    entry.paidOutAmountCents?.formatGerman().orEmpty(),
                    entry.receiptUris.joinToString("|") { File(it).name },
                ).joinToString(";") { csvField(it) }
            }
        return rows.joinToString("\n")
    }

    fun exportFileBaseName(prefix: String, from: LocalDate, to: LocalDate): String =
        "${prefix}_${from.format(DATE_FMT)}_bis_${to.format(DATE_FMT)}"

    private fun csvField(value: String): String =
        if (value.contains(';') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
