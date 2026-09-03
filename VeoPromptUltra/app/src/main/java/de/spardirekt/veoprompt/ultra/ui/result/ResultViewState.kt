package de.spardirekt.veoprompt.ultra.ui.result

data class ResultViewState(
    val projectId: String = "",
    val veoPrompt: String = "",
    val voiceover: String = "",
    val title: String = "",
    val hashtags: List<String> = emptyList(),
    val safetyRisk: String = "LOW",
    val safetyItems: List<String> = emptyList(),
    val safetyPolicyVersion: String = "",
    val aigcPolicyVersion: String = "",
    val aigcVerdict: String = "",
    val aigcShopPublishSafe: Boolean = true,
    val aigcChecklist: List<String> = emptyList(),
    val aigcPublishSteps: List<String> = emptyList(),
    val geminiPolicyVersion: String = "",
    val geminiVerdict: String = "",
    val geminiSubmissionSafe: Boolean = true,
    val geminiChecklist: List<String> = emptyList(),
    val geminiPublishSteps: List<String> = emptyList(),
    val tiktokShopMode: Boolean = true,
    val language: String = "DE",
    val creativeMode: String = "AUTO",
    val status: String = "",
    val expanded: Boolean = false,
    val productIdentity: String = "",
    val visualSignature: List<String> = emptyList(),
    val appMode: String = "Simple",
    val loaded: Boolean = false,
    val copyNotice: String? = null
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

    fun aigcChecklistText(): String = buildString {
        append("TikTok Shop AIGC · ${aigcVerdict.ifBlank { "—" }}")
        if (aigcPolicyVersion.isNotBlank()) append(" · $aigcPolicyVersion")
        append('\n')
        aigcChecklist.forEach { append(it).append('\n') }
        if (aigcPublishSteps.isNotEmpty()) {
            append('\n')
            aigcPublishSteps.forEachIndexed { i, step ->
                append("${i + 1}. ").append(step).append('\n')
            }
        }
    }.trim()

    fun geminiChecklistText(): String = buildString {
        append("Gemini / VEO · ${geminiVerdict.ifBlank { "—" }}")
        if (geminiPolicyVersion.isNotBlank()) append(" · $geminiPolicyVersion")
        append('\n')
        geminiChecklist.forEach { append(it).append('\n') }
        if (geminiPublishSteps.isNotEmpty()) {
            append('\n')
            geminiPublishSteps.forEachIndexed { i, step ->
                append("${i + 1}. ").append(step).append('\n')
            }
        }
    }.trim()
}
