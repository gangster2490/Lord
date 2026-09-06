package de.spardirekt.ugcclean.net

import de.spardirekt.ugcclean.gen.JsonExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : ChatClient {

    override suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        jsonMode: Boolean,
    ): String {
        val models = AiModelConfig.geminiModels
        var last: Exception? = null
        for ((index, model) in models.withIndex()) {
            try {
                return execute(apiKey, model, systemPrompt, userText, imageDataUrls, jsonMode)
            } catch (e: AiException) {
                last = e
                val unavailable = e.message?.contains("not found", true) == true ||
                    e.message?.contains("not supported", true) == true ||
                    e.message?.contains("404") == true
                if (!unavailable && !e.retryable) throw e
                if (index == models.lastIndex) throw e
            } catch (e: IOException) {
                last = e
                if (index == models.lastIndex) {
                    throw AiException("Netzwerkfehler: ${e.message}", retryable = true)
                }
            }
        }
        throw last ?: AiException("Gemini-Anfrage fehlgeschlagen")
    }

    fun testConnection(apiKey: String): String {
        if (Keys.isDemo(apiKey)) return "Demo-Modus bereit"
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiException(httpError(response.code, raw))
            }
            return "Verbindung OK"
        }
    }

    private fun execute(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        jsonMode: Boolean,
    ): String {
        val body = GeminiPayload.build(systemPrompt, userText, imageDataUrls, jsonMode)
        val modelPath = if (model.startsWith("models/")) model else "models/$model"
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/$modelPath:generateContent?key=${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiException(httpError(response.code, raw), retryable = response.code == 429)
            }
            val content = GeminiPayload.parseText(json, raw)
            if (content.isBlank()) throw AiException("Leere Gemini-Antwort")
            return content
        }
    }

    private fun httpError(code: Int, raw: String): String {
        val snippet = JsonExtractor.extractObject(raw).take(280)
        return "Gemini HTTP $code: $snippet"
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

object GeminiPayload {
    fun build(
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        jsonMode: Boolean,
    ): JsonObject = buildJsonObject {
        put(
            "systemInstruction",
            buildJsonObject {
                put(
                    "parts",
                    buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(systemPrompt)) })
                    },
                )
            },
        )
        put(
            "contents",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put(
                            "parts",
                            buildJsonArray {
                                add(buildJsonObject { put("text", JsonPrimitive(userText)) })
                                imageDataUrls.forEach { url ->
                                    val (mime, data) = parseDataUrl(url)
                                    add(
                                        buildJsonObject {
                                            put(
                                                "inline_data",
                                                buildJsonObject {
                                                    put("mime_type", JsonPrimitive(mime))
                                                    put("data", JsonPrimitive(data))
                                                },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
        put(
            "generationConfig",
            buildJsonObject {
                put("temperature", JsonPrimitive(0.4))
                if (jsonMode) put("responseMimeType", JsonPrimitive("application/json"))
            },
        )
    }

    fun parseText(json: Json, raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return ""
        val first = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return ""
        val parts = (first["content"] as? JsonObject)?.get("parts") as? JsonArray ?: return ""
        return parts.joinToString("") { part ->
            (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
        }
    }

    fun parseDataUrl(url: String): Pair<String, String> {
        val match = DATA_URL.find(url.trim())
        return if (match != null) {
            match.groupValues[1] to match.groupValues[2]
        } else {
            "image/jpeg" to url.trim()
        }
    }

    private val DATA_URL = Regex("^data:([^;]+);base64,(.+)$", RegexOption.DOT_MATCHES_ALL)
}
