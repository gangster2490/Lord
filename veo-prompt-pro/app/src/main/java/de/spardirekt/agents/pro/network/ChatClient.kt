package de.spardirekt.agents.pro.network

/**
 * LLM chat used by [de.spardirekt.agents.pro.generation.GenerationPipeline].
 * Production uses [OpenAiClient]; [DemoChatClient] runs the same stages without a network.
 */
interface ChatClient {
    suspend fun chat(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String> = emptyList(),
        timeoutSeconds: Long,
        jsonMode: Boolean = true,
        temperature: Double = 0.4,
        maxTokens: Int = 4096,
        maxAttempts: Int = 2,
        reasoningEffort: String? = null
    ): Result<String>
}
