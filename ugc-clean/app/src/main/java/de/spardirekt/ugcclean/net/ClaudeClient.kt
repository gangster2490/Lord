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

class ClaudeClient(
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
        val models = AiModelConfig.claudeModels
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
        throw last ?: AiException("Claude-Anfrage fehlgeschlagen")
    }

    fun testConnection(apiKey: String): String {
        if (Keys.isDemo(apiKey)) return "Demo-Modus bereit"
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .addHeader("x-api-key", apiKey.trim())
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
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
        val body = ClaudePayload.build(model, systemPrompt, userText, imageDataUrls, jsonMode)
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey.trim())
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiException(httpError(response.code, raw), retryable = response.code == 429)
            }
            val content = ClaudePayload.parseText(json, raw)
            if (content.isBlank()) throw AiException("Leere Claude-Antwort")
            return content
        }
    }

    private fun httpError(code: Int, raw: String): String {
        val snippet = JsonExtractor.extractObject(raw).take(280)
        return "Claude HTTP $code: $snippet"
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}

object ClaudePayload {
    fun build(
        model: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        jsonMode: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put("max_tokens", JsonPrimitive(4096))
        put("temperature", JsonPrimitive(0.4))
        val system = if (jsonMode) "$systemPrompt\n\nReturn a single JSON object only." else systemPrompt
        put("system", JsonPrimitive(system))
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("text"))
                                        put("text", JsonPrimitive(userText))
                                    },
                                )
                                imageDataUrls.forEach { url ->
                                    val (mime, data) = parseDataUrl(url)
                                    add(
                                        buildJsonObject {
                                            put("type", JsonPrimitive("image"))
                                            put(
                                                "source",
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("base64"))
                                                    put("media_type", JsonPrimitive(mime))
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
    }

    fun parseText(json: Json, raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return ""
        val content = root["content"] as? JsonArray ?: return ""
        return content.joinToString("") { part ->
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
