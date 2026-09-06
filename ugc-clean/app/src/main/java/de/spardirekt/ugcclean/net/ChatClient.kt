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

object Keys {
    fun isDemo(apiKey: String): Boolean = apiKey.trim().equals("sk-demo", ignoreCase = true)
}
