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
        "Preferred timing budget for one continuous clip: 0.0–1.0 s establish the existing First Frame; 1.0–6.5 s one main action; 6.5–8.0 s natural completion / brief settle. Not three shots. No extra scenes or CTA segments."

    private val appearanceLeak = Regex(
        """(use a similar product|similar product is acceptable|a similar product from the same category is acceptable|functionally equivalent is (ok|fine|acceptable)|a typical product of this type|you may (merge|replace|substitute)|category-equivalent product is (ok|fine|acceptable))""",
        RegexOption.IGNORE_CASE,
    )

    fun ensure(prompt: String, lockOn: Boolean, fingerprint: JSONObject? = null): String {
        var body = prompt.trim()
        if (lockOn && !body.contains("REFERENCE IMAGE OVERRIDES", ignoreCase = true)) {
            body = "$LOCK_TEXT\n\n$body"
        }
        body = insertBlock(body, "STRUCTURAL IDENTITY LOCK:", ProductIdentity.structuralLockBlock(fingerprint))
        body = ensureContains(body, COMPONENT_COUNT_LOCK)
        body = ensureContains(body, GENERIC_SUBSTITUTION_BAN)
        body = insertBlock(body, "MOVING COMPONENT LOCK:", "MOVING COMPONENT LOCK:\n$MOVING_COMPONENT_LOCK")
        body = ensureContains(body, MOVING_COMPONENT_LOCK, "Identity-critical moving components must preserve")
        body = ensureContains(body, STATIC_WHEN_UNCERTAIN, "A static exact component is preferable")
        body = ensureContains(body, ANTI_MORPH, "moving-part deformation")
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) {
            body = ensureContains(body, ProductIdentity.MICROWAVE_COVER_LOCK)
            body = ensureContains(body, ProductIdentity.MICROWAVE_VENT_STATIC, "keep it completely static")
        }
        body = body.replace(Regex("maximum 8(\\.0)? seconds", RegexOption.IGNORE_CASE), "exactly 8.0 seconds")
        return body.trim()
    }

    fun ensureNoSpeech(prompt: String): String {
        return if (prompt.contains("No spoken dialogue", ignoreCase = true)) {
            prompt
        } else {
            prompt.trimEnd() + "\n\nNo spoken dialogue."
        }
    }

    fun ensureSpeechTiming(prompt: String, speechLanguage: String): String {
        if (speechLanguage.equals("OFF", true)) return ensureNoSpeech(prompt)
        return ensureContains(prompt, SPEECH_END_TIMING)
    }

    fun applyGenerator(prompt: String, generator: String): String {
        var cleaned = prompt.replace(Regex("maximum 8(\\.0)? seconds", RegexOption.IGNORE_CASE), "exactly 8.0 seconds")
        val gen = generator.uppercase()
        if (gen == "VEO") {
            cleaned = ensureContains(cleaned, VEO_DURATION_LOCK, "freeze-frame tail")
            cleaned = ensureContains(cleaned, SCENE_TIMING_BUDGET, "0.0–1.0 s")
            if (!cleaned.contains("Target generator:", ignoreCase = true)) {
                cleaned = "Target generator: Veo. Vertical 9:16. One continuous clip.\n\n$cleaned"
            }
            return cleaned
        }
        val closest = "Use the closest supported duration to 8.0 seconds without creating multiple scenes.\n$VEO_DURATION_LOCK"
        cleaned = ensureContains(cleaned, closest, "closest supported duration to 8.0 seconds")
        cleaned = ensureContains(cleaned, VEO_DURATION_LOCK, "freeze-frame tail")
        if (cleaned.contains("Target generator:", ignoreCase = true)) return cleaned
        val note = if (gen == "KLING") {
            "Target generator: Kling. Vertical 9:16. One continuous clip."
        } else {
            "Target generator: generic short-form video model. Vertical 9:16. One continuous clip."
        }
        return "$note\n\n$cleaned"
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
        return lower.contains("transparent dome") &&
            lower.contains("green circular base ring") &&
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
        val end = lower.contains("end at exactly 8.0 seconds") || lower.contains("must end at exactly 8.0 seconds")
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
        return failures
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
}
