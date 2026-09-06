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
        val (cleanAnalysis, cleanFingerprint) = CrossProductGuard.clean(analysis, fingerprint)
        val scene = session.scene ?: JSONObject()
        val category = (cleanAnalysis ?: analysis).optString("product_category").ifBlank { "—" }
        val use = (cleanAnalysis ?: analysis).optString("observed_use_case").ifBlank {
            (cleanAnalysis ?: analysis).optString("inferred_use_case").ifBlank { "—" }
        }
        val visual = join(de.spardirekt.ugcagent.v3.prompt.EvidenceModel.visuallyConfirmed(session.evidence, cleanAnalysis ?: analysis))
        val text = join(de.spardirekt.ugcagent.v3.prompt.EvidenceModel.verifiedReliable(session.evidence, cleanAnalysis ?: analysis))
        val identitySource = cleanFingerprint ?: fingerprint
        val identity = identitySource.optString("overall_geometry").ifBlank {
            join(JsonExtractor.stringList(identitySource, "identity_critical_components"))
        }
        val action = scene.optString("main_action").ifBlank { ActionIdentity.recommendedSafeAction(cleanFingerprint ?: fingerprint) }
        val firstFrame = session.firstFrameId?.take(8) ?: "—"
        val plan = CreativeStrategyEngine.plan(cleanAnalysis, cleanFingerprint)
        val idea = CreativeStrategyEngine.ideaLabel(plan, russian)
        val warnings = session.warnings.filter { it.isNotBlank() && !isInternal(it) }.distinct().take(6)
        val body = if (russian) {
            buildString {
                appendLine("Категория товара: $category")
                appendLine("Основное применение: $use")
                appendLine("Идея ролика: $idea")
                if (plan.desire.isNotBlank()) appendLine("Почему берут: ${plan.desire}")
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
                appendLine("Videoidée: $idea")
                if (plan.desire.isNotBlank()) appendLine("Kaufgrund: ${plan.desire}")
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

    fun videoPackage(prompt: String, caption: String, hashtags: List<String>): String {
        return listOf(
            prompt.trim(),
            caption.trim(),
            hashtags.joinToString(" ").trim(),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun copyAll(details: String, prompt: String, caption: String, hashtags: List<String>): String {
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
            lower.contains("hidden geometry") ||
            lower.contains("dominant product identity") ||
            lower.contains("repeated images grouped") ||
            lower.contains("prompt quality")
    }
}
