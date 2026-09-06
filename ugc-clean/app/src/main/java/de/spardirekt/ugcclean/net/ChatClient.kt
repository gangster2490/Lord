package de.spardirekt.ugcclean.net

interface ChatClient {
    suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        jsonMode: Boolean,
    ): String
}

class AiException(message: String, val retryable: Boolean = false) : Exception(message)

object Keys {
    fun isDemo(apiKey: String): Boolean = apiKey.trim().equals("sk-demo", ignoreCase = true)
}
