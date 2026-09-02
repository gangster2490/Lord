package de.spardirekt.recipeveo.network

/**
 * Chooses the live OpenAI client or the offline demo client.
 *
 * Paste an API key that starts with `sk-demo` (Settings or the generate dialog)
 * to run PHOTO_ANALYSIS → RESULT without calling OpenAI. The Settings screen
 * is unchanged — there is no extra demo toggle.
 */
object ChatClients {
    const val DEMO_PREFIX = "sk-demo"

    fun isDemoKey(apiKey: String): Boolean =
        apiKey.trim().startsWith(DEMO_PREFIX, ignoreCase = true)

    fun fromApiKey(apiKey: String, live: ChatClient = OpenAiClient()): ChatClient =
        if (isDemoKey(apiKey)) DemoChatClient else live
}
