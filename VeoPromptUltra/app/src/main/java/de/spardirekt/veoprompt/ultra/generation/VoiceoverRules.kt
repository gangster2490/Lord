package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.VoiceLanguage

object VoiceoverRules {

    fun wordCount(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    fun preferredRange(language: VoiceLanguage): IntRange? = when (language) {
        VoiceLanguage.DE -> 10..16
        VoiceLanguage.RU -> 12..18
        VoiceLanguage.OFF -> null
    }

    fun issues(text: String, language: VoiceLanguage): List<String> {
        if (language == VoiceLanguage.OFF) return emptyList()
        val line = text.trim()
        if (line.isEmpty() || line.equals("OFF", true)) return listOf("voiceover_empty")
        val words = wordCount(line)
        val range = preferredRange(language) ?: return emptyList()
        val out = mutableListOf<String>()
        if (words !in range) out += "voiceover_word_count:$words"
        if (TruncationGuard.looksMechanicallyTruncated(line)) out += "voiceover_truncated"
        return out
    }
}
