package de.spardirekt.ugcclean.net

enum class AiProviderId {
    OPENAI,
    GEMINI,
    ;

    companion object {
        fun fromStored(raw: String?): AiProviderId =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OPENAI
    }
}

object AiModelConfig {
    const val OPENAI_PRIMARY = "gpt-5.6-sol"
    const val OPENAI_FALLBACK = "gpt-5.6-terra"
    const val OPENAI_FALLBACK_2 = "gpt-4o"

    const val GEMINI_PRIMARY = "gemini-2.5-flash"
    const val GEMINI_FALLBACK = "gemini-2.0-flash"
    const val GEMINI_FALLBACK_2 = "gemini-1.5-flash"

    val openaiModels: List<String> = listOf(OPENAI_PRIMARY, OPENAI_FALLBACK, OPENAI_FALLBACK_2)
    val geminiModels: List<String> = listOf(GEMINI_PRIMARY, GEMINI_FALLBACK, GEMINI_FALLBACK_2)

    fun openaiCandidates(available: Set<String>?): List<String> {
        if (available.isNullOrEmpty()) return openaiModels
        return openaiModels.filter { it in available }.ifEmpty { openaiModels }
    }

    fun geminiCandidates(available: Set<String>?): List<String> {
        if (available.isNullOrEmpty()) return geminiModels
        val filtered = geminiModels.filter { model ->
            available.any { it == model || it.endsWith("/$model") || it.contains(model) }
        }
        return filtered.ifEmpty { geminiModels }
    }
}

class ProviderClients(
    val openAi: OpenAiClient = OpenAiClient(),
    val gemini: GeminiClient = GeminiClient(),
    val demo: DemoClient = DemoClient(),
) {
    fun forKey(provider: AiProviderId, apiKey: String): ChatClient {
        if (Keys.isDemo(apiKey)) return demo
        return when (provider) {
            AiProviderId.OPENAI -> openAi
            AiProviderId.GEMINI -> gemini
        }
    }

    fun test(provider: AiProviderId, apiKey: String): String {
        if (Keys.isDemo(apiKey)) return "Demo-Modus bereit"
        return when (provider) {
            AiProviderId.OPENAI -> openAi.testConnection(apiKey)
            AiProviderId.GEMINI -> gemini.testConnection(apiKey)
        }
    }
}
