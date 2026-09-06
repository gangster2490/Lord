package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

object ProductLock {
    const val LOCK_TEXT = """The reference images define one exact physical product.

Your task is not to create a similar product from the same category.

Extract and preserve the minimum identity-critical visible geometry required to keep the exact product unchanged.

Preserve the exact number, geometry, relative position and attachment layout of identity-critical components.

Do not merge components.
Do not split components.
Do not omit components.
Do not relocate components.
Do not replace components with generic alternatives.
Do not invent hidden structure.

If an action would require reconstruction of unseen geometry, classify the action as high risk and select a simpler action.

A functionally equivalent but visually different product is a failed result.

Keep exactly the same physical product throughout the entire clip.

Do not redesign, reinterpret, replace, duplicate or morph it.

Do not add unseen controls, parts, accessories or features.

Do not change visible construction.

If an action requires changing the product, simplify the action instead.

REFERENCE IMAGE OVERRIDES TEXTUAL INTERPRETATION."""

    const val COMPONENT_COUNT_LOCK =
        "Preserve the exact number, geometry and relative positions of all identity-critical visible components.\n" +
            "Do not merge, split, omit, relocate, simplify or invent components."

    const val GENERIC_SUBSTITUTION_BAN =
        "Do not generate a similar product.\n" +
            "Do not generate a generic product from the same category.\n" +
            "Do not substitute the reference with a functionally equivalent product.\n" +
            "A functionally similar but visually different product is a failed generation."

    const val ANTI_MORPH =
        "No product redesign, substitution, morphing, duplication, component merging, component deletion, invented parts, invented reservoirs, geometry drift, moving-part deformation, proportion changes, texture drift, impossible physics, malformed hands or extra fingers."

    const val MOVING_COMPONENT_LOCK =
        "Identity-critical moving components must preserve their exact geometry, proportions, attachment points and mechanism during motion.\n" +
            "Do not stretch, resize, reshape, relocate or reinterpret them.\n" +
            "If their exact movement cannot be preserved from the reference evidence, keep them stationary."

    const val STATIC_WHEN_UNCERTAIN =
        "If an identity-critical component is structurally important and its exact movement is uncertain, do not animate that component.\n" +
            "A static exact component is preferable to an animated but geometrically incorrect component."

    const val VEO_DURATION_LOCK =
        "Generate exactly 8.0 seconds total.\n" +
            "The clip must end at exactly 8.0 seconds.\n" +
            "Do not continue beyond 8.0 seconds.\n" +
            "Do not add an intro, outro, extra hold frame, freeze-frame tail, transition tail, or additional action after the main micro-moment."

    const val SPEECH_END_TIMING =
        "The spoken line must finish before the 8.0-second endpoint."

    const val SCENE_TIMING_BUDGET =
        "Preferred timing for one continuous clip: 0.0–1.5 s warm spoken line; 1.5–6.5 s one LOW-RISK home moment; 6.5–8.0 s natural settle. Not three shots. No extra scenes or CTA segments."

    const val DURATION_BLOCK =
        "DURATION:\n$VEO_DURATION_LOCK\n$SCENE_TIMING_BUDGET"

    const val UGC_STYLE =
        "STYLE:\nWarm, homely, natural kitchen UGC. Ordinary cozy home, not a showroom. Casual human voice, not a product presenter. Slightly imperfect handheld smartphone. No robotic or overly technical narration. No polished commercial tone."

    private const val SECTION_LOOKAHEAD =
        "FORMAT|REFERENCE|PRODUCT IDENTITY LOCK|FINAL IDENTITY LOCK|MOVING COMPONENT LOCK|SETTING|CAMERA|SAFE ACTION|ACTION|HUMAN BEHAVIOUR|LIGHTING|SPEECH|ANTI-MORPH|DURATION|TIMING|STYLE|PRODUCT LOCK|Target generator"

    const val CAMERA_LOCK =
        "Natural handheld smartphone. Slight tremor and tiny human drift are allowed. No orbit, dramatic push-in, aggressive zoom, cinematic crane, or major angle change that hides identity-critical geometry."

