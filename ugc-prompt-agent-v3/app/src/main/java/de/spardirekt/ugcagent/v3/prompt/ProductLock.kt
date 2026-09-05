package de.spardirekt.ugcagent.v3.prompt

object ProductLock {
    const val LOCK_TEXT = """Use the uploaded reference image as the strict visual identity reference.

Keep exactly the same physical product throughout the entire clip.

Do not redesign, reinterpret, replace, duplicate or morph it.

Do not add unseen controls, parts, accessories or features.

Do not change visible construction.

If an action requires changing the product, simplify the action instead.

REFERENCE IMAGE OVERRIDES TEXTUAL INTERPRETATION."""

    private val appearanceLeak = Regex(
        """\b(black|white|red|blue|green|silver|gold|wooden|metal|plastic|round|square|brand|logo|stainless)\b.+\b(pan|handle|product|item)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun ensure(prompt: String, lockOn: Boolean): String {
        val body = prompt.trim()
        if (!lockOn) return body
        return if (body.contains("REFERENCE IMAGE OVERRIDES", ignoreCase = true)) {
            body
        } else {
            "$LOCK_TEXT\n\n$body"
        }
    }

    fun ensureNoSpeech(prompt: String): String {
        return if (prompt.contains("No spoken dialogue", ignoreCase = true)) {
            prompt
        } else {
            prompt.trimEnd() + "\n\nNo spoken dialogue."
        }
    }

    fun applyGenerator(prompt: String, generator: String): String {
        val note = when (generator.uppercase()) {
            "VEO" -> "Target generator: Veo. Vertical 9:16, maximum 8.0 seconds, one continuous clip."
            "KLING" -> "Target generator: Kling. Vertical 9:16, maximum 8.0 seconds, one continuous clip."
            else -> "Target generator: generic short-form video model. Vertical 9:16, maximum 8.0 seconds, one continuous clip."
        }
        return if (prompt.contains("Target generator:", ignoreCase = true)) prompt else "$note\n\n$prompt"
    }

    fun looksLikeProductRebuild(prompt: String): Boolean = appearanceLeak.containsMatchIn(prompt)

    fun wordCount(prompt: String): Int = prompt.split(Regex("\\s+")).filter { it.isNotBlank() }.size
}
