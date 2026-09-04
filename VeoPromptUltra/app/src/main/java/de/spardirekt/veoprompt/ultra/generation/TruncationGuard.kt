package de.spardirekt.veoprompt.ultra.generation

/**
 * Hard guard: stored veoPrompt must never be mechanically shortened.
 * Detection only — never used as a formatter.
 */
object TruncationGuard {
    private val ellipsis = listOf("...", "…", "……")

    fun looksMechanicallyTruncated(text: String): Boolean {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return false
        if (ellipsis.any { trimmed.endsWith(it) }) return true
        val last = trimmed.last()
        val endsSentence = last == '.' || last == '!' || last == '?' || last == '"' || last == '\n'
        val lastLine = trimmed.lineSequence().lastOrNull()?.trim().orEmpty()
        if (lastLine.endsWith("-") && lastLine.length < 24) return true
        if (!endsSentence && last.isLetterOrDigit() && trimmed.length > 40) {
            val lastWord = lastLine.substringAfterLast(' ')
            if (lastWord.length >= 12 && lastLine.length < 40) return true
        }
        return false
    }

    fun containsBrokenSentence(text: String): Boolean {
        val lastLine = text.trim().lineSequence().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (lastLine.isEmpty()) return true
        if (lastLine.endsWith(",") || lastLine.endsWith(":") || lastLine.endsWith(";")) return true
        if (lastLine.endsWith(" the") || lastLine.endsWith(" a") || lastLine.endsWith(" and")) return true
        return looksMechanicallyTruncated(text)
    }
}
