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
     * fill voiceover OFF, normalize hashtag # prefixes, flatten two-line shot blocks,
     * prepend product-specific NEGATIVE bullets, and strip leaked pan overlays / kitchen
     * setting / Holzdeckel voiceover on non-pan products. Never mechanically cuts veoPrompt.
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
        current = flattenShotSequence(current, productModel)
        current = enrichProductLock(current, productModel)
        current = enrichNegativePrompt(current, productModel)
        current = repairOnScreenText(current, productModel)
        current = repairSetting(current, productModel)
        current = repairVoiceover(current, productModel, voice)
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
        val spec = RegressionLocks.matchingSpec(productModel)
        spec?.negativePrefix?.forEach { bullet ->
            val token = bullet.removePrefix("-").trim()
            if (token.isNotBlank() && !containsDetail(negative, token)) {
                additions += if (bullet.startsWith("-")) bullet else "- $bullet"
            }
        }
        productModel.visualSignature.forEach { detail ->
            val token = detail.trim()
            if (token.isNotBlank() &&
                !containsDetail(negative, token) &&
                !containsDetail(additions.joinToString("\n"), token)
            ) {
                additions += "- no missing or redesigned $token"
            }
        }
        spec?.forbidden?.forEach { banned ->
            if (!containsDetail(negative, banned) &&
                !containsDetail(additions.joinToString("\n"), banned)
            ) {
                additions += "- no $banned"
            }
        }
        if (additions.isEmpty()) return response
        return prependToSection(response, "NEGATIVE PROMPT", additions)
    }

    /**
     * Gemini copy keeps only timed header lines. Merge a following identity
     * sentence onto that line and put product detail first so the 68-char clip
     * still keeps the photographed object.
     */
    internal fun flattenShotSequence(
        response: StructuredResponse,
        productModel: ProductModel
    ): StructuredResponse {
        if (response.veoPrompt.isBlank()) return response
        val shots = sectionBody(response.veoPrompt, "SHOT SEQUENCE")
        if (shots.isBlank()) return response
        val flattened = flattenShotLines(shots, productModel)
        if (flattened == shots.trim()) return response
        return replaceSection(response, "SHOT SEQUENCE", flattened)
    }

    internal fun flattenShotLines(raw: String, productModel: ProductModel): String {
        val headerRe = Regex(
            """(?m)^\s*((?:0\.0|2\.0|4\.0|6\.0)\s*[–\-]\s*(?:2\.0|4\.0|6\.0|8\.0)s[^\n]*)"""
        )
        val matches = headerRe.findAll(raw).toList()
        if (matches.size < 4) return raw.trim()
        val fallback = distinctiveDetails(productModel.visualSignature, 3)
            .joinToString(", ")
        return matches.mapIndexed { index, match ->
            val header = match.groupValues[1].trim()
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: raw.length
            val body = raw.substring(start, end).trim()
            flattenOneShot(header, body, fallback, index)
        }.joinToString("\n")
    }

    private fun flattenOneShot(
        header: String,
        body: String,
        fallback: String,
        index: Int
    ): String {
        val colon = header.indexOf(':')
        val label = if (colon >= 0) header.substring(0, colon).trim() else header
        val existing = if (colon >= 0) header.substring(colon + 1).trim() else ""
        val bodyText = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        val combined = listOf(existing, bodyText).filter { it.isNotBlank() }.joinToString(" ")
        val detail = promoteIdentity(combined, fallback, index)
        return "$label: $detail"
    }

    private val GENERIC_SHOT_PREFIX = Regex(
        """(?i)^(?:Product visible immediately with strongest verified visual detail:\s*|""" +
            """Clear framing of the same exact physical product\.?\s*|""" +
            """Exactly one verified hero feature or physically plausible action with one hand\.?\s*|""" +
            """Stable desirable hero shot of the same unchanged product\.?\s*(?:End exactly at 8\.0s\.?)?\s*)"""
    )

    private fun promoteIdentity(combined: String, fallback: String, index: Int): String {
        if (combined.isBlank()) return defaultShotDetail(fallback, index)
        val prefixMatch = GENERIC_SHOT_PREFIX.find(combined)
        if (prefixMatch != null) {
            val tail = combined.substring(prefixMatch.range.last + 1).trim().trimEnd('.')
            val head = if (tail.length >= 8) tail else fallback
            val identity = head.ifBlank { defaultShotDetail(fallback, index) }
            val rest = combined.trim()
            return if (rest.contains(identity)) {
                identityFirst(identity, rest)
            } else {
                "$identity. $rest"
            }
        }
        if (combined.length < 12 && fallback.isNotBlank()) return fallback
        return combined
    }

    private fun identityFirst(identity: String, original: String): String {
        val stripped = original
            .replace(identity, "", ignoreCase = false)
            .replace(Regex("""(?i)Product visible immediately with strongest verified visual detail:\s*"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim(':', '.', ',', '—', '-')
            .trim()
        return if (stripped.isBlank() || stripped.equals(identity, ignoreCase = true)) {
            identity
        } else {
            "$identity. $stripped"
        }
    }

    private fun distinctiveDetails(details: List<String>, take: Int): List<String> {
        val cleaned = details.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.size <= take) return cleaned
        val first = cleaned.first()
        val ranked = cleaned.drop(1).sortedByDescending { distinctiveScore(it) }
        return (listOf(first) + ranked).distinctBy { it.lowercase() }.take(take)
    }

    private fun distinctiveScore(token: String): Int {
        val t = token.lowercase()
        var score = listOf(
            "lid", "ferrule", "rivet", "hanging", "handle", "bowl",
            "tray", "frame", "collar", "bit", "drain", "plate", "latch"
        ).count { key -> t.contains(key) } * 12 + minOf(t.length, 24)
        if (t.contains("ferrule")) score += 24
        if (t.contains("lid")) score += 20
        if (t.contains("rivet")) score += 8
        return score
    }

    private fun defaultShotDetail(fallback: String, index: Int): String = when (index) {
        0 -> fallback.ifBlank { "product visible with strongest verified detail" }
        1 -> listOf("same unchanged product, full framing", fallback).first { it.isNotBlank() }
        2 -> "one hand, one verified action"
        else -> "stable hero of the same product. End 8.0s"
    }

    private val PAN_OVERLAY_LEAKS = setOf("holzdeckel", "tiefe form")
    private val PAN_VOICEOVER_LEAKS = listOf("holzdeckel", "tiefe form", "tiefer topf")

    internal fun repairOnScreenText(
        response: StructuredResponse,
        productModel: ProductModel
    ): StructuredResponse {
        val spec = RegressionLocks.matchingSpec(productModel) ?: return response
        if (spec.id == RegressionLocks.PAN.id) return response
        val overlays = sectionBody(response.veoPrompt, "ON-SCREEN TEXT")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (overlays.none { it.lowercase() in PAN_OVERLAY_LEAKS }) return response
        val kept = overlays.filterNot { it.lowercase() in PAN_OVERLAY_LEAKS }
        val replacement = if (kept.size >= 2) {
            kept.take(3).joinToString("\n")
        } else {
            spec.overlayLines.joinToString("\n")
        }
        return replaceSection(response, "ON-SCREEN TEXT", replacement)
    }

    internal fun repairSetting(
        response: StructuredResponse,
        productModel: ProductModel
    ): StructuredResponse {
        val spec = RegressionLocks.matchingSpec(productModel) ?: return response
        if (spec.id == RegressionLocks.PAN.id) return response
        val setting = sectionBody(response.veoPrompt, "SETTING")
        val leakedKitchen = setting.contains("premium kitchen", ignoreCase = true)
        if (!leakedKitchen) return response
        return replaceSection(response, "SETTING", spec.setting)
    }

    internal fun repairVoiceover(
        response: StructuredResponse,
        productModel: ProductModel,
        voice: VoiceLanguage
    ): StructuredResponse {
        if (voice == VoiceLanguage.OFF) return response
        val spec = RegressionLocks.matchingSpec(productModel) ?: return response
        if (spec.id == RegressionLocks.PAN.id) return response
        var current = response
        val section = sectionBody(current.veoPrompt, "VOICEOVER")
        if (PAN_VOICEOVER_LEAKS.any { section.contains(it, ignoreCase = true) }) {
            current = replaceSection(current, "VOICEOVER", spec.voiceover)
        }
        if (PAN_VOICEOVER_LEAKS.any { current.voiceover.contains(it, ignoreCase = true) }) {
            current = current.copy(voiceover = spec.voiceover)
        }
        return current
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

    private fun prependToSection(
        response: StructuredResponse,
        header: String,
        extraLines: List<String>
    ): StructuredResponse {
        if (extraLines.isEmpty()) return response
        val existing = sectionBody(response.veoPrompt, header).trim()
        val prefix = extraLines.joinToString("\n")
        val merged = if (existing.isBlank()) prefix else prefix + "\n" + existing
        return if (headerPresent(response.veoPrompt, header)) {
            replaceSection(response, header, merged)
        } else {
            appendSection(response, header, merged)
        }
    }

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
