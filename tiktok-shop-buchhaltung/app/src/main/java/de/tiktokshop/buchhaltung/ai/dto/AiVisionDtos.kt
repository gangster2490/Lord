package de.tiktokshop.buchhaltung.ai.dto

import kotlinx.serialization.Serializable

/**
 * Strenges JSON-Vertrag zwischen Backend-Proxy und App. Jedes Feld außer `transactions`
 * ist nullable: die AI darf fehlende Werte NICHT erfinden, sondern muss `null` zurückgeben
 * (siehe CLAUDE-Vorgabe). Die App zeigt jeden AI-Kandidaten trotzdem nur im Review-Screen an -
 * nie automatisch gespeichert.
 *
 * Erwartetes Schema:
 * ```json
 * {
 *   "transactions": [
 *     {
 *       "date": "2026-07-03",
 *       "merchant": "Google AI Pro",
 *       "amount": 10.99,
 *       "currency": "EUR",
 *       "type": "expense",
 *       "category": "AI-Dienste",
 *       "businessPercentage": 100,
 *       "status": "normal",
 *       "confidence": 0.98
 *     }
 *   ]
 * }
 * ```
 */
@Serializable
data class AiVisionResponseDto(
    val transactions: List<AiVisionTransactionDto> = emptyList(),
)

@Serializable
data class AiVisionTransactionDto(
    val date: String? = null,
    val merchant: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    /** "income" oder "expense". */
    val type: String? = null,
    val category: String? = null,
    val businessPercentage: Int? = null,
    val status: String? = null,
    val confidence: Float? = null,
)
