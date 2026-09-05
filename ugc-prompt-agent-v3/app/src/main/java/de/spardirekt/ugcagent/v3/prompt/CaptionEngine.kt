package de.spardirekt.ugcagent.v3.prompt

import de.spardirekt.ugcagent.v3.compliance.ComplianceEngine
import org.json.JSONObject

object CaptionEngine {
    const val RU_PREFERRED =
        "Прозрачная крышка для тарелки помогает закрыть еду во время разогрева и уменьшить количество брызг внутри микроволновки. Прозрачный купол позволяет видеть содержимое."
    const val RU_FALLBACK =
        "Прозрачная крышка для тарелки для использования при разогреве в микроволновке. Прозрачный купол позволяет видеть содержимое."
    const val DE_PREFERRED =
        "Die transparente Tellerabdeckung hilft, Essen beim Aufwärmen abzudecken und Spritzer in der Mikrowelle zu reduzieren. Die durchsichtige Kuppel lässt den Inhalt sehen."
    const val DE_FALLBACK =
        "Transparente Abdeckung für den Teller zum Aufwärmen in der Mikrowelle. Die durchsichtige Kuppel lässt den Inhalt sehen."

    fun finalize(
        raw: String,
        analysis: JSONObject?,
        evidence: JSONObject?,
        fingerprint: JSONObject?,
        language: String,
        appendDisclosure: Boolean = true,
    ): String {
        var text = stripDisclosure(raw)
        text = EvidenceModel.omitUnverified(text)
        if (isUnsafe(text, evidence, analysis) || text.isBlank() || isMicrowaveCover(analysis, fingerprint)) {
            text = safeCaption(analysis, evidence, fingerprint, language)
        }
        if (appendDisclosure && isCommercialLanguage(language)) {
            text = ComplianceEngine.addDisclosure(text, language)
        }
        return text.trim()
    }

    fun safeCaption(
        analysis: JSONObject?,
        evidence: JSONObject?,
        fingerprint: JSONObject?,
        language: String,
    ): String {
        val russian = language.equals("РУССКИЙ", true)
        val microwave = isMicrowaveCover(analysis, fingerprint)
        if (microwave) {
            val splashOk = EvidenceModel.splashVisuallyConfirmed(evidence, analysis)
            return when {
                splashOk && russian -> RU_PREFERRED
                splashOk && !russian -> DE_PREFERRED
                russian -> RU_FALLBACK
                else -> DE_FALLBACK
            }
        }
        val visual = EvidenceModel.visuallyConfirmed(evidence, analysis).take(2)
        val category = analysis?.optString("product_category").orEmpty().ifBlank {
            if (russian) "товар" else "Produkt"
        }
        val use = analysis?.optString("observed_use_case").orEmpty()
        val visualLine = visual.joinToString(if (russian) ", " else ", ")
        return if (russian) {
            buildString {
                append("Товар категории $category")
                if (use.isNotBlank() && !EvidenceModel.looksLikePerformanceClaim(use)) append(" для $use")
                append(".")
                if (visualLine.isNotBlank()) append(" Видно: $visualLine.")
            }
        } else {
            buildString {
                append("Produkt der Kategorie $category")
                if (use.isNotBlank() && !EvidenceModel.looksLikePerformanceClaim(use)) append(" für $use")
                append(".")
                if (visualLine.isNotBlank()) append(" Sichtbar: $visualLine.")
            }
        }
    }

    fun isUnsafe(text: String, evidence: JSONObject?, analysis: JSONObject?): Boolean {
        if (text.isBlank()) return true
        if (EvidenceModel.containsUnverifiedClaim(text)) return true
        if (EvidenceModel.containsUnverifiedSplashBenefit(text) && !EvidenceModel.splashVisuallyConfirmed(evidence, analysis)) {
            return true
        }
        if (EvidenceModel.captionContainsSellerClaim(text, evidence, analysis)) return true
        return false
    }

    fun isMicrowaveCover(analysis: JSONObject?, fingerprint: JSONObject?): Boolean {
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) return true
        val use = analysis?.optString("observed_use_case").orEmpty().lowercase()
        val category = analysis?.optString("product_category").orEmpty().lowercase()
        return use.contains("microwave") ||
            use.contains("микроволн") ||
            use.contains("tellerabdeck") ||
            (category.contains("kitchen") && (
                use.contains("cover") ||
                    use.contains("splash") ||
                    use.contains("брызг") ||
                    use.contains("крыш")
                ))
    }

    fun isCommercialLanguage(language: String): Boolean =
        language.equals("DEUTSCH", true) || language.equals("РУССКИЙ", true)

    private fun stripDisclosure(text: String): String {
        return text
            .replace(Regex("(?im)^\\s*(werbung|anzeige)\\s*$"), "")
            .replace(Regex("(?i)\\b(werbung|anzeige)\\b"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
