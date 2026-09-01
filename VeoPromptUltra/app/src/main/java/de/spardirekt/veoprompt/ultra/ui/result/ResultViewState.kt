package de.spardirekt.veoprompt.ultra.ui.result

data class ResultViewState(
    val projectId: String = "",
    val veoPrompt: String = "",
    val voiceover: String = "",
    val title: String = "",
    val hashtags: List<String> = emptyList(),
    val safetyRisk: String = "LOW",
    val safetyItems: List<String> = emptyList(),
    val language: String = "DE",
    val creativeMode: String = "AUTO",
    val status: String = "",
    val expanded: Boolean = false,
    val productIdentity: String = "",
    val loaded: Boolean = false
) {
    fun preview(): String {
        val text = veoPrompt
        if (text.length <= 280) return text
        val cut = text.indexOf('\n', 240).takeIf { it > 0 } ?: 280
        return text.substring(0, cut).trimEnd()
    }

    fun packageText(): String = buildString {
        append(veoPrompt.trim())
        append("\n\n")
        append(voiceover.trim())
        append("\n\n")
        append(title.trim())
        append("\n\n")
        append(hashtags.joinToString(" "))
    }
}
