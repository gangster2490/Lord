package de.tiktokshop.buchhaltung.ocr

import de.tiktokshop.buchhaltung.data.model.IncomeStatus

data class StatusClassification(
    val status: IncomeStatus,
    val confidence: Float,
    val matchedKeyword: String?,
)

/**
 * Klassifiziert den Auszahlungsstatus einer TikTok-Provision aus OCR-Text, siehe
 * OCR_RULES.md. Reihenfolge ist bewusst FROZEN vor AVAILABLE: ein Screenshot kann das
 * Wort "verfügbar" enthalten (z. B. als Label "Verfügbare Auszahlung"), obwohl die
 * Provision tatsächlich eingefroren ist (TC01) - ein klares FROZEN-Signal darf niemals
 * von einem schwächeren AVAILABLE-Treffer überschrieben werden.
 */
object StatusClassifier {

    private val REVERSED_KEYWORDS = listOf(
        "storniert", "zurückgebucht", "reversed", "cancelled commission", "canceled commission",
    )
    private val REFUNDED_KEYWORDS = listOf(
        "rückerstattet", "erstattet", "refunded", "refund issued", "erstattung",
    )
    private val FROZEN_KEYWORDS = listOf(
        "eingefroren", "abheben nicht möglich", "withdrawal unavailable", "frozen",
    )
    private val PAID_OUT_KEYWORDS = listOf(
        "ausgezahlt", "payout completed", "withdrawn",
    )
    private val AVAILABLE_KEYWORDS = listOf(
        "verfügbar", "available for withdrawal", "auszahlbar",
    )

    fun classify(rawText: String): StatusClassification {
        val normalized = rawText.lowercase()

        findFirstMatch(normalized, REVERSED_KEYWORDS)?.let {
            return StatusClassification(IncomeStatus.REVERSED, 0.9f, it)
        }
        findFirstMatch(normalized, REFUNDED_KEYWORDS)?.let {
            return StatusClassification(IncomeStatus.REFUNDED, 0.9f, it)
        }
        findFirstMatch(normalized, FROZEN_KEYWORDS)?.let {
            return StatusClassification(IncomeStatus.FROZEN, 0.9f, it)
        }
        findFirstMatch(normalized, PAID_OUT_KEYWORDS)?.let {
            return StatusClassification(IncomeStatus.PAID_OUT, 0.9f, it)
        }
        findFirstMatch(normalized, AVAILABLE_KEYWORDS)?.let {
            return StatusClassification(IncomeStatus.AVAILABLE, 0.85f, it)
        }

        // Provision sichtbar, aber ohne sicheren Nachweis für Auszahlung -> ACCRUED.
        return StatusClassification(IncomeStatus.ACCRUED, 0.4f, matchedKeyword = null)
    }

    private fun findFirstMatch(normalized: String, keywords: List<String>): String? =
        keywords.firstOrNull { normalized.contains(it) }
}
