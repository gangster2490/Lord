package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage

/**
 * The only runtime gate between structured model output and stored veoPrompt.
 * Validates fields. Repairs a single field locally only when that field can be
 * completed without mechanically cutting veoPrompt.
 */
object FinalPromptValidator {

    val REQUIRED_SECTIONS = listOf(
        "FORMAT",
        "REFERENCES",
        "PRODUCT LOCK",
        "SETTING",
        "SHOT SEQUENCE",
        "ON-SCREEN TEXT",
        "VOICEOVER",
        "AUDIO",
        "CRITICAL",
        "NEGATIVE PROMPT"
    )

    private val FORBIDDEN_IN_PROMPT = listOf("TITLE", "HASHTAGS")

    private val TIMED_BLOCKS = listOf(
        Regex("""0\.0\s*[–\-]\s*2\.0s"""),
        Regex("""2\.0\s*[–\-]\s*4\.0s"""),
        Regex("""4\.0\s*[–\-]\s*6\.0s"""),
        Regex("""6\.0\s*[–\-]\s*8\.0s""")
    )

    data class FieldIssue(
        val field: String,
        val reason: String
    )

    data class Report(
        val response: StructuredResponse,
        val issues: List<FieldIssue>
    ) {
        val ok: Boolean get() = issues.isEmpty()
    }

    fun validate(
        response: StructuredResponse,
        productModel: ProductModel,
        voice: VoiceLanguage,
        tiktokShop: Boolean
    ): Report {
        val split = splitLeakedMeta(response)
        val issues = mutableListOf<FieldIssue>()
        val prompt = split.veoPrompt.trim()

        if (prompt.isEmpty()) {
            issues += FieldIssue("veoPrompt", "empty")
        } else {
            val sections = parseSections(prompt)
            REQUIRED_SECTIONS.forEach { name ->
                val body = sections[name].orEmpty().trim()
                if (body.isEmpty()) issues += FieldIssue(sectionField(name), "missing_section:$name")
            }
            if (FORBIDDEN_IN_PROMPT.any { headerPresent(prompt, it) }) {
                issues += FieldIssue("veoPrompt", "title_or_hashtags_inside_prompt")
            }
            val blockHits = TIMED_BLOCKS.count { it.containsMatchIn(prompt) }
            if (blockHits != 4) {
                issues += FieldIssue("veoPrompt", "timed_blocks:$blockHits")
            }
            if (!prompt.contains("8.0s") && !prompt.contains("8.0 s")) {
                issues += FieldIssue("veoPrompt", "missing_8s_end")
            }
            if (TruncationGuard.looksMechanicallyTruncated(prompt) ||
                TruncationGuard.containsBrokenSentence(prompt)
            ) {
                issues += FieldIssue("veoPrompt", "truncated_or_broken")
            }
            val lock = sections["PRODUCT LOCK"].orEmpty()
            if (!isProductSpecificLock(lock, productModel)) {
                issues += FieldIssue("veoPrompt", "product_lock_not_specific")
            }
            if (!lock.contains("unchanged", ignoreCase = true) &&
                !lock.contains("same single physical product", ignoreCase = true)
            ) {
                issues += FieldIssue("veoPrompt", "same_object_rule_missing")
            }
            val audio = sections["AUDIO"].orEmpty()
            if (audio.isBlank()) issues += FieldIssue("veoPrompt", "audio_incomplete")
        }

        val title = split.title.trim()
        if (title.isEmpty()) issues += FieldIssue("title", "empty")

        val tags = normalizeHashtags(split.hashtags, tiktokShop)
        if (tags.size != 5) issues += FieldIssue("hashtags", "count:${tags.size}")

        val voiceover = when (voice) {
            VoiceLanguage.OFF -> "OFF"
            else -> split.voiceover.trim()
        }
        if (voice == VoiceLanguage.OFF) {
            // ok
        } else if (voiceover.isBlank() || voiceover.equals("OFF", true)) {
            issues += FieldIssue("voiceover", "incomplete")
        } else if (TruncationGuard.looksMechanicallyTruncated(voiceover)) {
            issues += FieldIssue("voiceover", "truncated")
        }

        val repaired = split.copy(
            title = title,
            hashtags = tags,
            voiceover = voiceover
        )
        return Report(repaired, issues.distinctBy { it.field + it.reason })
    }

