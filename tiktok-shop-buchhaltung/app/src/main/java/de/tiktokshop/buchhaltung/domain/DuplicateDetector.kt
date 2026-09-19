package de.tiktokshop.buchhaltung.domain

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import java.time.LocalDate

/**
 * Erkennt mögliche Dubletten anhand von Datum + Betrag + Händler/Plattform + Bild-Hash
 * (TEST_CASES.md TC04). Verhindert nicht das Speichern, sondern liefert eine Warnung.
 */
object DuplicateDetector {

    fun findIncomeDuplicate(
        date: LocalDate,
        amountCents: Cents,
        platform: String,
        imageHash: String?,
        existing: List<IncomeEntry>,
    ): IncomeEntry? = existing.firstOrNull { candidate ->
        candidate.date == date &&
            candidate.amountCents == amountCents &&
            candidate.platform.equals(platform, ignoreCase = true) &&
            (imageHash == null || candidate.imageHash == null || candidate.imageHash == imageHash)
    }

    fun findExpenseDuplicate(
        date: LocalDate,
        grossAmountCents: Cents,
        merchant: String?,
        imageHash: String?,
        existing: List<ExpenseEntry>,
    ): ExpenseEntry? = existing.firstOrNull { candidate ->
        candidate.date == date &&
            candidate.grossAmountCents == grossAmountCents &&
            candidate.merchant?.equals(merchant, ignoreCase = true) == true &&
            (imageHash == null || candidate.imageHash == null || candidate.imageHash == imageHash)
    }
}
