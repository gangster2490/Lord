package de.spardirekt.ugcclean.gen

object PromptComposer {
    val HEADINGS = listOf(
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

    const val TIMING = "" +
        "0.0–1.5 s: hook and immediate use context so the selling idea is already clear\n" +
        "1.5–6.5 s: one LOW-RISK natural interaction\n" +
        "6.5–8.0 s: natural settle\n" +
        "End exactly at 8.0 seconds.\n" +
        "No intro, outro, CTA, additional scene, freeze-frame or transition tail."

    fun compose(
        plan: de.spardirekt.ugcclean.model.ProductPlan,
        language: de.spardirekt.ugcclean.model.SpeechLanguage,
    ): String {
        val features = plan.visibleFeatures
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
        val identity = buildString {
            append("Keep this exact physical product from the First Frame: ")
            append(plan.productName.trim().ifBlank { "the uploaded product" })
            append(".\n")
            if (features.isNotEmpty()) {
                append("Visible: ")
                append(features.joinToString("; "))
                append(".\n")
            }
            append("Do not replace it with a generic category product.\n")
            append("Do not add, remove, or restyle parts that are not in the photos.")
        }
        val moving = plan.movingParts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
            .ifBlank { "Keep identity-critical parts still if motion is uncertain." }
        val hook = when (language) {
            de.spardirekt.ugcclean.model.SpeechLanguage.DE -> plan.spokenHookDe
            de.spardirekt.ugcclean.model.SpeechLanguage.RU -> plan.spokenHookRu
        }.trim().ifBlank { defaultHook(language) }
        val speech = "One casual spoken line in ${languageLabel(language)}, finished before 8.0s:\n\"$hook\""
        val antiMorph = buildString {
            append("Do not morph, merge, or replace this product.\n")
            append("Do not invent extra components from other product categories.\n")
            append("If motion risks identity, keep the product still and move only camera or hands.")
        }
        return buildString {
            section("FORMAT", "9:16 vertical. Exactly 8.0 seconds. One continuous handheld UGC clip. Target generator: Veo.")
            section("REFERENCE", "First uploaded photo is the First Frame. Recreate that exact physical product. Listing UI, prices and seller text are ignored.")
            section("PRODUCT IDENTITY LOCK", identity)
            section("MOVING COMPONENT LOCK", "Keep still unless clearly safe to move: $moving")
            section("SETTING", plan.setting.trim().ifBlank { "A real everyday space that matches how THIS product is used." })
            section("CAMERA", plan.camera.trim().ifBlank { "Slight handheld, natural crop, not studio-centered." })
            section("ACTION", plan.action.trim().ifBlank { "One low-risk natural interaction with this exact product." })
            section("HUMAN BEHAVIOUR", plan.humanBehaviour.trim().ifBlank { "Casual, unscripted, no presenter cadence." })
            section("LIGHTING", plan.lighting.trim().ifBlank { "Real room light. No studio setup." })
            section("SPEECH", speech)
            section("ANTI-MORPH", antiMorph)
            section("TIMING", TIMING)
        }.trim()
    }

    fun headingCounts(prompt: String): Map<String, Int> {
        val counts = HEADINGS.associateWith { 0 }.toMutableMap()
        prompt.lineSequence().forEach { line ->
            val heading = HEADINGS.firstOrNull { line.trim().equals("$it:", true) || line.trim().equals(it, true) }
            if (heading != null) counts[heading] = (counts[heading] ?: 0) + 1
        }
        return counts
    }

    fun isCanonical(prompt: String): Boolean {
        val counts = headingCounts(prompt)
        return HEADINGS.all { counts[it] == 1 } && prompt.trim().endsWith("transition tail.")
    }

    private fun StringBuilder.section(heading: String, body: String) {
        if (isNotEmpty()) append("\n\n")
        append(heading).append(":\n").append(body.trim())
    }

    private fun languageLabel(language: de.spardirekt.ugcclean.model.SpeechLanguage): String =
        if (language == de.spardirekt.ugcclean.model.SpeechLanguage.DE) "German" else "Russian"

    private fun defaultHook(language: de.spardirekt.ugcclean.model.SpeechLanguage): String =
        if (language == de.spardirekt.ugcclean.model.SpeechLanguage.DE) {
            "Schau mal, das hier benutze ich einfach so."
        } else {
            "Смотри, вот так я этим пользуюсь."
        }
}