    /**
     * Local, non-destructive field repair: move leaked TITLE/HASHTAGS out of veoPrompt,
     * fill voiceover OFF, normalize hashtag # prefixes, and append missing PRODUCT LOCK
     * / NEGATIVE PROMPT identity lines. Never shortens veoPrompt.
     */
    fun localRepair(
        response: StructuredResponse,
        productModel: ProductModel,
        voice: VoiceLanguage,
        tiktokShop: Boolean
    ): StructuredResponse {
        var current = splitLeakedMeta(response)
        if (voice == VoiceLanguage.OFF) {
            current = current.copy(voiceover = "OFF")
            current = replaceSection(current, "VOICEOVER", "OFF")
        } else if (current.voiceover.isNotBlank()) {
            val voSection = sectionBody(current.veoPrompt, "VOICEOVER")
            if (voSection.isBlank()) {
                current = appendSection(current, "VOICEOVER", current.voiceover)
            }
        }
        current = current.copy(hashtags = normalizeHashtags(current.hashtags, tiktokShop))
        if (current.title.isBlank()) {
            current = current.copy(title = productModel.productIdentity.ifBlank { "Product Ad" })
        }
        current = enrichProductLock(current, productModel)
        current = enrichNegativePrompt(current, productModel)
        return current
    }

    internal fun enrichProductLock(
        response: StructuredResponse,
        productModel: ProductModel
    ): StructuredResponse {
        if (response.veoPrompt.isBlank()) return response
        val lock = sectionBody(response.veoPrompt, "PRODUCT LOCK")
        val additions = mutableListOf<String>()
        productModel.visualSignature.forEach { detail ->
            val token = detail.trim()
            if (token.isNotBlank() && !containsDetail(lock, token)) {
                additions += "Preserve $token."
            }
        }
        val hasSameObject = lock.contains("unchanged", ignoreCase = true) ||
            lock.contains("same single physical product", ignoreCase = true)
        if (!hasSameObject) {
            additions += SAME_OBJECT_SENTENCE
            additions += DO_NOT_REGENERATE_SENTENCE
        }
        if (additions.isEmpty()) return response
        return appendToSection(response, "PRODUCT LOCK", additions)
    }

    internal fun enrichNegativePrompt(
        response: StructuredResponse,
        productModel: ProductModel
    ): StructuredResponse {
        if (response.veoPrompt.isBlank()) return response
        val negative = sectionBody(response.veoPrompt, "NEGATIVE PROMPT")
        val additions = mutableListOf<String>()
        productModel.visualSignature.forEach { detail ->
            val token = detail.trim()
            if (token.isNotBlank() && !containsDetail(negative, token)) {
                additions += "- no missing or redesigned $token"
            }
        }
        RegressionLocks.matchingSpec(productModel)?.forbidden?.forEach { banned ->
            if (!containsDetail(negative, banned)) {
                additions += "- no $banned"
            }
        }
        if (additions.isEmpty()) return response
        return appendToSection(response, "NEGATIVE PROMPT", additions)
    }

    fun failedFields(report: Report): List<String> = report.issues.map { it.field }.distinct()

    fun splitLeakedMeta(response: StructuredResponse): StructuredResponse {
        var prompt = response.veoPrompt.replace("\r\n", "\n")
        var title = response.title.trim()
        var tags = response.hashtags.toList()
        val sections = parseSections(prompt)

        if (title.isBlank()) {
            title = sections["TITLE"].orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        }
        if (tags.isEmpty()) {
            tags = extractHashtags(sections["HASHTAGS"].orEmpty())
        }
        if (sections.containsKey("TITLE") || sections.containsKey("HASHTAGS")) {
            prompt = removeSection(prompt, "TITLE")
        }
        return response.copy(
            veoPrompt = prompt.trim(),
            title = title,
            hashtags = tags
        )
    }

    fun parseSections(prompt: String): Map<String, String> {
        val headers = REQUIRED_SECTIONS + FORBIDDEN_IN_PROMPT
        val pattern = Regex("(?im)^(" + headers.joinToString("|") { Regex.escape(it) } + ")\\s*$")
        val matches = pattern.findAll(prompt).toList()
        if (matches.isEmpty()) return emptyMap()
        val map = linkedMapOf<String, String>()
        matches.forEachIndexed { i, match ->
            val name = match.groupValues[1].uppercase()
            val start = match.range.last + 1
            val end = matches.getOrNull(i + 1)?.range?.first ?: prompt.length
            map[canonicalHeader(name)] = prompt.substring(start, end).trim()
        }
        return map
    }

    fun sectionBody(prompt: String, header: String): String =
        parseSections(prompt)[header].orEmpty()

    private fun headerPresent(prompt: String, header: String): Boolean =
        Regex("(?im)^${Regex.escape(header)}\\s*$").containsMatchIn(prompt)

