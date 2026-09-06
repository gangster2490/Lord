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
        "0.0–1.5 s: hook and immediate use context so the selling idea is already clear\n" +
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
        val plan = CreativeStrategyEngine.plan(analysis, fingerprint)
        val retryRaw = "ACTION:\n$action\n\nSETTING:\n${plan.setting}"
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
        if (CrossProductGuard.containsLeak(prompt, fingerprint, analysis, CreativeStrategyEngine.plan(analysis, fingerprint))) {
            failures.add("cross_product_leak")
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
        if (!speechLanguage.equals("OFF", true) && hooks.any { HookEngine.isWeak(it, speechLanguage, analysis, fingerprint = fingerprint) }) {
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
        val hinted = fingerprint ?: fingerprintFromIdentity(sections["PRODUCT IDENTITY LOCK"])
        val (cleanAnalysis, cleanFingerprint) = CrossProductGuard.clean(analysis, hinted)
        val effectiveFingerprint = cleanFingerprint ?: hinted
        val plan = CreativeStrategyEngine.plan(cleanAnalysis, effectiveFingerprint)
        val parsedAction = firstNonBlank(sections["ACTION"], extractLooseAction(cleaned), plan.action)
        val action = if (
            CreativeStrategyEngine.actionConflicts(parsedAction, plan) ||
            CrossProductGuard.containsLeak(parsedAction, effectiveFingerprint, cleanAnalysis, plan)
        ) plan.action else parsedAction
        val setting = plan.setting
        val resolvedHook = resolveHook(cleaned, hook, speechLanguage, cleanAnalysis, effectiveFingerprint, plan)
        val identity = identityBlock(effectiveFingerprint, cleanAnalysis, evidence, sections["PRODUCT IDENTITY LOCK"])
        val moving = movingBlock(effectiveFingerprint, cleanAnalysis, sections["MOVING COMPONENT LOCK"])
        val settingBody = stripDimensions(setting, cleanAnalysis, evidence)
        val actionBody = stripDimensions(action, cleanAnalysis, evidence)
        val extraAllowed = "$identity\n$moving"
        val body = buildString {
            appendSection("FORMAT", formatBlock(generator, plan))
            appendSection("REFERENCE", referenceBlock(effectiveFingerprint))
            appendSection("PRODUCT IDENTITY LOCK", identity)
            appendSection("MOVING COMPONENT LOCK", moving)
            appendSection("SETTING", settingBody)
            appendSection("CAMERA", plan.camera)
            appendSection("ACTION", actionBody)
            appendSection("HUMAN BEHAVIOUR", plan.human)
            appendSection("LIGHTING", plan.lighting)
            appendSection("SPEECH", speechBlock(speechLanguage, resolvedHook, plan, effectiveFingerprint, cleanAnalysis))
            appendSection("ANTI-MORPH", ProductLock.antiMorphFor(effectiveFingerprint, cleanAnalysis))
            appendSection("TIMING", timingBlock(plan))
        }.trim()
        val guarded = CrossProductGuard.strip(body, effectiveFingerprint, cleanAnalysis, plan)
        return ProductLexicon.stripForeign(guarded, effectiveFingerprint, cleanAnalysis, extraAllowed)
    }

    private fun fingerprintFromIdentity(identity: String?): JSONObject? = when (CrossProductGuard.family(null, null, identity.orEmpty())) {
        CrossProductGuard.Family.MICROWAVE_COVER -> ProductIdentity.microwaveCoverFingerprint()
        CrossProductGuard.Family.COOKWARE_PAN -> ProductIdentity.cookwarePanFingerprint()
        CrossProductGuard.Family.FISHING_GEAR -> CreativeStrategyEngine.fishingChairFingerprint()
        CrossProductGuard.Family.GENERIC -> null
    }

    private fun StringBuilder.appendSection(heading: String, body: String) {
        if (isNotEmpty()) append("\n\n")
        append(heading).append(":\n").append(body.trim())
    }

    private fun formatBlock(generator: String, plan: CreativeStrategyEngine.Plan): String {
        val gen = when (generator.uppercase()) {
            "KLING" -> "Target generator: Kling."
            "VEO" -> "Target generator: Veo."
            else -> "Target generator: generic short-form video model."
        }
        return "$gen Vertical 9:16. One continuous natural smartphone UGC clip. ${plan.formatTone}"
    }

    private fun timingBlock(plan: CreativeStrategyEngine.Plan): String =
        "0.0–1.5 s: ${plan.opening}\n" +
            "1.5–6.5 s: one LOW-RISK natural interaction that supports that single idea\n" +
            "6.5–8.0 s: natural settle\n" +
            "End exactly at 8.0 seconds.\n" +
            "No intro, outro, CTA, additional scene, freeze-frame or transition tail."

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
        val extras = extractShortFeatures(reused)
        val extraBlob = extras.joinToString("\n") + "\n" + reused.orEmpty()
        val features = ProductIdentity.compactIdentityFeatures(
            fingerprint,
            extras.filter { !CrossProductGuard.isForeignIdentityLine(it, fingerprint, analysis, extraBlob) },
            analysis,
        )
            .map { stripDimensions(it, analysis, evidence) }
            .filter { it.isNotBlank() && !looksLikeDimension(it) }
            .distinctBy { normalizePhrase(it) }
            .take(10)
        val numbered = features.mapIndexed { index, line -> "${index + 1}. $line" }
        val combined = (numbered + ProductIdentity.IDENTITY_POLICY_LINES).joinToString("\n")
        return semanticDedup(stripDimensions(combined, analysis, evidence))
    }

    private fun extractShortFeatures(reused: String?): List<String> {
        if (reused.isNullOrBlank()) return emptyList()
        return reused.lineSequence().mapNotNull { line ->
            ProductIdentity.compactFeature(line)
        }.toList()
    }

    private fun movingBlock(fingerprint: JSONObject?, analysis: JSONObject?, reused: String?): String {
        val base = ProductLock.MOVING_COMPONENT_LOCK + "\n" + ProductLock.STATIC_WHEN_UNCERTAIN
        val withMicrowave = if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) {
            base + "\n" + ProductIdentity.MICROWAVE_VENT_STATIC
        } else {
            base
        }
        if (
            fingerprint == null &&
            !reused.isNullOrBlank() &&
            reused.contains("identity-critical moving", true) &&
            !CrossProductGuard.containsLeak(reused, fingerprint, analysis)
        ) {
            return semanticDedup(reused)
        }
        return semanticDedup(withMicrowave)
    }

    private fun speechBlock(
        language: String,
        hook: String?,
        plan: CreativeStrategyEngine.Plan,
        fingerprint: JSONObject?,
        analysis: JSONObject?,
    ): String {
        if (language.equals("OFF", true)) return "No spoken dialogue."
        val line = cleanSpokenLine(hook, language, analysis, fingerprint, plan)
        return "${HookEngine.speechIntro(language, plan)}\n\"$line\"\nThe spoken line must finish before the 8.0-second endpoint."
    }

    private fun cleanSpokenLine(
        hook: String?,
        language: String,
        analysis: JSONObject?,
        fingerprint: JSONObject?,
        plan: CreativeStrategyEngine.Plan,
    ): String {
        val candidate = hook?.trim().orEmpty()
        if (
            candidate.isNotBlank() &&
            !HookEngine.isWeak(candidate, language, analysis, plan, fingerprint) &&
            !CrossProductGuard.containsLeak(candidate, fingerprint, analysis, plan)
        ) {
            return candidate
        }
        return HookEngine.generate(analysis, language, fingerprint, plan)
    }

    private fun resolveHook(
        raw: String,
        preferred: String?,
        language: String,
        analysis: JSONObject?,
        fingerprint: JSONObject?,
        plan: CreativeStrategyEngine.Plan,
    ): String? {
        if (language.equals("OFF", true)) return null
        fun usable(value: String?): Boolean {
            val text = value?.trim().orEmpty()
            return text.isNotBlank() &&
                !HookEngine.isWeak(text, language, analysis, plan, fingerprint) &&
                !CrossProductGuard.containsLeak(text, fingerprint, analysis, plan)
        }
        val found = ProductLock.extractSpokenHooks(raw)
        if (found.size > 1) {
            return if (usable(preferred)) preferred!!.trim()
            else HookEngine.generate(analysis, language, fingerprint, plan)
        }
        if (usable(preferred)) return preferred!!.trim()
        val only = found.firstOrNull()
        if (usable(only)) return only
        return HookEngine.generate(analysis, language, fingerprint, plan)
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
