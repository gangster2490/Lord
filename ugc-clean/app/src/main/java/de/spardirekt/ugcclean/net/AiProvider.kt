package de.spardirekt.ugcclean.net

enum class AiProviderId {
    OPENAI,
    GEMINI,
    CLAUDE,
    ;

    companion object {
        fun fromStored(raw: String?): AiProviderId {
            if (raw.equals("anthropic", ignoreCase = true)) return CLAUDE
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OPENAI
        }
    }
}

object AiModelConfig {
    const val OPENAI_PRIMARY = "gpt-5.6-sol"
    const val OPENAI_FALLBACK = "gpt-5.6-terra"
    const val OPENAI_FALLBACK_2 = "gpt-4o"

    const val GEMINI_PRIMARY = "gemini-2.5-flash"
    const val GEMINI_FALLBACK = "gemini-2.0-flash"
    const val GEMINI_FALLBACK_2 = "gemini-1.5-flash"

    const val CLAUDE_PRIMARY = "claude-sonnet-4-5"
    const val CLAUDE_FALLBACK = "claude-opus-4-5"
    const val CLAUDE_FALLBACK_2 = "claude-3-5-sonnet-latest"

    val openaiModels: List<String> = listOf(OPENAI_PRIMARY, OPENAI_FALLBACK, OPENAI_FALLBACK_2)
    val geminiModels: List<String> = listOf(GEMINI_PRIMARY, GEMINI_FALLBACK, GEMINI_FALLBACK_2)
    val claudeModels: List<String> = listOf(CLAUDE_PRIMARY, CLAUDE_FALLBACK, CLAUDE_FALLBACK_2)

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

    fun claudeCandidates(available: Set<String>?): List<String> {
        if (available.isNullOrEmpty()) return claudeModels
        return claudeModels.filter { it in available }.ifEmpty { claudeModels }
    }
}

data class ProviderAttempt(
    val provider: AiProviderId,
    val apiKey: String,
)

class ProviderClients(
    val openAi: OpenAiClient = OpenAiClient(),
    val gemini: GeminiClient = GeminiClient(),
    val claude: ClaudeClient = ClaudeClient(),
    val demo: DemoClient = DemoClient(),
) {
    fun forKey(provider: AiProviderId, apiKey: String): ChatClient {
        if (Keys.isDemo(apiKey)) return demo
        return when (provider) {
            AiProviderId.OPENAI -> openAi
            AiProviderId.GEMINI -> gemini
            AiProviderId.CLAUDE -> claude
        }
    }

    fun test(provider: AiProviderId, apiKey: String): String {
        if (Keys.isDemo(apiKey)) return "Demo-Modus bereit"
        return when (provider) {
            AiProviderId.OPENAI -> openAi.testConnection(apiKey)
            AiProviderId.GEMINI -> gemini.testConnection(apiKey)
            AiProviderId.CLAUDE -> claude.testConnection(apiKey)
        }
    }

    companion object {
        fun attemptOrder(
            primary: AiProviderId,
            keyFor: (AiProviderId) -> String,
        ): List<ProviderAttempt> {
            val firstKey = keyFor(primary)
            val order = mutableListOf(ProviderAttempt(primary, firstKey))
            if (Keys.isDemo(firstKey) || firstKey.isBlank()) return order
            AiProviderId.entries.forEach { id ->
                if (id == primary) return@forEach
                val altKey = keyFor(id)
                if (altKey.isNotBlank() && !Keys.isDemo(altKey)) {
                    order += ProviderAttempt(id, altKey)
                }
            }
            return order
        }
    }
}
