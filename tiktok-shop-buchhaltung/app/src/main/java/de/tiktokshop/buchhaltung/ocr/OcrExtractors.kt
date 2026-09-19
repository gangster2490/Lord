package de.tiktokshop.buchhaltung.ocr

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import java.time.LocalDate

/**
 * Vorschlag für eine Einnahme aus OCR. Alle Felder sind bewusst nullable/unbestätigt -
 * der Bestätigungsscreen (UX_FLOW.md) muss jeden Wert vor dem Speichern anzeigen.
 */
data class IncomeDraft(
    val date: LocalDate?,
    val platform: String,
    val incomeType: String,
    val amountCents: Cents?,
    val amountConfidence: Float?,
    val status: IncomeStatus,
    val statusConfidence: Float,
    val rawText: String,
    val needsReview: Boolean,
)

data class ExpenseDraft(
    val date: LocalDate?,
    val merchant: String?,
    val suggestedCategory: ExpenseCategory?,
    val grossAmountCents: Cents?,
    val amountConfidence: Float?,
    val rawText: String,
    val needsReview: Boolean,
)

private const val REVIEW_CONFIDENCE_THRESHOLD = 0.75f

object IncomeOcrExtractor {
    fun extract(rawText: String, defaultPlatform: String = "TikTok Shop", defaultIncomeType: String = "Provision"): IncomeDraft {
        val amount = AmountParser.extractAmount(rawText)
        val status = StatusClassifier.classify(rawText)
        val date = GermanDateParser.extractDate(rawText)

        val needsReview = amount == null ||
            amount.confidence < REVIEW_CONFIDENCE_THRESHOLD ||
            status.confidence < REVIEW_CONFIDENCE_THRESHOLD ||
            date == null

        return IncomeDraft(
            date = date,
            platform = defaultPlatform,
            incomeType = defaultIncomeType,
            amountCents = amount?.amountCents,
            amountConfidence = amount?.confidence,
            status = status.status,
            statusConfidence = status.confidence,
            rawText = rawText,
            needsReview = needsReview,
        )
    }
}

object ExpenseOcrExtractor {
    private val MERCHANT_LINE = Regex("""^[A-ZÄÖÜ][\wÄÖÜäöüß&.\-\s]{2,40}$""")

    fun extract(rawText: String): ExpenseDraft {
        val amount = AmountParser.extractAmount(rawText)
        val date = GermanDateParser.extractDate(rawText)
        val merchant = guessMerchant(rawText)
        val category = CategorySuggester.suggest(merchant) ?: CategorySuggester.suggest(rawText)

        val needsReview = amount == null || (amount.confidence < REVIEW_CONFIDENCE_THRESHOLD) || date == null || merchant == null

        return ExpenseDraft(
            date = date,
            merchant = merchant,
            suggestedCategory = category,
            grossAmountCents = amount?.amountCents,
            amountConfidence = amount?.confidence,
            rawText = rawText,
            needsReview = needsReview,
        )
    }

    /** Sehr einfache Heuristik: die erste "titel-artige" Zeile eines Kassenbons ist meist der Händlername. */
    private fun guessMerchant(rawText: String): String? =
        rawText.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && MERCHANT_LINE.matches(it) }
}
