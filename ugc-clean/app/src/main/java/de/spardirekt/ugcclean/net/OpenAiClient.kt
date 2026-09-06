package de.spardirekt.ugcclean.net

import de.spardirekt.ugcclean.gen.JsonExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

class OpenAiException(message: String, val retryable: Boolean = false) : Exception(message)

class OpenAiClient(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val primaryModel: String = "gpt-5.6-sol",
    private val fallbackModel: String = "gpt-4o",
) : ChatClient {

    override suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        jsonMode: Boolean,
    ): String {
        val attempts = listOf(
            RequestShape(primaryModel, jsonMode, includeReasoning = true),
            RequestShape(primaryModel, jsonMode = false, includeReasoning = false),
            RequestShape(fallbackModel, jsonMode, includeReasoning = false),
            RequestShape(fallbackModel, jsonMode = false, includeReasoning = false),
        )
        var last: Exception? = null
        for ((index, shape) in attempts.withIndex()) {
            try {
                return execute(apiKey, systemPrompt, userText, imageDataUrls, shape)
            } catch (e: OpenAiException) {
                last = e
                val unsupported = e.message?.contains("unsupported_parameter", true) == true
                val rate = e.message?.contains("429") == true || e.retryable
                if (!unsupported && !rate) throw e
                if (index == attempts.lastIndex) throw e
            } catch (e: IOException) {
                last = e
                if (index == attempts.lastIndex) {
                    throw OpenAiException("Netzwerkfehler: ${e.message}", retryable = true)
                }
            }
        }
        throw last ?: OpenAiException("OpenAI-Anfrage fehlgeschlagen")
    }

    fun testConnection(apiKey: String): String {
        if (Keys.isDemo(apiKey)) return "Demo-Modus bereit"
        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OpenAiException(httpError(response.code, raw))
            }
            return "Verbindung OK"
        }
    }

    private fun execute(
        apiKey: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        shape: RequestShape,
    ): String {
        val userContent = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(userText))
            })
            imageDataUrls.forEach { url ->
                add(buildJsonObject {
                    put("type", JsonPrimitive("image_url"))
                    put(
                        "image_url",
                        buildJsonObject {
                            put("url", JsonPrimitive(url))
                            put("detail", JsonPrimitive("high"))
                        },
                    )
                })
            }
        }
        val gpt5 = shape.model.startsWith("gpt-5")
        val body = buildJsonObject {
            put("model", JsonPrimitive(shape.model))
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(systemPrompt))
                    })
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", userContent)
                    })
                },
            )
            if (shape.jsonMode) {
                put("response_format", buildJsonObject { put("type", JsonPrimitive("json_object")) })
            }
            if (gpt5) {
                put("max_completion_tokens", JsonPrimitive(2500))
                if (shape.includeReasoning) put("reasoning_effort", JsonPrimitive("low"))
            } else {
                put("temperature", JsonPrimitive(0.4))
                put("max_tokens", JsonPrimitive(2500))
            }
        }
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OpenAiException(httpError(response.code, raw), retryable = response.code == 429)
            }
            val root = json.parseToJsonElement(raw) as? JsonObject
                ?: throw OpenAiException("Ungültige OpenAI-Antwort")
            val content = flattenContent(root)
            if (content.isBlank()) throw OpenAiException("Leere Modellantwort")
            return content
        }
    }

    private fun flattenContent(root: JsonObject): String {
        val choices = root["choices"] as? JsonArray ?: return ""
        val message = (choices.firstOrNull() as? JsonObject)?.get("message") as? JsonObject ?: return ""
        return flatten(message["content"])
    }

    private fun flatten(element: JsonElement?): String = when (element) {
        null -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        is JsonArray -> element.joinToString("") { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull.orEmpty()
                is JsonObject -> item["text"]?.jsonPrimitive?.contentOrNull
                    ?: item["content"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                else -> ""
            }
        }
        is JsonObject -> element["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        else -> ""
    }

    private fun httpError(code: Int, raw: String): String {
        val snippet = JsonExtractor.extractObject(raw).take(280)
        return "OpenAI HTTP $code: $snippet"
    }

    private data class RequestShape(
        val model: String,
        val jsonMode: Boolean,
        val includeReasoning: Boolean,
    )

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}