    const val HUMAN_LOCK =
        "One anatomically correct hand with five fingers. Natural UGC movement only. No presenter gestures, excessive pointing, aggressive gripping, extra hands, or covering identity-critical parts."

    const val LIGHTING_LOCK =
        "Ordinary home daylight or normal indoor light. Natural reflections. Slightly imperfect real UGC. No studio-commercial lighting, glossy advertising look, fake glow, or showroom perfection."

    const val FIRST_FRAME_WINS =
        "If references conflict in color or finish, the selected First Frame is the primary source of truth. SELECTED FIRST FRAME WINS."

    private val appearanceLeak = Regex(
        """(use a similar product|similar product is acceptable|a similar product from the same category is acceptable|functionally equivalent is (ok|fine|acceptable)|a typical product of this type|you may (merge|replace|substitute)|category-equivalent product is (ok|fine|acceptable))""",
        RegexOption.IGNORE_CASE,
    )

    fun ensure(prompt: String, lockOn: Boolean, fingerprint: JSONObject? = null): String {
        val repaired = de.spardirekt.ugcagent.v3.text.Utf8Guard.repair(prompt.trim())
        return PromptComposer.compose(
            raw = repaired,
            fingerprint = fingerprint,
            generator = "VEO",
            speechLanguage = "OFF",
        )
    }

    fun finalizeClean(
        prompt: String,
        fingerprint: JSONObject? = null,
        generator: String = "VEO",
        speechLanguage: String = "OFF",
        hook: String? = null,
        lockOn: Boolean = true,
        analysis: JSONObject? = null,
        evidence: JSONObject? = null,
    ): String {
        val repaired = de.spardirekt.ugcagent.v3.text.Utf8Guard.repair(prompt)
        val composed = PromptComposer.compose(
            raw = repaired,
            fingerprint = fingerprint,
            generator = generator,
            speechLanguage = speechLanguage,
            hook = hook,
            analysis = analysis,
            evidence = evidence,
        )
        if (PromptComposer.isCanonical(composed, speechLanguage, analysis, evidence)) return composed
        return PromptComposer.compose(
            raw = PromptComposer.extractAction(composed).ifBlank { composed },
            fingerprint = fingerprint,
            generator = generator,
            speechLanguage = speechLanguage,
            hook = hook,
            analysis = analysis,
            evidence = evidence,
        )
    }

    fun repairOnce(
        prompt: String,
        fingerprint: JSONObject? = null,
        generator: String = "VEO",
        speechLanguage: String = "OFF",
        lockOn: Boolean = true,
    ): String = finalizeClean(prompt, fingerprint, generator, speechLanguage, hook = null, lockOn = lockOn)

    fun ensureNoSpeech(prompt: String): String {
        return if (prompt.contains("No spoken dialogue", ignoreCase = true)) {
            prompt
        } else {
            prompt.trimEnd() + "\n\nNo spoken dialogue."
        }
    }

    fun ensureSpeechTiming(prompt: String, speechLanguage: String): String {
        if (speechLanguage.equals("OFF", true)) return ensureNoSpeech(prompt)
        return normalizeSpeech(prompt, speechLanguage, hook = null)
    }

    fun normalizeSpeech(prompt: String, speechLanguage: String, hook: String?): String {
        return PromptComposer.compose(
            raw = de.spardirekt.ugcagent.v3.text.Utf8Guard.repair(prompt),
            fingerprint = null,
            generator = "VEO",
            speechLanguage = speechLanguage,
            hook = hook,
        )
    }

    fun speechHeadingCount(prompt: String): Int =
        Regex("(?im)^SPEECH:").findAll(prompt).count()

    fun speechEndTimingCount(prompt: String): Int =
        Regex("spoken line must finish before the 8\\.0-second endpoint", RegexOption.IGNORE_CASE).findAll(prompt).count()

    fun identityLockCount(prompt: String): Int =
        Regex("(?im)^PRODUCT IDENTITY LOCK:").findAll(prompt).count()

    fun leftoverIdentityHeadingCount(prompt: String): Int =
        Regex("(?im)^(FINAL IDENTITY LOCK|STRUCTURAL IDENTITY LOCK):").findAll(prompt).count()

