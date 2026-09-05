package de.spardirekt.ugcagent.v3.prompt

import de.spardirekt.ugcagent.v3.ai.JsonExtractor
import de.spardirekt.ugcagent.v3.compliance.MarketplaceFilter
import de.spardirekt.ugcagent.v3.pipeline.PipelineSession
import org.json.JSONObject

object DetailsBuilder {
    fun build(session: PipelineSession): String {
        val russian = isRussian(session.speechLanguage, session.captionLanguage)
        val analysis = session.analysis ?: JSONObject()
        val fingerprint = session.identityFingerprint ?: JSONObject()
        val scene = session.scene ?: JSONObject()
        val category = analysis.optString("product_category").ifBlank { "—" }
        val use = analysis.optString("observed_use_case").ifBlank { analysis.optString("inferred_use_case").ifBlank { "—" } }
        val visual = join(JsonExtractor.stringList(analysis, "visual_features_relevant_to_use"))
        val text = join(JsonExtractor.stringList(analysis, "text_claims"))
        val identity = fingerprint.optString("overall_geometry").ifBlank {
            join(JsonExtractor.stringList(fingerprint, "identity_critical_components"))
        }
        val action = scene.optString("main_action").ifBlank { ActionIdentity.recommendedSafeAction(fingerprint) }
        val firstFrame = session.firstFrameId?.take(8) ?: "—"
        val warnings = session.warnings.filter { it.isNotBlank() && !isInternal(it) }.distinct().take(6)
        val body = if (russian) {
            buildString {
                appendLine("Категория товара: $category")
                appendLine("Основное применение: $use")
                appendLine("Подтверждённые видимые функции: ${visual.ifBlank { "—" }}")
                appendLine("Подтверждённые текстовые признаки: ${text.ifBlank { "—" }}")
                appendLine("Ключевые детали идентичности: ${identity.ifBlank { "—" }}")
                appendLine("Выбранный First Frame: $firstFrame")
                appendLine("Безопасное действие: $action")
                if (warnings.isNotEmpty()) {
                    appendLine("Важные предупреждения:")
                    warnings.forEach { appendLine("- $it") }
                }
            }
        } else {
            buildString {
                appendLine("Produktkategorie: $category")
                appendLine("Hauptnutzung: $use")
                appendLine("Bestätigte sichtbare Funktionen: ${visual.ifBlank { "—" }}")
                appendLine("Bestätigte Textmerkmale: ${text.ifBlank { "—" }}")
                appendLine("Wichtige Identitätsdetails: ${identity.ifBlank { "—" }}")
                appendLine("Gewähltes First Frame: $firstFrame")
                appendLine("Sichere Aktion: $action")
                if (warnings.isNotEmpty()) {
                    appendLine("Wichtige Hinweise:")
                    warnings.forEach { appendLine("- $it") }
                }
            }
        }
        return MarketplaceFilter.stripFromText(body.trim())
    }

    fun videoPackage(details: String, prompt: String, caption: String, hashtags: List<String>): String {
        return listOf(
            details.trim(),
            prompt.trim(),
            caption.trim(),
            hashtags.joinToString(" ").trim(),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun isRussian(speech: String, caption: String): Boolean =
        speech.equals("РУССКИЙ", true) || caption.equals("РУССКИЙ", true)

    private fun join(items: List<String>): String =
        items.map { it.trim() }.filter { it.isNotBlank() && !isInternal(it) }.distinct().take(8).joinToString("; ")

    private fun isInternal(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("uncertain_hidden") ||
            lower.contains("ambiguity_warning") ||
            lower.contains("confidence") && lower.contains("0.") ||
            lower.contains("hidden geometry")
    }
}
