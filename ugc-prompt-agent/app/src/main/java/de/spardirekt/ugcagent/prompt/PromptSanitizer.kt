package de.spardirekt.ugcagent.prompt

/**
 * Final-output gate for Veo/Kling prompts.
 * The photo is the visual identity — the text must stay a short UGC micro-moment.
 */
object PromptSanitizer {
    const val MAX_WORDS = 80

    private val FENCE_OPEN = Regex("^```(?:[a-zA-Z0-9_-]+)?\\s*")
    private val FENCE_CLOSE = Regex("\\s*```$")
    private val MULTI_SPACE = Regex("\\s+")

    /**
     * Product-optics leaks the system prompt already forbids.
     * Warning only — never auto-rewrite product identity.
     */
    private val VISUAL_LEAK = Regex(
        """(?i)\b(form|farbe|material|grö(?:ß|ss)e|marke|brand(?:name)?|silhouett(?:e|en)|verpackungsdesign|produktfarbe|produktform)\b""",
    )

    fun clean(raw: String): String {
        var text = raw.trim()
        text = text.replace(FENCE_OPEN, "")
        text = text.replace(FENCE_CLOSE, "")
        text = text.removeSurrounding("\"").removeSurrounding("'").trim()
        text = text.replace(MULTI_SPACE, " ").trim()
        if (text.isEmpty()) return ""
        val words = text.split(' ').filter { it.isNotBlank() }
        return words.take(MAX_WORDS).joinToString(" ")
    }

    fun wordCount(text: String): Int =
        text.trim().split(MULTI_SPACE).count { it.isNotBlank() }

    fun hasVisualProductLeak(text: String): Boolean =
        VISUAL_LEAK.containsMatchIn(text)

    fun evaluate(raw: String): SanitizedPrompt {
        val prompt = clean(raw)
        return SanitizedPrompt(
            prompt = prompt,
            wordCount = wordCount(prompt),
            truncated = wordCount(raw.replace(FENCE_OPEN, "").replace(FENCE_CLOSE, "")) > MAX_WORDS,
            visualLeak = hasVisualProductLeak(prompt),
        )
    }
}

data class SanitizedPrompt(
    val prompt: String,
    val wordCount: Int,
    val truncated: Boolean,
    val visualLeak: Boolean,
)
