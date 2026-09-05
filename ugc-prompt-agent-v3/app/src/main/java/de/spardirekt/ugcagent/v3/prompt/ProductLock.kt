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
        "No product redesign, substitution, morphing, duplication, component merging, component deletion, invented controls, invented reservoirs, geometry drift, proportion changes, texture drift, impossible physics, malformed hands or extra fingers."

    const val EXACT_DURATION =
        "Generate exactly 8.0 seconds total.\n" +
            "End the clip at exactly 8.0 seconds.\n" +
            "Do not extend beyond 8.0 seconds."

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
        body = ensureContains(body, ANTI_MORPH)
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) {
            body = ensureContains(body, ProductIdentity.MICROWAVE_COVER_LOCK)
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

    fun applyGenerator(prompt: String, generator: String): String {
        var cleaned = prompt.replace(Regex("maximum 8(\\.0)? seconds", RegexOption.IGNORE_CASE), "exactly 8.0 seconds")
        if (!cleaned.contains("exactly 8.0 seconds", ignoreCase = true)) {
            cleaned = cleaned.trimEnd() + "\n\n" + EXACT_DURATION
        }
        if (cleaned.contains("Target generator:", ignoreCase = true)) {
            return cleaned
        }
        val note = when (generator.uppercase()) {
            "VEO" -> "Target generator: Veo. Vertical 9:16. One continuous clip."
            "KLING" -> "Target generator: Kling. Vertical 9:16. One continuous clip.\nUse the closest supported duration to 8.0 seconds without creating multiple scenes."
            else -> "Target generator: generic short-form video model. Vertical 9:16. One continuous clip.\nUse the closest supported duration to 8.0 seconds without creating multiple scenes."
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

    fun regressionFailures(prompt: String, fingerprint: JSONObject? = null): List<String> {
        val failures = mutableListOf<String>()
        if (allowsComponentMutation(prompt)) failures.add("component_count_lock")
        if (allowsGenericSubstitution(prompt)) failures.add("generic_substitution")
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint) && !preservesMicrowaveCover(prompt)) {
            failures.add("microwave_cover_identity")
        }
        if (!prompt.contains("exactly 8.0 seconds", ignoreCase = true)) {
            failures.add("exact_duration")
        }
        return failures
    }

    fun wordCount(prompt: String): Int = prompt.split(Regex("\\s+")).filter { it.isNotBlank() }.size

    private fun ensureContains(body: String, block: String): String {
        val needle = block.lineSequence().first { it.isNotBlank() }
        return if (body.contains(needle, ignoreCase = true)) body else body.trimEnd() + "\n\n" + block.trim()
    }

    private fun insertBlock(body: String, header: String, block: String): String {
        if (block.isBlank()) return body
        if (body.contains(header, ignoreCase = true)) return body
        return body.trimEnd() + "\n\n" + block.trim()
    }
}