    fun movingLockCount(prompt: String): Int =
        Regex("(?im)^MOVING COMPONENT LOCK:").findAll(prompt).count()

    fun durationHeadingCount(prompt: String): Int =
        Regex("(?im)^TIMING:").findAll(prompt).count()

    fun leftoverDurationCount(prompt: String): Int =
        Regex("(?im)^DURATION:").findAll(prompt).count()

    fun antiMorphHeadingCount(prompt: String): Int =
        Regex("(?im)^ANTI-MORPH:").findAll(prompt).count()

    fun extractSpokenHooks(prompt: String): List<String> {
        val found = mutableListOf<String>()
        Regex("Spoken hook begins around 0\\.3–0\\.8 seconds:?\\s*\\n?\"([^\"]+)\"").findAll(prompt).forEach {
            found += it.groupValues[1].trim()
        }
        Regex("(?i)(?:says?|speaks?|line):\\s*\"([^\"]{8,110})\"").findAll(prompt).forEach {
            found += it.groupValues[1].trim()
        }
        sectionRegex("SPEECH:").findAll(prompt).forEach { match ->
            Regex("\"([^\"]{8,110})\"").findAll(match.value).forEach { found += it.groupValues[1].trim() }
            match.value.lineSequence()
                .map { it.trim().trim('"') }
                .filter { line ->
                    line.length in 8..110 &&
                        !line.startsWith("SPEECH", true) &&
                        !line.startsWith("The person speaks", true) &&
                        !line.startsWith("Spoken hook", true) &&
                        !line.startsWith("The spoken line", true)
                }
                .forEach { found += it }
        }
        return found.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    }

    fun hasConflictingSpokenHooks(prompt: String): Boolean = extractSpokenHooks(prompt).size > 1

    fun applyGenerator(prompt: String, generator: String): String {
        val cleaned = prompt.replace(Regex("maximum 8(\\.0)? seconds", RegexOption.IGNORE_CASE), "exactly 8.0 seconds")
        val speech = inferredSpeechLanguage(cleaned)
        val hook = extractSpokenHooks(cleaned).firstOrNull()
        return PromptComposer.compose(
            raw = cleaned,
            fingerprint = null,
            generator = generator,
            speechLanguage = speech,
            hook = hook,
        )
    }

    fun looksLikeProductRebuild(prompt: String): Boolean = appearanceLeak.containsMatchIn(prompt)

