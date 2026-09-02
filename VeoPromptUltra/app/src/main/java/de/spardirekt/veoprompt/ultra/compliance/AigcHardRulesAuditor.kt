package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.FinalPromptValidator
import de.spardirekt.veoprompt.ultra.model.ProductModel

/**
 * Deterministic AIGC hard-rule pass.
 * Appends CRITICAL / NEGATIVE identity lines only. Never shortens veoPrompt.
 * Findings go to safetyAudit, never into veoPrompt.
 */
object AigcHardRulesAuditor {

    data class Finding(
        val code: String,
        val severity: String,
        val field: String,
        val message: String
    )

    data class Result(
        val prompt: String,
        val findings: List<Finding>
    )

    fun audit(
        prompt: String,
        voiceover: String,
        title: String,
        overlay: String,
        @Suppress("UNUSED_PARAMETER") productModel: ProductModel,
        tiktokShopMode: Boolean
    ): Result {
        val spoken = listOf(voiceover, title, overlay).joinToString("\n")
        val positivePrompt = prompt.lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("- no", ignoreCase = true) || it.startsWith("no ", ignoreCase = true) }
            .joinToString("\n")
        val packageText = "$spoken\n$positivePrompt"
        val findings = mutableListOf<Finding>()

        if (tiktokShopMode) {
            findings += Finding(
                code = "AIGC_DISCLOSE",
                severity = "INFO",
                field = "package",
                message = "VEO-ролик полностью AI-generated. При публикации включите переключатель TikTok «AI-generated content»."
            )
        }

        findings += scan(spoken, "voiceover", AigcHardRules.BANNED_IMPERSONATION, "AIGC_NO_IMPERSONATE")
        findings += scan(spoken, "voiceover", AigcHardRules.BANNED_FALSE_ENDORSE, "AIGC_NO_FALSE_ENDORSE")
        findings += scan(spoken, "voiceover", AigcHardRules.BANNED_UNREALISTIC, "AIGC_NO_UNREALISTIC")
        findings += scan(packageText, "veoPrompt", AigcHardRules.BANNED_PRODUCT_ALTER, "AIGC_NO_PRODUCT_ALTER")
        findings += scan(packageText, "veoPrompt", AigcHardRules.BANNED_FAKE_FEATURES, "AIGC_NO_FAKE_FEATURES")
        findings += scan(packageText, "package", AigcHardRules.BANNED_FEAR, "AIGC_NO_FEAR")
        findings += scan(packageText, "package", AigcHardRules.BANNED_IP, "AIGC_NO_IP")

        val enriched = enrichPrompt(prompt)
        return Result(prompt = enriched, findings = findings.distinctBy { it.code + it.message })
    }

    fun enrichPrompt(prompt: String): String {
        if (prompt.isBlank()) return prompt
        var out = prompt
        out = appendMissingLines(out, "CRITICAL", AigcHardRules.CRITICAL_LOCK_LINES)
        out = appendMissingLines(out, "NEGATIVE PROMPT", AigcHardRules.NEGATIVE_BULLETS)
        return out
    }

    private fun appendMissingLines(prompt: String, header: String, lines: List<String>): String {
        val body = FinalPromptValidator.sectionBody(prompt, header)
        val missing = lines.filter { line ->
            val token = line.removePrefix("- ").trim()
            token.isNotBlank() && !body.contains(token, ignoreCase = true)
        }
        if (missing.isEmpty()) return prompt
        val merged = if (body.isBlank()) missing.joinToString("\n") else body.trimEnd() + "\n" + missing.joinToString("\n")
        val sections = FinalPromptValidator.parseSections(prompt).toMutableMap()
        sections[header] = merged
        return FinalPromptValidator.REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            val content = sections[name].orEmpty().trim()
            if (content.isEmpty()) name else "$name\n$content"
        }.trim()
    }

    private fun scan(
        text: String,
        field: String,
        phrases: List<String>,
        code: String
    ): List<Finding> {
        val lower = text.lowercase()
        return phrases.mapNotNull { phrase ->
            if (lower.contains(phrase.lowercase())) {
                Finding(code, "HIGH", field, "AIGC hard rule: «$phrase».")
            } else {
                null
            }
        }
    }
}
