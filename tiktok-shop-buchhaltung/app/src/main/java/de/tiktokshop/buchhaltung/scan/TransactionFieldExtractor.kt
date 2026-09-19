package de.tiktokshop.buchhaltung.scan

import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.ocr.AmountParser
import de.tiktokshop.buchhaltung.ocr.CategorySuggester
import de.tiktokshop.buchhaltung.ocr.GermanDateParser
import de.tiktokshop.buchhaltung.ocr.IgnoreTerms
import de.tiktokshop.buchhaltung.ocr.IncomeTypeSuggester
import de.tiktokshop.buchhaltung.ocr.RecognizedLine
import de.tiktokshop.buchhaltung.ocr.StatusClassifier

/**
 * Extrahiert die Felder einer einzelnen Transaktion (Datum, Händler, Betrag, Typ, Kategorie,
 * Status) aus einer Gruppe visuell zusammengehöriger OCR-Zeilen (siehe [TransactionGrouper]).
 * Reine, Android-unabhängige Logik - jede Regel folgt OCR_RULES.md: kein Betrag/Datum wird
 * erfunden, bei Unsicherheit bleibt das Feld leer.
 */
object TransactionFieldExtractor {

    /** Liefert null, wenn die Gruppe erkennbar keine Transaktion ist (kein Betrag auffindbar). */
    fun extract(group: List<RecognizedLine>): TransactionCandidate? {
        if (group.isEmpty()) return null
        val groupText = group.joinToString("\n") { it.text }

        val amount = AmountParser.extractAmount(groupText) ?: return null
        val date = group.firstNotNullOfOrNull { GermanDateParser.extractDate(it.text) }
            ?: GermanDateParser.extractDate(groupText)
        val merchant = findMerchantLine(group)

        val incomeType = IncomeTypeSuggester.suggest(groupText)
        val entryType = if (incomeType != null) EntryType.INCOME else EntryType.EXPENSE

        return if (entryType == EntryType.INCOME) {
            val statusClassification = StatusClassifier.classify(groupText)
            TransactionCandidate(
                entryType = EntryType.INCOME,
                date = date,
                merchant = merchant ?: "TikTok Shop",
                incomeType = incomeType,
                category = null,
                amountCents = amount.amountCents,
                status = statusClassification.status,
                confidence = combinedConfidence(amount.confidence, statusClassification.confidence, merchant, date),
                source = RecognitionSource.OCR_LOCAL,
                rawText = groupText,
            )
        } else {
            val category = CategorySuggester.suggest(merchant) ?: CategorySuggester.suggest(groupText)
            TransactionCandidate(
                entryType = EntryType.EXPENSE,
                date = date,
                merchant = merchant,
                incomeType = null,
                category = category,
                amountCents = amount.amountCents,
                status = null,
                confidence = combinedConfidence(amount.confidence, if (category != null) 0.8f else 0.3f, merchant, date),
                source = RecognitionSource.OCR_LOCAL,
                rawText = groupText,
            )
        }
    }

    /**
     * Wählt die Zeile, die am ehesten der Händler-/Quellenname ist: keine Monats-/Kopfzeile
     * (siehe [IgnoreTerms]), keine reine Datumszeile, keine reine Betragszeile.
     */
    private fun findMerchantLine(group: List<RecognizedLine>): String? =
        group.firstOrNull { line ->
            val text = line.text.trim()
            text.isNotBlank() &&
                !IgnoreTerms.isIgnoredMerchantLine(text) &&
                GermanDateParser.extractDate(text) == null &&
                AmountParser.extractAmount(text) == null
        }?.text?.trim()

    private fun combinedConfidence(amountConfidence: Float, secondarySignal: Float, merchant: String?, date: java.time.LocalDate?): Float {
        var confidence = (amountConfidence + secondarySignal) / 2f
        if (merchant.isNullOrBlank()) confidence -= 0.15f
        if (date == null) confidence -= 0.15f
        return confidence.coerceIn(0f, 1f)
    }
}
