package de.spardirekt.ugcagent.gemini

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

class GeminiException(val code: String, message: String) : Exception(message)

class GeminiClient(
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
        ).trim().removeSurrounding("\"").trim()
    }

    private fun generate(
        apiKey: String,
        system: String,
        userText: String,
        images: List<StoredImage>,
        temperature: Double,
        maxTokens: Int,
    ): String {
        if (apiKey.isBlank()) throw GeminiException("NO_API_KEY", "missing_api_key")
        val parts = JSONArray()
        parts.put(JSONObject().put("text", "$system\n\n$userText"))
        images.forEach { image ->
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", ImageCompressor.fileToBase64(image.file)),
                ),
            )
        }
        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(JSONObject().put("parts", parts)),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", temperature)
                    .put("maxOutputTokens", maxTokens),
            )

        var lastError: GeminiException? = null
        for (model in MODELS) {
            try {
                return request(apiKey, model, payload)
            } catch (e: GeminiException) {
                lastError = e
                if (e.code == "NOT_FOUND") continue
                throw e
            }
        }
        throw lastError ?: GeminiException("GENERIC", "gemini_failed")
    }

    private fun request(apiKey: String, model: String, payload: JSONObject): String {
        val url = "$BASE/models/$model:generateContent?key=$apiKey"
        val body = payload.toString().toRequestBody(JSON)
        val req = Request.Builder().url(url).post(body).build()
        return try {
            http.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> parseText(raw)
                    400, 401, 403 -> {
                        if (raw.contains("too large", ignoreCase = true) ||
                            raw.contains("payload", ignoreCase = true) ||
                            raw.contains("REQUEST_TOO_LARGE", ignoreCase = true)
                        ) {
                            throw GeminiException("IMAGE_TOO_LARGE", raw)
                        }
                        if (raw.contains("API_KEY_INVALID", ignoreCase = true) ||
                            raw.contains("API key not valid", ignoreCase = true) ||
                            response.code == 401 ||
                            response.code == 403
                        ) {
                            throw GeminiException("INVALID_API_KEY", raw)
                        }
                        throw GeminiException("GENERIC", raw)
                    }
                    404 -> throw GeminiException("NOT_FOUND", raw)
                    429 -> throw GeminiException("RATE_LIMIT", raw)
                    else -> throw GeminiException("GENERIC", "HTTP ${response.code}: $raw")
                }
            }
        } catch (e: GeminiException) {
            throw e
        } catch (_: SocketTimeoutException) {
            throw GeminiException("TIMEOUT", "timeout")
        } catch (_: IOException) {
            throw GeminiException("NETWORK", "network")
        }
    }

    private fun parseText(raw: String): String {
        val json = JSONObject(raw)
        val candidates = json.optJSONArray("candidates") ?: throw GeminiException("PARSE_ERROR", raw)
        if (candidates.length() == 0) throw GeminiException("PARSE_ERROR", raw)
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw GeminiException("PARSE_ERROR", raw)
        val text = buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text"))
            }
        }.trim()
        if (text.isEmpty()) throw GeminiException("PARSE_ERROR", raw)
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
        if (start < 0 || end <= start) throw GeminiException("PARSE_ERROR", text)
        return JSONObject(candidate.substring(start, end + 1))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private val MODELS = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-flash-latest")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
