package de.spardirekt.ugcagent.v3.prompt

import de.spardirekt.ugcagent.v3.ai.JsonExtractor
import org.json.JSONObject

object PromptComposer {
    val CANONICAL_HEADINGS = listOf(
        "FORMAT",
        "REFERENCE",
        "PRODUCT IDENTITY LOCK",
        "MOVING COMPONENT LOCK",
        "SETTING",
        "CAMERA",
        "ACTION",
        "HUMAN BEHAVIOUR",
        "LIGHTING",
        "SPEECH",
        "ANTI-MORPH",
        "TIMING",
    )

    val FORBIDDEN_HEADINGS = listOf(
        "FINAL IDENTITY LOCK",
        "STRUCTURAL IDENTITY LOCK",
        "DURATION",
        "STYLE",
        "PRODUCT LOCK",
        "SAFE ACTION",
        "TARGET GENERATOR",
    )

    const val TIMING_BLOCK =
        "0.0–1.5 s: hook and establish product\n" +
            "1.5–6.5 s: one LOW-RISK natural interaction\n" +
            "6.5–8.0 s: natural settle\n" +
            "End exactly at 8.0 seconds.\n" +
            "No intro, outro, CTA, additional scene, freeze-frame or transition tail."

    private val headingLine = Regex(
        "(?im)^(FORMAT|REFERENCE|PRODUCT IDENTITY LOCK|FINAL IDENTITY LOCK|STRUCTURAL IDENTITY LOCK|MOVING COMPONENT LOCK|SETTING|CAMERA|SAFE ACTION|ACTION|HUMAN BEHAVIOUR|HUMAN BEHAVIOR|LIGHTING|SPEECH|ANTI-MORPH|ANTI MORPH|DURATION|TIMING|STYLE|PRODUCT LOCK|TARGET GENERATOR)\\s*:?\\s*$",
    )
    private val dimensionPattern = Regex(
        "(?i)(\\d+[.,]?\\d*\\s*(mm|cm|cm2|cm²|inch|inches|in\\b|дюйм|мм|см)|\\b\\d+\\s*[x×]\\s*\\d+(\\s*(mm|cm|мм|см))?\\b|ø\\s*\\d+|диаметр\\s*\\d+|dimension[s]?\\s*[:=])",
    )

    fun compose(
        raw: String,
        fingerprint: JSONObject? = null,
        generator: String = "VEO",
        speechLanguage: String = "OFF",
        hook: String? = null,
        analysis: JSONObject? = null,
        evidence: JSONObject? = null,
    ): String {
        val first = render(raw, fingerprint, generator, speechLanguage, hook, analysis, evidence)
        if (isCanonical(first, speechLanguage, analysis, evidence, fingerprint)) return first
        val action = extractAction(first).ifBlank { extractAction(raw) }
        val retryRaw = "ACTION:\n$action\n\nSETTING:\nOrdinary cozy home kitchen."
        return render(retryRaw, fingerprint, generator, speechLanguage, hook, analysis, evidence)
    }

    fun isCanonical(
        prompt: String,
        speechLanguage: String = "DEUTSCH",
        analysis: JSONObject? = null,
        evidence: JSONObject? = null,
        fingerprint: JSONObject? = null,
    ): Boolean = canonicalFailures(prompt, speechLanguage, analysis, evidence, fingerprint).isEmpty()

    fun headingCounts(prompt: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        prompt.lineSequence().forEach { line ->
            val heading = canonicalizeHeading(line) ?: return@forEach
            counts[heading] = (counts[heading] ?: 0) + 1
        }
        return counts
    }

    fun rawHeadingCounts(prompt: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        prompt.lineSequence().forEach { line ->
            val heading = rawHeading(line) ?: return@forEach
            counts[heading] = (counts[heading] ?: 0) + 1
        }
        return counts
    }

