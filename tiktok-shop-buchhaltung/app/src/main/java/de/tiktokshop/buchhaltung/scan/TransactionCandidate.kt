package de.tiktokshop.buchhaltung.scan

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import java.time.LocalDate
import java.util.UUID

/** Woher ein [TransactionCandidate] stammt - für Transparenz im Review-Screen. */
enum class RecognitionSource {
    OCR_LOCAL,
    AI_VISION,
    MANUAL,
}

/**
 * Eine einzelne, noch unbestätigte Transaktion, die aus einem Screenshot erkannt wurde
 * (ein Screenshot kann mehrere [TransactionCandidate] erzeugen). Niemals direkt speichern -
 * immer zuerst im Review-Screen vom Nutzer bestätigen lassen (OCR_RULES.md, TC06).
 *
 * Felder sind absichtlich nullable statt geraten: fehlt ein Wert, bleibt er null und die
 * Kachel wird im Review-Screen als "Bitte prüfen" markiert, statt einen falschen Wert
 * vorauszufüllen.
 */
data class TransactionCandidate(
    val id: String = UUID.randomUUID().toString(),
    val entryType: EntryType,
    val date: LocalDate?,
    val merchant: String?,
    /** Nur für [EntryType.INCOME] relevant, z. B. "Provision", "Bonus", "Rewards". */
    val incomeType: String?,
    /** Nur für [EntryType.EXPENSE] relevant. */
    val category: ExpenseCategory?,
    val amountCents: Cents?,
    val currency: String = "EUR",
    val businessUsePercent: Int = 100,
    /** Nur für [EntryType.INCOME] relevant (TikTok-Auszahlungsstatus). */
    val status: IncomeStatus?,
    val confidence: Float,
    val source: RecognitionSource,
    val rawText: String,
) {
    /** true, wenn Pflichtfelder fehlen oder die Erkennung unsicher ist - im Review-Screen hervorheben. */
    val needsReview: Boolean
        get() = amountCents == null ||
            date == null ||
            merchant.isNullOrBlank() ||
            confidence < 0.75f ||
            (entryType == EntryType.EXPENSE && category == null) ||
            (entryType == EntryType.INCOME && status == null)
}
