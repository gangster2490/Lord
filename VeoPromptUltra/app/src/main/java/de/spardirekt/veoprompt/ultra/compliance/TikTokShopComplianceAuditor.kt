package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.FinalPromptValidator
import de.spardirekt.veoprompt.ultra.generation.VoiceoverRules
import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.SafetyAudit
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage

/**
 * Deterministic compliance pass.
 * May repair voiceover / title / overlays only. Never mechanically shortens veoPrompt.
 * Safety audit is stored separately and never written into veoPrompt.
 */
object TikTokShopComplianceAuditor {

    data class Finding(
        val code: String,
        val severity: String,
        val field: String,
        val message: String,
        val repaired: Boolean = false
    )

    data class Result(
        val response: StructuredResponse,
        val audit: SafetyAudit
    )

    fun audit(
        response: StructuredResponse,
        productModel: ProductModel,
        voice: VoiceLanguage,
        tiktokShopMode: Boolean
    ): Result {
        val findings = mutableListOf<Finding>()
        var voiceover = response.voiceover
        var title = response.title
        var prompt = response.veoPrompt
        val overlay = FinalPromptValidator.sectionBody(prompt, "ON-SCREEN TEXT")
        val spoken = listOf(voiceover, title, overlay).joinToString("\n")

        if (tiktokShopMode) {
            findings += Finding(
                code = "AI_LABEL",
                severity = "INFO",
                field = "package",
                message = "VEO-видео нужно пометить как AI-generated при публикации, если платформа этого требует."
            )
        }

        findings += scanPhrases(spoken, "voiceover", TikTokShopPolicy.BANNED_SUPERLATIVES, "CL_SUPERLATIVE", "HIGH")
        findings += scanPhrases(spoken, "voiceover", TikTokShopPolicy.BANNED_MEDICAL, "CL_MEDICAL", "HIGH")
        findings += scanPhrases(spoken, "voiceover", TikTokShopPolicy.BANNED_SYMPATHY, "PR_SYMPATHY", "HIGH")
        findings += scanPhrases(spoken, "voiceover", TikTokShopPolicy.BANNED_HARD_CTA, "PR_FORCED_CTA", "MEDIUM")
        findings += scanPhrases(spoken, "voiceover", TikTokShopPolicy.BANNED_URGENCY, "PR_URGENCY", "MEDIUM")
        findings += scanPhrases(spoken, "package", TikTokShopPolicy.BANNED_OFF_PLATFORM, "PR_OFF_PLATFORM", "HIGH")
        findings += scanPhrases(spoken, "package", TikTokShopPolicy.BANNED_POLITICAL, "PR_POLITICAL", "HIGH")
        findings += scanPhrases(spoken, "package", TikTokShopPolicy.BANNED_HARMFUL, "PR_HARMFUL", "HIGH")

        if (TikTokShopPolicy.PRICE_REGEX.containsMatchIn(spoken) ||
            TikTokShopPolicy.PRICE_REGEX.containsMatchIn(overlay)
        ) {
            findings += Finding("PR_PRICE_UI", "HIGH", "onScreenText", "Найдены цена, скидка или marketplace CTA.")
        }

        val highFacts = highConfidenceBlob(productModel)
        TikTokShopPolicy.REGULATED_UNLESS_HIGH.forEach { term ->
            val hit = spoken.contains(term, ignoreCase = true)
            if (hit && !highFacts.contains(term.lowercase())) {
                findings += Finding(
                    "CL_UNSUPPORTED",
                    "HIGH",
                    "voiceover",
                    "Неподтверждённое утверждение: «$term»."
                )
            }
        }

        VoiceoverRules.issues(voiceover, voice).forEach { issue ->
            val severity = if (issue.startsWith("voiceover_word_count")) "INFO" else "MEDIUM"
            findings += Finding("CQ_VOICEOVER", severity, "voiceover", "Озвучка: $issue")
        }

        if (!prompt.contains("same single physical product", ignoreCase = true) &&
            !prompt.contains("unchanged", ignoreCase = true)
        ) {
            findings += Finding("CQ_ACCURACY", "HIGH", "veoPrompt", "Нет явного правила same-object / unchanged product.")
        }

        val hook = FinalPromptValidator.sectionBody(prompt, "SHOT SEQUENCE")
        if (hook.contains("food only", ignoreCase = true) ||
            hook.contains("environment-only", ignoreCase = true)
        ) {
            findings += Finding("CQ_FIRST_FRAME", "MEDIUM", "veoPrompt", "Хук скрывает товар.")
        }

        if (tiktokShopMode) {
            val cleanedVo = sanitizeField(voiceover, voice)
            if (cleanedVo != voiceover) {
                markRepaired(findings, "voiceover")
                voiceover = cleanedVo
            }
            val cleanedTitle = sanitizeField(title, voice)
            if (cleanedTitle != title) {
                markRepaired(findings, "title")
                title = cleanedTitle
            }
            val cleanedOverlay = sanitizeOverlay(overlay)
            if (cleanedOverlay != overlay) {
                markRepaired(findings, "onScreenText")
                prompt = replaceSection(prompt, "ON-SCREEN TEXT", cleanedOverlay)
            }
            if (voice != VoiceLanguage.OFF) {
                prompt = replaceSection(prompt, "VOICEOVER", voiceover)
            }
        }

        val unique = findings.distinctBy { it.code + it.message }
        val risk = when {
            unique.any { it.severity == "HIGH" && !it.repaired } -> "HIGH"
            unique.any { it.severity == "MEDIUM" && !it.repaired } -> "MEDIUM"
            unique.any { it.severity == "HIGH" } -> "MEDIUM"
            unique.any { it.severity != "INFO" } -> "LOW"
            else -> "LOW"
        }
        val items = unique.map { finding ->
            val mark = if (finding.repaired) "исправлено" else finding.severity
            "${finding.code} · $mark · ${finding.message}"
        }
        val audit = SafetyAudit(
            riskLevel = risk,
            items = items,
            policyVersion = TikTokShopPolicy.VERSION
        )
        return Result(
            response = response.copy(
                veoPrompt = prompt,
                voiceover = voiceover,
                title = title,
                safetyAudit = audit
            ),
            audit = audit
        )
    }