    fun canonicalFailures(
        prompt: String,
        speechLanguage: String = "DEUTSCH",
        analysis: JSONObject? = null,
        evidence: JSONObject? = null,
        fingerprint: JSONObject? = null,
    ): List<String> {
        val failures = mutableListOf<String>()
        val counts = headingCounts(prompt)
        val raw = rawHeadingCounts(prompt)
        CANONICAL_HEADINGS.forEach { heading ->
            val count = counts[heading] ?: 0
            if (count != 1) failures.add("heading_${heading.replace(' ', '_').lowercase()}_$count")
        }
        FORBIDDEN_HEADINGS.forEach { heading ->
            if ((raw[heading] ?: 0) > 0) failures.add("forbidden_${heading.replace(' ', '_').lowercase()}")
        }
        if (ProductLock.hasConflictingSpokenHooks(prompt)) failures.add("conflicting_spoken_hooks")
        if (antiMorphCount(prompt) > 1) failures.add("repeated_anti_morph")
        if (timingInstructionCount(prompt) > 1) failures.add("repeated_timing")
        if (durationPhraseCount(prompt) > 1) failures.add("repeated_duration_wording")
        if (containsUncertainDimensions(prompt, analysis, evidence)) failures.add("uncertain_dimensions")
        if (ProductLexicon.containsForeign(prompt, fingerprint, analysis, identityAllowedExtra(prompt))) {
            failures.add("foreign_component_leak")
        }
        if (!endsWithTiming(prompt)) failures.add("prompt_does_not_end_with_timing")
        if (Regex("REFERENCE IMAGE OVERRIDES", RegexOption.IGNORE_CASE).findAll(prompt).count() > 1) {
            failures.add("repeated_first_frame_rule")
        }
        if (!speechLanguage.equals("OFF", true) && ProductLock.extractSpokenHooks(prompt).isEmpty()) {
            failures.add("missing_spoken_line")
        }
        if (!speechLanguage.equals("OFF", true) && ProductLock.extractSpokenHooks(prompt).size > 1) {
            failures.add("two_speech_lines")
        }
        val hooks = ProductLock.extractSpokenHooks(prompt)
        if (!speechLanguage.equals("OFF", true) && hooks.any { HookEngine.isWeak(it, speechLanguage, analysis) }) {
            failures.add("weak_hook")
        }
        return failures
    }

    fun extractAction(raw: String): String {
        val sections = parseSections(stripNoise(raw))
        return firstNonBlank(sections["ACTION"], sections["SAFE ACTION"], extractLooseAction(raw), "")
    }

    private fun render(
        raw: String,
        fingerprint: JSONObject?,
        generator: String,
        speechLanguage: String,
        hook: String?,
        analysis: JSONObject?,
        evidence: JSONObject?,
    ): String {
        val cleaned = stripNoise(raw)
        val sections = parseSections(cleaned)
        val action = firstNonBlank(
            sections["ACTION"],
            extractLooseAction(cleaned),
            "One hand uses the referenced product with a LOW-RISK home movement. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry.",
        )
        val setting = firstNonBlank(
            sections["SETTING"],
            "Ordinary cozy home kitchen. Warm, lived-in, slightly imperfect. Not a studio, showroom or commercial set.",
        )
        val resolvedHook = resolveHook(cleaned, hook, speechLanguage, analysis)
        val identity = identityBlock(fingerprint, analysis, evidence, sections["PRODUCT IDENTITY LOCK"])
        val moving = movingBlock(fingerprint, sections["MOVING COMPONENT LOCK"])
        val settingBody = stripDimensions(setting, analysis, evidence)
        val actionBody = stripDimensions(action, analysis, evidence)
        val extraAllowed = "$identity\n$moving\n$settingBody\n$actionBody"
        val body = buildString {
            appendSection("FORMAT", formatBlock(generator))
            appendSection("REFERENCE", referenceBlock(fingerprint))
            appendSection("PRODUCT IDENTITY LOCK", identity)
            appendSection("MOVING COMPONENT LOCK", moving)
            appendSection("SETTING", settingBody)
            appendSection("CAMERA", ProductLock.CAMERA_LOCK + " Warm homely handheld UGC, not a polished commercial move.")
            appendSection("ACTION", actionBody)
            appendSection("HUMAN BEHAVIOUR", ProductLock.HUMAN_LOCK)
            appendSection("LIGHTING", ProductLock.LIGHTING_LOCK)
            appendSection("SPEECH", speechBlock(speechLanguage, resolvedHook))
            appendSection("ANTI-MORPH", ProductLock.antiMorphFor(fingerprint, analysis))
            appendSection("TIMING", TIMING_BLOCK)
        }.trim()
        return ProductLexicon.stripForeign(body, fingerprint, analysis, extraAllowed)
    }

