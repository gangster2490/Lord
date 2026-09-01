package de.spardirekt.veoprompt.ultra.network

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
        maxAttempts: Int = 2
    ): Result<String>
}
