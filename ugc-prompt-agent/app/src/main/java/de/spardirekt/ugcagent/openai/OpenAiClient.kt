package de.spardirekt.ugcagent.openai

import de.spardirekt.ugcagent.data.ImageCompressor
import de.spardirekt.ugcagent.data.StoredImage
import de.spardirekt.ugcagent.prompt.SceneIdea
import de.spardirekt.ugcagent.prompt.SystemPrompts
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class ApiException(val code: String, message: String) : Exception(message)

class OpenAiClient(
    private val http: OkHttpClient = defaultClient(),
) {

    fun analyze(apiKey: String, images: List<StoredImage>): JSONObject {
        require(images.isNotEmpty()) { "no_images" }
        val user = """
Analysiere diese Produktfotos. Antworte ausschließlich mit dem geforderten JSON.
Keine visuelle Beschreibung, keine Marke, keine Form/Farbe/Material.
""".trimIndent()
        val text = generate(
            apiKey = apiKey,
            system = SystemPrompts.VISION_ANALYSIS,
            userText = user,
            images = images,
            temperature = 0.2,
            maxTokens = 1024,
            jsonMode = true,
        )
        return extractJson(text)
    }

    fun buildPrompt(
        apiKey: String,
        analysis: JSONObject,
        scene: SceneIdea,
        firstFrame: StoredImage,
    ): String {
        val user = """
Kontext-JSON (nur Funktion/Zielgruppe/Alltag, keine Optik):
$analysis

Gewählte Szene — GENAU DIESE Kombination verwenden, keine andere:
- Opener: ${scene.opener}
- Umgebung: ${scene.environment}
- Handlung: ${scene.action}

First-Frame: das hochgeladene Originalfoto ist die visuelle Quelle. Nicht beschreiben.
Schreibe NUR den Veo-Prompt (max. 80 Wörter, Deutsch, 9:16, max. 8 Sekunden, ein Mikro-Moment).
""".trimIndent()
        return generate(
            apiKey = apiKey,
            system = SystemPrompts.VIDEO_PROMPT,
            userText = user,
            images = listOf(firstFrame),
            temperature = 0.85,
            maxTokens = 512,
            jsonMode = false,
        ).trim().removeSurrounding("\"").trim()
    }

    fun improvePrompt(apiKey: String, current: String, scene: SceneIdea): String {
        val user = """
Aktueller Prompt:
$current

Szene bleibt: ${scene.opener} / ${scene.environment} / ${scene.action}
""".trimIndent()
        return generate(
            apiKey = apiKey,
            system = SystemPrompts.IMPROVE_PASS,
            userText = user,
            images = emptyList(),
            temperature = 0.5,
            maxTokens = 512,
            jsonMode = false,
        ).trim().removeSurrounding("\"").trim()
    }

    private fun generate(
        apiKey: String,
        system: String,
        userText: String,
        images: List<StoredImage>,
        temperature: Double,
        maxTokens: Int,
        jsonMode: Boolean,
    ): String {
        if (apiKey.isBlank()) throw ApiException("NO_API_KEY", "missing_api_key")
        var lastError: ApiException? = null
        for (model in MODELS) {
            try {
                return request(
                    apiKey = apiKey,
                    model = model,
                    system = system,
                    userText = userText,
                    images = images,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    jsonMode = jsonMode,
                )
            } catch (e: ApiException) {
                lastError = e
                if (e.code == "NOT_FOUND") continue
                throw e
            }
        }
        throw lastError ?: ApiException("GENERIC", "openai_failed")
    }

    private fun request(
        apiKey: String,
        model: String,
        system: String,
        userText: String,
        images: List<StoredImage>,
        temperature: Double,
        maxTokens: Int,
        jsonMode: Boolean,
    ): String {
        val userContent = JSONArray()
        userContent.put(JSONObject().put("type", "text").put("text", userText))
        val detail = if (model.startsWith("gpt-5.6")) "original" else "high"
        images.forEach { image ->
            userContent.put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject()
                            .put("url", ImageCompressor.fileToDataUrl(image.file))
                            .put("detail", detail),
                    ),
            )
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", userContent))

        val gpt5 = model.startsWith("gpt-5")
        val payload = JSONObject().put("model", model).put("messages", messages)
        if (jsonMode) {
            payload.put("response_format", JSONObject().put("type", "json_object"))
        }
        if (gpt5) {
            payload.put("max_completion_tokens", (maxTokens + 4_000).coerceAtMost(16_384))
            payload.put("reasoning_effort", if (model.contains("luna")) "low" else "medium")
        } else {
            payload.put("temperature", temperature)
            payload.put("max_tokens", maxTokens)
        }

        val req = Request.Builder()
            .url(CHAT_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        return try {
            http.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> parseText(raw)
                    400 -> {
                        if (raw.contains("too large", ignoreCase = true) ||
                            raw.contains("context_length", ignoreCase = true) ||
                            raw.contains("image", ignoreCase = true) && raw.contains("limit", ignoreCase = true)
                        ) {
                            throw ApiException("IMAGE_TOO_LARGE", raw)
                        }
                        if (raw.contains("model", ignoreCase = true) &&
                            (raw.contains("not found", ignoreCase = true) || raw.contains("does not exist", ignoreCase = true))
                        ) {
                            throw ApiException("NOT_FOUND", raw)
                        }
                        throw ApiException("GENERIC", raw)
                    }
                    401, 403 -> throw ApiException("INVALID_API_KEY", raw)
                    404 -> throw ApiException("NOT_FOUND", raw)
                    429 -> throw ApiException("RATE_LIMIT", raw)
                    else -> throw ApiException("GENERIC", "HTTP ${response.code}: $raw")
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (_: SocketTimeoutException) {
            throw ApiException("TIMEOUT", "timeout")
        } catch (_: IOException) {
            throw ApiException("NETWORK", "network")
        }
    }

    private fun parseText(raw: String): String {
        val json = JSONObject(raw)
        val choices = json.optJSONArray("choices") ?: throw ApiException("PARSE_ERROR", raw)
        if (choices.length() == 0) throw ApiException("PARSE_ERROR", raw)
        val text = choices.getJSONObject(0)
            .optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) throw ApiException("PARSE_ERROR", raw)
        return text
    }

    private fun extractJson(text: String): JSONObject {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        val candidate = fenced?.trim() ?: text.trim()
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) throw ApiException("PARSE_ERROR", text)
        return JSONObject(candidate.substring(start, end + 1))
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-5.6-sol"
        val MODELS = listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-4o")
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