    private fun scanPhrases(
        text: String,
        field: String,
        phrases: List<String>,
        code: String,
        severity: String
    ): List<Finding> {
        val lower = text.lowercase()
        return phrases.mapNotNull { phrase ->
            if (lower.contains(phrase.lowercase())) {
                Finding(code, severity, field, "Запрещённая формулировка: «$phrase».")
            } else {
                null
            }
        }
    }

    private fun sanitizeField(text: String, @Suppress("UNUSED_PARAMETER") voice: VoiceLanguage): String {
        if (text.isBlank() || text.equals("OFF", true)) return text
        var out = text
        val banned = TikTokShopPolicy.BANNED_SUPERLATIVES +
            TikTokShopPolicy.BANNED_URGENCY +
            TikTokShopPolicy.BANNED_MEDICAL +
            TikTokShopPolicy.BANNED_SYMPATHY +
            TikTokShopPolicy.BANNED_HARD_CTA +
            TikTokShopPolicy.BANNED_OFF_PLATFORM
        banned.forEach { phrase ->
            out = out.replace(Regex("(?i)${Regex.escape(phrase)}"), "")
        }
        out = out.replace(TikTokShopPolicy.PRICE_REGEX, "")
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .trim()
        if (out.isBlank()) return text
        return out
    }

    private fun sanitizeOverlay(overlay: String): String {
        if (overlay.isBlank()) return overlay
        val lines = overlay.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val kept = lines.filterNot { line ->
            TikTokShopPolicy.PRICE_REGEX.containsMatchIn(line) ||
                TikTokShopPolicy.BANNED_URGENCY.any { line.contains(it, ignoreCase = true) } ||
                TikTokShopPolicy.BANNED_SUPERLATIVES.any { line.contains(it, ignoreCase = true) }
        }
        return if (kept.isEmpty()) "None" else kept.joinToString("\n")
    }

    private fun replaceSection(prompt: String, header: String, body: String): String {
        val sections = FinalPromptValidator.parseSections(prompt).toMutableMap()
        sections[header] = body.trim()
        return FinalPromptValidator.REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            val content = sections[name].orEmpty().trim()
            if (content.isEmpty()) name else "$name\n$content"
        }.trim()
    }

    private fun markRepaired(findings: MutableList<Finding>, field: String) {
        for (i in findings.indices) {
            if (findings[i].field == field || findings[i].field == "voiceover" && field == "voiceover") {
                findings[i] = findings[i].copy(repaired = true)
            }
        }
    }

    private fun highConfidenceBlob(model: ProductModel): String {
        return (
            model.visualSignature +
                model.confirmedParts +
                model.confirmedColors +
                model.confirmedMaterials +
                model.confirmedFunctions +
                model.confirmedMarkings +
                model.descriptionEvidence
            ).joinToString(" ").lowercase()
    }
}