    fun allowsComponentMutation(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val compactForbid = Regex(
            """do not merge,\s*split,\s*(omit|remove),\s*relocate,\s*simplify or invent""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(prompt)
        val lineForbid = lower.contains("do not merge") &&
            lower.contains("do not split") &&
            (lower.contains("do not omit") || lower.contains("do not remove")) &&
            lower.contains("do not relocate") &&
            lower.contains("invent")
        return !(compactForbid || lineForbid)
    }

    fun allowsGenericSubstitution(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val bansSimilar = lower.contains("do not generate a similar") ||
            lower.contains("not to create a similar product")
        val bansCategory = lower.contains("generic product from the same category") ||
            lower.contains("similar product from the same category")
        val bansFunctional = lower.contains("functionally similar but visually different") ||
            lower.contains("functionally equivalent but visually different")
        val missingBan = !(bansSimilar && bansCategory && bansFunctional)
        return missingBan || looksLikeProductRebuild(prompt)
    }

    fun preservesMicrowaveCover(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("transparent") && lower.contains("dome") &&
            lower.contains("green circular") && lower.contains("base ring") &&
            lower.contains("curved green") && lower.contains("handle") &&
            lower.contains("circular upper vent") &&
            lower.contains("rectangular") &&
            lower.contains("green caps") &&
            lower.contains("relative positions") &&
            (lower.contains("seam/rib") || lower.contains("seam") && lower.contains("rib")) &&
            lower.contains("attachment") &&
            lower.contains("cylindrical reservoir")
    }

    fun hasMovingComponentLock(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("identity-critical moving components") &&
            lower.contains("do not stretch") &&
            (lower.contains("keep them stationary") || lower.contains("keep the component stationary"))
    }

    fun allowsMovingComponentDeformation(prompt: String): Boolean {
        if (!hasMovingComponentLock(prompt)) return true
        val lower = prompt.lowercase()
        val permits = lower.contains("you may stretch") ||
            lower.contains("resizing is allowed") ||
            lower.contains("shape may change") ||
            lower.contains("animate even if uncertain")
        return permits
    }

    fun veoHasExactDuration(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val exact = lower.contains("exactly 8.0 seconds")
        val end = lower.contains("end at exactly 8.0 seconds") ||
            lower.contains("end exactly at 8.0 seconds") ||
            lower.contains("must end at exactly 8.0 seconds")
        val noTail = lower.contains("freeze-frame tail") && (lower.contains("intro") && lower.contains("outro"))
        val onlyMaximum = lower.contains("maximum 8") && !exact
        return exact && end && noTail && !onlyMaximum
    }

    fun hasSpeechEndTiming(prompt: String): Boolean =
        prompt.contains("finish before the 8.0-second endpoint", ignoreCase = true)

    fun allowsExtraTail(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val forbids = lower.contains("do not add") &&
            lower.contains("intro") &&
            lower.contains("outro") &&
            (lower.contains("freeze-frame") || lower.contains("hold frame")) &&
            lower.contains("additional action")
        return !forbids
    }

    fun regressionFailures(prompt: String, fingerprint: JSONObject? = null, generator: String = "VEO", speechLanguage: String = "OFF"): List<String> {
        val failures = mutableListOf<String>()
        if (allowsComponentMutation(prompt)) failures.add("component_count_lock")
        if (allowsGenericSubstitution(prompt)) failures.add("generic_substitution")
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint) && !preservesMicrowaveCover(prompt)) {
            failures.add("microwave_cover_identity")
        }
        if (allowsMovingComponentDeformation(prompt)) failures.add("moving_component_lock")
        if (generator.equals("VEO", true) && !veoHasExactDuration(prompt)) failures.add("veo_exact_duration")
        if (!speechLanguage.equals("OFF", true) && !hasSpeechEndTiming(prompt)) failures.add("speech_end_timing")
        if (allowsExtraTail(prompt)) failures.add("extra_tail")
        if (leaksInternalAnalysis(prompt)) failures.add("internal_analysis_leak")
        if (hasDuplicateProductLock(prompt)) failures.add("duplicate_product_lock")
        if (!speechLanguage.equals("OFF", true) && speechHeadingCount(prompt) > 1) failures.add("duplicate_speech_heading")
        if (!speechLanguage.equals("OFF", true) && speechEndTimingCount(prompt) > 1) failures.add("duplicate_speech_timing")
        if (identityLockCount(prompt) != 1) failures.add("duplicate_identity_lock")
        if (leftoverIdentityHeadingCount(prompt) > 0) failures.add("leftover_identity_heading")
        if (movingLockCount(prompt) != 1) failures.add("duplicate_moving_lock")
        if (antiMorphHeadingCount(prompt) != 1) failures.add("duplicate_anti_morph")
        if (durationHeadingCount(prompt) != 1) failures.add("duplicate_duration")
        if (leftoverDurationCount(prompt) > 0) failures.add("leftover_duration")
        if (hasConflictingSpokenHooks(prompt)) failures.add("conflicting_spoken_hooks")
        failures += PromptComposer.canonicalFailures(prompt, speechLanguage)
        return failures.distinct()
    }

    fun leaksInternalAnalysis(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("uncertain_hidden_geometry") ||
            lower.contains("ambiguity_warning") ||
            lower.contains("uncertain/hidden geometry") ||
            lower.contains("hidden mechanism assumption") ||
            Regex("\"text_claims\"\\s*:").containsMatchIn(prompt) ||
            Regex("\"possible_pain_point\"\\s*:").containsMatchIn(prompt)
    }

    fun hasDuplicateProductLock(prompt: String): Boolean =
        Regex("REFERENCE IMAGE OVERRIDES", RegexOption.IGNORE_CASE).findAll(prompt).count() > 1 ||
            identityLockCount(prompt) > 1 ||
            leftoverIdentityHeadingCount(prompt) > 0

    fun looksLikeFinishConflict(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("conflicting finish") ||
            lower.contains("finish_conflict") ||
            (lower.contains("color") && lower.contains("finish") && (lower.contains("conflict") || lower.contains("disagree")))
    }

    fun wordCount(prompt: String): Int = prompt.split(Regex("\\s+")).filter { it.isNotBlank() }.size

    private fun ensureContains(body: String, block: String, marker: String? = null): String {
        val needle = marker ?: block.lineSequence().first { it.isNotBlank() }
        return if (body.contains(needle, ignoreCase = true)) body else body.trimEnd() + "\n\n" + block.trim()
    }

    private fun insertBlock(body: String, header: String, block: String): String {
        if (block.isBlank()) return body
        if (body.contains(header, ignoreCase = true)) return body
        return body.trimEnd() + "\n\n" + block.trim()
    }

    private fun ensureProductLockOnce(body: String): String {
        return if (body.contains("REFERENCE IMAGE OVERRIDES", ignoreCase = true)) body else "$LOCK_TEXT\n\n$body"
    }

    private fun inferredSpeechLanguage(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("no spoken dialogue") -> "OFF"
            lower.contains("casual home russian") || lower.contains("speaks naturally in russian") -> "РУССКИЙ"
            lower.contains("casual home german") || lower.contains("speaks naturally in german") -> "DEUTSCH"
            else -> "OFF"
        }
    }

    private fun insertFinalIdentityLock(body: String, fingerprint: JSONObject?): String {
        return upsertHeaderSection(body, "PRODUCT IDENTITY LOCK:", ProductIdentity.finalIdentityLockBlock(fingerprint))
    }

    private fun ensureAntiMorphOnce(body: String): String {
        return upsertHeaderSection(body, "ANTI-MORPH:", "ANTI-MORPH:\n$ANTI_MORPH")
    }

    private fun resolveSpokenHook(prompt: String, preferred: String?, speechLanguage: String, analysis: JSONObject?): String? {
        if (speechLanguage.equals("OFF", true)) return null
        val found = extractSpokenHooks(prompt)
        if (found.size > 1) {
            return if (!preferred.isNullOrBlank() && !HookEngine.isWeak(preferred, speechLanguage)) {
                preferred.trim()
            } else {
                HookEngine.generate(analysis, speechLanguage)
            }
        }
        if (!preferred.isNullOrBlank() && !HookEngine.isWeak(preferred, speechLanguage)) return preferred.trim()
        val only = found.firstOrNull()
        if (!only.isNullOrBlank() && !HookEngine.isWeak(only, speechLanguage)) return only
        return HookEngine.generate(analysis, speechLanguage)
    }

    private fun stripUncertainDimensions(body: String): String {
        var next = body
        next = Regex("(?im)^.*exact dimensions?:.*$").replace(next, "")
        next = Regex("(?im)^.*conflicting dimensions.*$").replace(next, "")
        next = Regex("(?im)^.*\\b\\d+\\s*(mm|cm)\\b.*dimension.*$").replace(next, "")
        return next.replace(Regex("\n{3,}"), "\n\n")
    }

    private fun stripInternalLeaks(body: String): String {
        var next = body
        next = Regex("(?im)^.*uncertain_hidden_geometry.*$").replace(next, "")
        next = Regex("(?im)^.*ambiguity_warning.*$").replace(next, "")
        next = Regex("(?im)^.*Uncertain/hidden geometry.*$").replace(next, "")
        next = Regex("(?im)^.*hidden mechanism assumption.*$").replace(next, "")
        next = Regex("(?im)^.*unconfirmed attachment.*$").replace(next, "")
        next = Regex("(?im)^.*conflicting finish notes?.*$").replace(next, "")
        next = Regex("(?im)^.*exact dimensions?:.*$").replace(next, "")
        return next.replace(Regex("\n{3,}"), "\n\n")
    }

    private fun stripJsonDumps(body: String): String {
        return Regex("\\{[^{}]{0,8000}(\"(uncertain_hidden_geometry|ambiguity_warning|possible_pain_point|text_claims)\")[^{}]{0,8000}\\}")
            .replace(body, "")
            .replace(Regex("\n{3,}"), "\n\n")
    }

    private fun collapseDuplicateLockBlocks(body: String): String {
        var next = keepFirstBlock(body, Regex("(?is)The reference images define one exact physical product\\.[\\s\\S]*?REFERENCE IMAGE OVERRIDES TEXTUAL INTERPRETATION\\."))
        next = keepFirstHeaderSection(next, "PRODUCT IDENTITY LOCK:")
        next = keepFirstHeaderSection(next, "FINAL IDENTITY LOCK:")
        next = keepFirstHeaderSection(next, "MOVING COMPONENT LOCK:")
        next = keepFirstHeaderSection(next, "ANTI-MORPH:")
        next = keepFirstHeaderSection(next, "SPEECH:")
        next = keepFirstHeaderSection(next, "TIMING:")
        next = keepFirstHeaderSection(next, "DURATION:")
        next = keepFirstHeaderSection(next, "STYLE:")
        next = keepFirstOccurrence(next, GENERIC_SUBSTITUTION_BAN)
        next = keepFirstOccurrence(next, COMPONENT_COUNT_LOCK)
        next = keepFirstOccurrence(next, SPEECH_END_TIMING)
        next = keepFirstOccurrence(next, "Spoken hook begins around 0.3–0.8 seconds")
        next = keepFirstOccurrence(next, "Generate exactly 8.0 seconds total.")
        return next.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun stripSpeechSections(body: String): String {
        var next = sectionRegex("SPEECH:").replace(body, "\n")
        next = Regex("(?im)^The spoken line must finish before the 8\\.0-second endpoint\\.?\\s*$").replace(next, "")
        next = Regex("(?im)^Spoken hook begins around 0\\.3–0\\.8 seconds:?\\s*$").replace(next, "")
        return next.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun existingSpokenLine(prompt: String): String? {
        val quoted = Regex("Spoken hook begins around 0\\.3–0\\.8 seconds:\\s*\\n\"([^\"]+)\"").find(prompt)?.groupValues?.getOrNull(1)
        if (!quoted.isNullOrBlank()) return quoted.trim()
        val speech = Regex("(?is)SPEECH:\\s*(.*)").find(prompt)?.groupValues?.getOrNull(1).orEmpty()
        val line = speech.lineSequence()
            .map { it.trim().trim('"') }
            .firstOrNull { it.isNotBlank() && !it.startsWith("The person speaks") && !it.startsWith("Spoken hook") && !it.startsWith("The spoken line") && !it.equals("SPEECH:", true) }
        return line?.takeIf { it.length in 8..110 }
    }

    private fun keepFirstBlock(body: String, block: Regex): String {
        val matches = block.findAll(body).toList()
        if (matches.size <= 1) return body
        var next = body
        matches.drop(1).reversed().forEach { match ->
            next = next.removeRange(match.range)
        }
        return next
    }

    private fun upsertHeaderSection(body: String, header: String, block: String): String {
        val regex = sectionRegex(header)
        val matches = regex.findAll(body).toList()
        val next = if (matches.isEmpty()) {
            body.trimEnd() + "\n\n" + block.trim()
        } else {
            var updated = body
            matches.drop(1).reversed().forEach { updated = updated.removeRange(it.range) }
            val first = regex.find(updated) ?: return updated.trimEnd() + "\n\n" + block.trim()
            updated.replaceRange(first.range, "\n" + block.trim() + "\n")
        }
        return next.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun sectionRegex(header: String): Regex {
        return Regex("(?is)(?:^|\\n)${Regex.escape(header)}\\s*.*?(?=\\n(?:$SECTION_LOOKAHEAD)\\b|$)")
    }

    private fun keepFirstHeaderSection(body: String, header: String): String {
        val matches = sectionRegex(header).findAll(body).toList()
        if (matches.size <= 1) return body
        var next = body
        matches.drop(1).reversed().forEach { match ->
            next = next.removeRange(match.range)
        }
        return next
    }

    private fun keepFirstOccurrence(body: String, block: String): String {
        val first = body.indexOf(block, ignoreCase = true)
        if (first < 0) return body
        val second = body.indexOf(block, startIndex = first + block.length, ignoreCase = true)
        if (second < 0) return body
        return body.substring(0, second) + body.substring(second + block.length)
    }
}
