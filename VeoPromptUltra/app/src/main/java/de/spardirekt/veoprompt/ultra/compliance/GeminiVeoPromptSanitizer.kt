package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.FinalPromptValidator
import de.spardirekt.veoprompt.ultra.model.GeminiVeoReport
import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage

/**
 * Deterministic Gemini / VEO submission sanitizer.
 *
 * May repair voiceover / title / overlays and rewrite banned phrases in
 * mutable sections. Appends CRITICAL / NEGATIVE lock lines only.
 * Never mechanically shortens veoPrompt with take/substring/ellipsis.
 * Never writes GEMINI AUDIT into veoPrompt.
 */
object GeminiVeoPromptSanitizer {

    data class Finding(
        val code: String,
        val severity: String,
        val field: String,
        val message: String,
        val repaired: Boolean = false
    )

    data class Result(
        val response: StructuredResponse,
        val report: GeminiVeoReport,
        val findings: List<Finding>
    )

    private val MUTABLE_SECTIONS = setOf(
        "SETTING", "SHOT SEQUENCE", "ON-SCREEN TEXT", "VOICEOVER", "AUDIO"
    )

    fun sanitize(
        response: StructuredResponse,
        productModel: ProductModel,
        voice: VoiceLanguage
    ): Result {
        val findings = mutableListOf<Finding>()
        findings += Finding(
            code = "GV_SUBMIT",
            severity = "INFO",
            field = "package",
            message = "Полный veoPrompt копируется в Gemini / VEO. Держите пакет product-only."
        )

        var voiceover = response.voiceover
        var title = response.title
        var prompt = response.veoPrompt
        val overlay = FinalPromptValidator.sectionBody(prompt, "ON-SCREEN TEXT")
        val positivePrompt = prompt.lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("- no", ignoreCase = true) || it.startsWith("no ", ignoreCase = true) }
            .joinToString("\n")
        val packageText = listOf(voiceover, title, overlay, positivePrompt).joinToString("\n")

        GeminiVeoPolicy.allBannedPhrases().forEach { (code, phrase) ->
            if (containsPhrase(packageText, phrase)) {
                val field = when {
                    containsPhrase(voiceover, phrase) -> "voiceover"
                    containsPhrase(title, phrase) -> "title"
                    containsPhrase(overlay, phrase) -> "onScreenText"
                    else -> "veoPrompt"
                }
                findings += Finding(
                    code = code,
                    severity = GeminiVeoPolicy.severityOf(code),
                    field = field,
                    message = "Gemini / VEO filter: «$phrase»."
                )
            }
        }

        prompt = enrichPrompt(prompt)

        val cleanedVo = sanitizeSpoken(voiceover)
        if (cleanedVo != voiceover) {
            markRepaired(findings, "voiceover")
            voiceover = cleanedVo
        }
        val cleanedTitle = sanitizeSpoken(title)
        if (cleanedTitle != title) {
            markRepaired(findings, "title")
            title = cleanedTitle
        }

        val cleanedOverlay = sanitizeOverlay(overlay)
        if (cleanedOverlay != overlay) {
            markRepaired(findings, "onScreenText")
            prompt = replaceSection(prompt, "ON-SCREEN TEXT", cleanedOverlay)
        }

        val rewritten = rewriteMutableSections(prompt)
        if (rewritten != prompt) {
            markRepaired(findings, "veoPrompt")
            prompt = rewritten
        }

        if (voice != VoiceLanguage.OFF && voiceover.isNotBlank()) {
            prompt = replaceSection(prompt, "VOICEOVER", voiceover)
        }

