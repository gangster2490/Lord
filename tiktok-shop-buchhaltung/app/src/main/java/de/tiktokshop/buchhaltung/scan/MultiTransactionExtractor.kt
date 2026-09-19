package de.tiktokshop.buchhaltung.scan

import android.net.Uri
import de.tiktokshop.buchhaltung.ai.AiVisionProvider
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.repository.RuleLearningRepository
import de.tiktokshop.buchhaltung.ocr.TextRecognizerEngine
import de.tiktokshop.buchhaltung.ocr.TransactionGrouper

/** Ergebnis eines Scans: die erkannten Kandidaten plus Transparenz darüber, wie sie entstanden sind. */
data class ExtractionResult(
    val candidates: List<TransactionCandidate>,
    val usedAiFallback: Boolean,
    val warning: String?,
)

/**
 * Orchestriert die Mehrfach-Transaktionserkennung eines Screenshots:
 * 1. ML Kit OCR (lokal, on-device) mit Zeilen-Koordinaten.
 * 2. Visuelle Gruppierung der Zeilen zu Transaktionsblöcken ([TransactionGrouper]).
 * 3. Feldextraktion pro Block ([TransactionFieldExtractor]).
 * 4. Anwenden gelernter Regeln ([RuleLearningRepository]).
 * 5. Nur bei unsicherem/leerem lokalen Ergebnis: AI-Vision-Fallback über den Backend-Proxy.
 *
 * Speichert NICHTS selbst - liefert nur Kandidaten für den Review-Screen (OCR_RULES.md).
 */
class MultiTransactionExtractor(
    private val textRecognizerEngine: TextRecognizerEngine,
    private val aiVisionProvider: AiVisionProvider,
    private val ruleLearningRepository: RuleLearningRepository,
) {
    suspend fun extract(imageUri: Uri): ExtractionResult {
        val document = textRecognizerEngine.recognizeStructured(imageUri)
        val groups = TransactionGrouper.group(document.lines)
        val localCandidates = groups
            .mapNotNull { TransactionFieldExtractor.extract(it) }
            .map { applyLearnedRule(it) }

        val needsAiFallback = localCandidates.isEmpty() ||
            localCandidates.count { it.needsReview } > localCandidates.size / 2

        if (!needsAiFallback) {
            return ExtractionResult(candidates = localCandidates, usedAiFallback = false, warning = null)
        }

        val aiResult = aiVisionProvider.analyzeReceipt(imageUri)
        return aiResult.fold(
            onSuccess = { aiCandidates ->
                if (aiCandidates.isNotEmpty()) {
                    ExtractionResult(
                        candidates = aiCandidates.map { applyLearnedRule(it) },
                        usedAiFallback = true,
                        warning = null,
                    )
                } else {
                    ExtractionResult(candidates = localCandidates, usedAiFallback = false, warning = null)
                }
            },
            onFailure = {
                ExtractionResult(
                    candidates = localCandidates,
                    usedAiFallback = false,
                    warning = if (localCandidates.isEmpty()) {
                        "Keine Transaktionen erkannt. Bitte manuell erfassen oder erneut versuchen."
                    } else {
                        null
                    },
                )
            },
        )
    }

    private suspend fun applyLearnedRule(candidate: TransactionCandidate): TransactionCandidate {
        val rule = ruleLearningRepository.suggestFor(candidate.merchant, candidate.entryType) ?: return candidate
        return when (candidate.entryType) {
            EntryType.EXPENSE -> candidate.copy(
                category = rule.categoryName?.let { name -> runCatching { ExpenseCategory.valueOf(name) }.getOrNull() }
                    ?: candidate.category,
                businessUsePercent = rule.businessUsePercent ?: candidate.businessUsePercent,
            )
            EntryType.INCOME -> candidate.copy(
                incomeType = rule.incomeType ?: candidate.incomeType,
                businessUsePercent = rule.businessUsePercent ?: candidate.businessUsePercent,
            )
        }
    }
}
