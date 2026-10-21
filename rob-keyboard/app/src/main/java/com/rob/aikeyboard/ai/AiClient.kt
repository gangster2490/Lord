package com.rob.aikeyboard.ai

import com.rob.aikeyboard.data.AiProvider
import com.rob.aikeyboard.data.Prefs
import com.rob.aikeyboard.security.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiException(message: String) : Exception(message)

/**
 * Calls the official OpenAI and Anthropic HTTP APIs with the user's own key (stored encrypted
 * on the device, never bundled in the APK). Only ever invoked after an explicit user action.
 * The base URL can point to a self-hosted HTTPS proxy instead.
 */
class AiClient(private val prefs: Prefs, private val secure: SecureStore) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    fun isConfigured(provider: AiProvider): Boolean = secure.hasSecret(keyName(provider))

    /** The chosen provider if it has a key, otherwise any provider that has one. */
    fun configuredProvider(): AiProvider? =
        prefs.aiProvider.takeIf(::isConfigured) ?: AiProvider.entries.firstOrNull(::isConfigured)

    private fun keyName(provider: AiProvider) = when (provider) {
        AiProvider.GEMINI -> SecureStore.GEMINI_KEY
        AiProvider.OPENAI -> SecureStore.OPENAI_KEY
        AiProvider.ANTHROPIC -> SecureStore.ANTHROPIC_KEY
    }

    suspend fun complete(provider: AiProvider, system: String, user: String, maxTokens: Int = 800): String =
        withContext(Dispatchers.IO) {
            val key = secure.getSecret(keyName(provider))
                ?: throw AiException("API-ключ для ${provider.label} не задан. Откройте настройки ROB Keyboard → AI.")
            when (provider) {
                AiProvider.GEMINI -> gemini(key, system, user, maxTokens)
                AiProvider.OPENAI -> openAi(key, system, user, maxTokens)
                AiProvider.ANTHROPIC -> anthropic(key, system, user, maxTokens)
            }
        }

    /** Google Gemini API (free tier available with a key from aistudio.google.com). */
    private fun gemini(key: String, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents",
                JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))),
            )
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens * 4).put("responseMimeType", "application/json"))
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${prefs.geminiModel}:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(json))
            .build()
        val response = execute(request)
        val parts = response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return (0 until parts.length()).map { parts.getJSONObject(it) }
            .filter { !it.optBoolean("thought") }
            .joinToString("") { it.optString("text") }
    }

    private fun openAi(key: String, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", prefs.openAiModel)
            .put("max_completion_tokens", maxTokens * 4) // reasoning models spend tokens before answering
            .apply {
                // Keyboard corrections must come back fast; reasoning models accept a minimal effort.
                val m = prefs.openAiModel.lowercase()
                if (m.startsWith("gpt-5")) put("reasoning_effort", "minimal")
            }
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        val request = Request.Builder()
            .url(prefs.openAiBaseUrl.trimEnd('/') + "/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(json))
            .build()
        val response = execute(request)
        return response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
    }

    private fun anthropic(key: String, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", prefs.anthropicModel)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        val request = Request.Builder()
            .url(prefs.anthropicBaseUrl.trimEnd('/') + "/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(json))
            .build()
        val response = execute(request)
        val content = response.getJSONArray("content")
        return (0 until content.length()).map { content.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("") { it.optString("text") }
    }

    private fun execute(request: Request): JSONObject {
        if (!request.url.isHttps) throw AiException("Разрешены только HTTPS-адреса.")
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull()
                throw AiException(
                    when (r.code) {
                        401, 403 -> "Ключ отклонён (HTTP ${r.code}). Проверьте API-ключ."
                        429 -> "Превышен лимит запросов (HTTP 429). Попробуйте позже."
                        else -> "Ошибка HTTP ${r.code}${msg?.let { ": $it" } ?: ""}"
                    },
                )
            }
            return JSONObject(text)
        }
    }
}