        val unique = findings.distinctBy { it.code + it.message }
        val report = GeminiVeoComplianceSystem.evaluate(unique, prompt, productModel)
        return Result(
            response = response.copy(
                veoPrompt = prompt,
                voiceover = voiceover,
                title = title
            ),
            report = report,
            findings = unique
        )
    }

    fun enrichPrompt(prompt: String): String {
        if (prompt.isBlank()) return prompt
        var out = prompt
        out = appendMissingLines(out, "CRITICAL", GeminiVeoPolicy.CRITICAL_LOCK_LINES)
        out = appendMissingLines(out, "NEGATIVE PROMPT", GeminiVeoPolicy.NEGATIVE_BULLETS)
        return out
    }

    internal fun containsPhrase(text: String, phrase: String): Boolean {
        if (text.isBlank() || phrase.isBlank()) return false
        return phraseRegex(phrase).containsMatchIn(text)
    }

    private fun sanitizeSpoken(text: String): String {
        if (text.isBlank() || text.equals("OFF", true)) return text
        var out = replaceBanned(text)
        out = out.replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .trim()
        if (out.isBlank()) return GeminiVeoPolicy.SAFE_FALLBACK_VOICE
        return out
    }

    private fun sanitizeOverlay(overlay: String): String {
        if (overlay.isBlank()) return overlay
        val lines = overlay.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val kept = lines.map { replaceBanned(it).trim() }.filter { it.isNotEmpty() }
        return if (kept.isEmpty()) "None" else kept.joinToString("\n")
    }

    private fun rewriteMutableSections(prompt: String): String {
        val sections = FinalPromptValidator.parseSections(prompt).toMutableMap()
        var changed = false
        MUTABLE_SECTIONS.forEach { header ->
            val body = sections[header].orEmpty()
            if (body.isBlank()) return@forEach
            val cleaned = replaceBanned(body)
            if (cleaned != body) {
                sections[header] = cleaned.trim().ifBlank { body }
                changed = true
            }
        }
        if (!changed) return prompt
        return rebuild(sections)
    }

    private fun replaceBanned(text: String): String {
        var out = text
        GeminiVeoPolicy.REPLACEMENTS.forEach { (from, to) ->
            out = phraseRegex(from).replace(out, to)
        }
        GeminiVeoPolicy.allBannedPhrases().forEach { (_, phrase) ->
            if (GeminiVeoPolicy.REPLACEMENTS.any { it.first.equals(phrase, true) }) return@forEach
            out = phraseRegex(phrase).replace(out, "")
        }
        return out.replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex(" ?\\n ?"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
    }

    private fun phraseRegex(phrase: String): Regex =
        Regex("(?i)(?<!\\p{L})${Regex.escape(phrase)}(?!\\p{L})")

    private fun appendMissingLines(prompt: String, header: String, lines: List<String>): String {
        val body = FinalPromptValidator.sectionBody(prompt, header)
        val missing = lines.filter { line ->
            val token = line.removePrefix("- ").trim()
            token.isNotBlank() && !body.contains(token, ignoreCase = true)
        }
        if (missing.isEmpty()) return prompt
        val merged = if (body.isBlank()) {
            missing.joinToString("\n")
        } else {
            body.trimEnd() + "\n" + missing.joinToString("\n")
        }
        val sections = FinalPromptValidator.parseSections(prompt).toMutableMap()
        sections[header] = merged
        return rebuild(sections)
    }

    private fun replaceSection(prompt: String, header: String, body: String): String {
        val sections = FinalPromptValidator.parseSections(prompt).toMutableMap()
        sections[header] = body.trim()
        return rebuild(sections)
    }

    private fun rebuild(sections: Map<String, String>): String =
        FinalPromptValidator.REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            val content = sections[name].orEmpty().trim()
            if (content.isEmpty()) name else "$name\n$content"
        }.trim()

    private fun markRepaired(findings: MutableList<Finding>, field: String) {
        for (i in findings.indices) {
            if (findings[i].code == "GV_SUBMIT") continue
            if (findings[i].field == field ||
                (field == "veoPrompt" && findings[i].field == "veoPrompt")
            ) {
                findings[i] = findings[i].copy(repaired = true)
            }
        }
    }
}