    private fun StringBuilder.appendSection(heading: String, body: String) {
        if (isNotEmpty()) append("\n\n")
        append(heading).append(":\n").append(body.trim())
    }

    private fun formatBlock(generator: String): String {
        val gen = when (generator.uppercase()) {
            "KLING" -> "Target generator: Kling."
            "VEO" -> "Target generator: Veo."
            else -> "Target generator: generic short-form video model."
        }
        return "$gen Vertical 9:16. One continuous natural smartphone UGC clip. Warm, homely, lived-in kitchen feeling. Not a showroom."
    }

    private fun referenceBlock(fingerprint: JSONObject?): String {
        val finish = if (ProductIdentity.hasFinishConflict(fingerprint)) "\n${ProductLock.FIRST_FRAME_WINS}" else ""
        return "Start from the selected original First Frame. Other references are supporting identity evidence. First Frame is the primary source of truth. REFERENCE IMAGE OVERRIDES TEXTUAL INTERPRETATION.$finish"
    }

    private fun identityBlock(
        fingerprint: JSONObject?,
        analysis: JSONObject?,
        evidence: JSONObject?,
        reused: String?,
    ): String {
        if (fingerprint == null && !reused.isNullOrBlank() && reused.length > 40) {
            return semanticDedup(stripDimensions(reused, analysis, evidence))
        }
        val constraints = ProductIdentity.finalIdentityConstraints(fingerprint)
            .filter { !looksLikeDimension(it) }
            .toMutableList()
        listOf(
            "Keep exactly the same single physical product",
            "Preserve the exact number, geometry and relative positions of all identity-critical visible components",
            "Do not merge, split, omit, relocate, simplify or invent components",
            "Do not generate a similar product",
            "Do not generate a generic product from the same category",
            "Do not substitute the reference with a functionally equivalent product",
            "A functionally similar but visually different product is a failed generation",
            "Do not redesign, reinterpret, replace, duplicate or morph it",
        ).forEach { line ->
            if (constraints.none { similarPhrase(it, line) }) constraints.add(line)
        }
        val numbered = constraints.distinctBy { normalizePhrase(it) }
            .mapIndexed { index, line -> "${index + 1}. ${stripDimensions(line, analysis, evidence)}" }
        val extras = mutableListOf<String>()
        extras += ProductLock.COMPONENT_COUNT_LOCK
        extras += ProductLock.GENERIC_SUBSTITUTION_BAN
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) {
            extras += ProductIdentity.MICROWAVE_COVER_LOCK
            extras += "Keep two rectangular upper modules separate; never merge them into one cylindrical reservoir."
        }
        if (ProductIdentity.looksLikeCookwarePan(fingerprint, analysis)) {
            extras += ProductIdentity.COOKWARE_PAN_LOCK
        }
        val combined = (numbered + extras).joinToString("\n")
        return semanticDedup(stripDimensions(combined, analysis, evidence))
    }

    private fun movingBlock(fingerprint: JSONObject?, reused: String?): String {
        val base = ProductLock.MOVING_COMPONENT_LOCK + "\n" + ProductLock.STATIC_WHEN_UNCERTAIN
        val withMicrowave = if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) {
            base + "\n" + ProductIdentity.MICROWAVE_VENT_STATIC
        } else {
            base
        }
        if (fingerprint == null && !reused.isNullOrBlank() && reused.contains("identity-critical moving", true)) {
            return semanticDedup(reused)
        }
        return semanticDedup(withMicrowave)
    }

    private fun speechBlock(language: String, hook: String?): String {
        if (language.equals("OFF", true)) return "No spoken dialogue."
        val line = hook?.trim().orEmpty().ifBlank { HookEngine.generate(null, language) }
        val langLine = if (language.equals("РУССКИЙ", true)) {
            "The person speaks naturally in casual home Russian, like chatting in their own kitchen, not presenting a product."
        } else {
            "The person speaks naturally in casual home German, like chatting in their own kitchen, not presenting a product."
        }
        return "$langLine\n\"$line\"\nThe spoken line must finish before the 8.0-second endpoint."
    }

    private fun resolveHook(raw: String, preferred: String?, language: String, analysis: JSONObject?): String? {
        if (language.equals("OFF", true)) return null
        val found = ProductLock.extractSpokenHooks(raw)
        if (found.size > 1) {
            return if (!preferred.isNullOrBlank() && !HookEngine.isWeak(preferred, language, analysis)) preferred.trim()
            else HookEngine.generate(analysis, language)
        }
        if (!preferred.isNullOrBlank() && !HookEngine.isWeak(preferred, language, analysis)) return preferred.trim()
        val only = found.firstOrNull()
        if (!only.isNullOrBlank() && !HookEngine.isWeak(only, language, analysis)) return only
        return HookEngine.generate(analysis, language)
    }

    private fun parseSections(text: String): Map<String, String> {
        val out = linkedMapOf<String, StringBuilder>()
        var current: String? = null
        text.lineSequence().forEach { rawLine ->
            val heading = canonicalizeHeading(rawLine)
            if (heading != null) {
                current = heading
                out.putIfAbsent(heading, StringBuilder())
            } else if (current != null) {
                val bucket = out.getValue(current!!)
                if (bucket.isNotEmpty()) bucket.append('\n')
                bucket.append(rawLine.trimEnd())
            }
        }
        return out.mapValues { it.value.toString().trim() }
    }

    private fun canonicalizeHeading(line: String): String? {
        val trimmed = line.trim().trimEnd(':').uppercase()
        return when (trimmed) {
            "FORMAT" -> "FORMAT"
            "REFERENCE" -> "REFERENCE"
            "PRODUCT IDENTITY LOCK", "FINAL IDENTITY LOCK", "STRUCTURAL IDENTITY LOCK", "PRODUCT LOCK" -> "PRODUCT IDENTITY LOCK"
            "MOVING COMPONENT LOCK" -> "MOVING COMPONENT LOCK"
            "SETTING" -> "SETTING"
            "CAMERA" -> "CAMERA"
            "ACTION", "SAFE ACTION" -> "ACTION"
            "HUMAN BEHAVIOUR", "HUMAN BEHAVIOR" -> "HUMAN BEHAVIOUR"
            "LIGHTING" -> "LIGHTING"
            "SPEECH" -> "SPEECH"
            "ANTI-MORPH", "ANTI MORPH" -> "ANTI-MORPH"
            "TIMING", "DURATION" -> "TIMING"
            "STYLE" -> "STYLE"
            "TARGET GENERATOR" -> "TARGET GENERATOR"
            else -> if (headingLine.matches(line.trim())) trimmed else null
        }
    }

    private fun rawHeading(line: String): String? {
        val trimmed = line.trim().trimEnd(':').uppercase()
        val known = CANONICAL_HEADINGS + FORBIDDEN_HEADINGS + listOf("ANTI MORPH", "HUMAN BEHAVIOR")
        return known.firstOrNull { it == trimmed }
    }

    private fun stripNoise(text: String): String {
        var next = de.spardirekt.ugcagent.v3.text.Utf8Guard.repair(text)
        next = EvidenceModel.sanitizePromptBody(next)
        next = de.spardirekt.ugcagent.v3.compliance.MarketplaceFilter.stripFromText(next)
        next = Regex("(?im)^.*uncertain_hidden_geometry.*$").replace(next, "")
        next = Regex("(?im)^.*ambiguity_warning.*$").replace(next, "")
        next = Regex("(?im)^.*Uncertain/hidden geometry.*$").replace(next, "")
        next = Regex("(?im)^.*hidden mechanism assumption.*$").replace(next, "")
        next = Regex("(?im)^.*unconfirmed attachment.*$").replace(next, "")
        next = Regex("(?im)^.*conflicting finish notes?.*$").replace(next, "")
        next = Regex("(?im)^.*exact dimensions?:.*$").replace(next, "")
        next = Regex("(?im)^.*conflicting dimensions.*$").replace(next, "")
        next = Regex("(?is)\\{[^{}]{0,8000}(\"(uncertain_hidden_geometry|ambiguity_warning|possible_pain_point|text_claims|dimensions)\")[^{}]{0,8000}\\}").replace(next, "")
        return next.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun stripDimensions(text: String, analysis: JSONObject?, evidence: JSONObject?): String {
        if (allowVerifiedDimensions(analysis, evidence)) return text
        return text.lineSequence()
            .filter { line -> !looksLikeDimension(line) || looksLikeDuration(line) }
            .joinToString("\n") { line ->
                if (looksLikeDuration(line)) line else dimensionPattern.replace(line, "")
            }
            .replace(Regex(" +"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun allowVerifiedDimensions(analysis: JSONObject?, evidence: JSONObject?): Boolean {
        val listed = JsonExtractor.stringList(analysis ?: JSONObject(), "dimensions")
        val uncertain = EvidenceModel.sellerOrUncertain(evidence, analysis)
        if (uncertain.any { looksLikeDimension(it) }) return false
        if (listed.any { it.isNotBlank() }) return false
        val verified = EvidenceModel.verifiedReliable(evidence, analysis)
        val visual = EvidenceModel.visuallyConfirmed(evidence, analysis)
        return verified.any { looksLikeDimension(it) } && visual.any { looksLikeDimension(it) }
    }

    fun looksLikeDimension(text: String): Boolean {
        if (looksLikeDuration(text)) return false
        val lower = text.lowercase()
        return dimensionPattern.containsMatchIn(text) ||
            lower.contains("dimension") ||
            lower.contains("диаметр") ||
            Regex("(?i)\\bразмер").containsMatchIn(text)
    }

    private fun looksLikeDuration(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("8.0 second") ||
            lower.contains("0.0–1.5") ||
            lower.contains("1.5–6.5") ||
            lower.contains("6.5–8.0") ||
            lower.contains("spoken line must finish")
    }

    fun containsUncertainDimensions(prompt: String, analysis: JSONObject?, evidence: JSONObject?): Boolean {
        val body = prompt.lineSequence().filter { !looksLikeDuration(it) }.joinToString("\n")
        if (!dimensionPattern.containsMatchIn(body) && !body.lowercase().contains("dimension")) return false
        return !allowVerifiedDimensions(analysis, evidence)
    }

    private fun extractLooseAction(text: String): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { candidate ->
            candidate.length in 12..180 &&
                (candidate.contains("hand", true) || candidate.contains("grip", true) || candidate.contains("handle", true) || candidate.contains("cover", true)) &&
                canonicalizeHeading(candidate) == null
        }
        return line.orEmpty()
    }

    private fun antiMorphCount(prompt: String): Int {
        val headings = Regex("(?im)^ANTI-MORPH:").findAll(prompt).count()
        val phrases = Regex("No product redesign, substitution, morphing", RegexOption.IGNORE_CASE).findAll(prompt).count()
        return maxOf(headings, phrases)
    }

    private fun timingInstructionCount(prompt: String): Int {
        val headings = Regex("(?im)^(TIMING|DURATION):").findAll(prompt).count()
        val repeatedBudget = Regex("0\\.0–1\\.5 s", RegexOption.IGNORE_CASE).findAll(prompt).count()
        return maxOf(headings, repeatedBudget)
    }

    fun durationPhraseCount(prompt: String): Int {
        val phrases = listOf(
            "generate exactly 8.0 seconds total",
            "end exactly at 8.0 seconds",
            "the clip must end at exactly 8.0 seconds",
            "end at exactly 8.0 seconds",
        )
        val lower = prompt.lowercase()
        return phrases.count { lower.contains(it) }
    }

    fun endsWithTiming(prompt: String): Boolean {
        val last = prompt.lineSequence().map { canonicalizeHeading(it) }.filterNotNull().lastOrNull()
        return last == "TIMING"
    }

    private fun identityAllowedExtra(prompt: String): String {
        val sections = parseSections(prompt)
        return listOf(
            sections["PRODUCT IDENTITY LOCK"],
            sections["MOVING COMPONENT LOCK"],
            sections["SETTING"],
            sections["ACTION"],
        ).joinToString("\n") { it.orEmpty() }
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.map { it?.trim().orEmpty() }.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun normalizePhrase(text: String): String =
        text.lowercase().replace(Regex("^\\d+\\.\\s*"), "").replace(Regex("[^a-z0-9а-яё]+"), " ").trim()

    private fun similarPhrase(a: String, b: String): Boolean {
        val left = normalizePhrase(a)
        val right = normalizePhrase(b)
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true
        val seed = minOf(28, minOf(left.length, right.length))
        if (seed < 12) return false
        return left.contains(right.take(seed)) || right.contains(left.take(seed))
    }

    private fun semanticDedup(text: String): String {
        val kept = mutableListOf<String>()
        text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            if (kept.none { similarPhrase(it, line) }) kept.add(line)
        }
        return kept.joinToString("\n")
    }
}