    private fun removeSection(prompt: String, header: String): String {
        val sections = parseSections(prompt)
        if (!sections.containsKey(header)) return prompt
        val kept = REQUIRED_SECTIONS.mapNotNull { name ->
            val body = sections[name] ?: return@mapNotNull null
            "$name\n$body"
        }
        return kept.joinToString("\n\n").trim()
    }

    private fun replaceSection(
        response: StructuredResponse,
        header: String,
        body: String
    ): StructuredResponse {
        val sections = parseSections(response.veoPrompt).toMutableMap()
        sections[header] = body.trim()
        val rebuilt = REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            val content = sections[name].orEmpty().trim()
            if (content.isEmpty()) name else "$name\n$content"
        }.trim()
        return response.copy(veoPrompt = rebuilt)
    }

    private val SAME_OBJECT_SENTENCE =
        "The same single physical product shown in the uploaded photos must remain unchanged across all four shots."
    private val DO_NOT_REGENERATE_SENTENCE =
        "Do not regenerate a slightly different version of the product for each shot."

    private fun appendToSection(
        response: StructuredResponse,
        header: String,
        extraLines: List<String>
    ): StructuredResponse {
        if (extraLines.isEmpty()) return response
        val existing = sectionBody(response.veoPrompt, header).trimEnd()
        val suffix = extraLines.joinToString("\n")
        val merged = if (existing.isBlank()) suffix else existing + "\n" + suffix
        return if (headerPresent(response.veoPrompt, header)) {
            replaceSection(response, header, merged)
        } else {
            appendSection(response, header, merged)
        }
    }

    private fun containsDetail(text: String, detail: String): Boolean {
        val blob = text.lowercase()
        val needle = detail.trim().lowercase()
        if (needle.isBlank()) return true
        if (blob.contains(needle)) return true
        val tokens = significantTokens(needle)
        return tokens.isNotEmpty() && tokens.all { blob.contains(it) }
    }

    private fun significantTokens(text: String): List<String> =
        text.split(Regex("[^a-zA-Zа-яА-Я0-9]+")).filter { it.length >= 4 }

    private fun appendSection(
        response: StructuredResponse,
        header: String,
        body: String
    ): StructuredResponse {
        if (headerPresent(response.veoPrompt, header)) {
            return replaceSection(response, header, body)
        }
        val joined = (response.veoPrompt.trim() + "\n\n$header\n${body.trim()}").trim()
        return response.copy(veoPrompt = joined)
    }

    private fun canonicalHeader(raw: String): String {
        val u = raw.uppercase()
        return REQUIRED_SECTIONS.firstOrNull { it.equals(u, true) }
            ?: FORBIDDEN_IN_PROMPT.firstOrNull { it.equals(u, true) }
            ?: u
    }

    private fun sectionField(name: String): String = "veoPrompt"

    fun isProductSpecificLock(lock: String, productModel: ProductModel): Boolean {
        if (lock.length < 80) return false
        val blob = lock.lowercase()
        val tokens = productModel.visualSignature
            .flatMap { it.split(Regex("[^a-zA-Zа-яА-Я0-9]+")) }
            .map { it.lowercase() }
            .filter { it.length >= 4 }
        if (tokens.isEmpty()) {
            return blob.contains("silhouette") || blob.contains("proportion") ||
                blob.contains("uploaded")
        }
        val hits = tokens.count { blob.contains(it) }
        return hits >= 2
    }

    fun normalizeHashtags(raw: List<String>, tiktokShop: Boolean): List<String> {
        val cleaned = raw.map { tag ->
            val t = tag.trim().trimStart('#')
                .replace(Regex("[^A-Za-z0-9_А-Яа-яЁё]"), "")
            if (t.isBlank()) "" else "#$t"
        }.filter { it.length > 1 }.distinctBy { it.lowercase() }.toMutableList()
        if (tiktokShop && cleaned.none { it.equals("#TikTokShop", true) }) {
            if (cleaned.size >= 5) cleaned[cleaned.lastIndex] = "#TikTokShop"
            else cleaned += "#TikTokShop"
        }
        while (cleaned.size > 5) cleaned.removeAt(cleaned.lastIndex)
        var i = 1
        while (cleaned.size < 5) {
            cleaned += "#Product$i"
            i++
        }
        return cleaned.take(5)
    }

    fun extractHashtags(text: String): List<String> {
        return Regex("#[A-Za-z0-9_А-Яа-яЁё]+").findAll(text).map { it.value }.toList()
    }
}
