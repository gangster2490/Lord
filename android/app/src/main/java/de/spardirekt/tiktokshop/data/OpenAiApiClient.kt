package de.spardirekt.tiktokshop.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiApiClient(
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun testKey(apiKey: String) {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val message = runCatching {
                    json.parseToJsonElement(body).jsonObject["error"]
                        ?.jsonObject
                        ?.get("message")
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull()
                error(message ?: "Ungültiger Key")
            }
        }
    }

    fun analyzeProduct(
        apiKey: String,
        model: String,
        dataUrls: List<String>,
    ): ProductDna {
        val images = dataUrls.take(4)
        if (images.isEmpty()) error("Bitte erst Fotos hochladen")

        val content = buildJsonArray {
            images.forEach { url ->
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject {
                                put("url", url)
                                put("detail", "high")
                            },
                        )
                    },
                )
            }
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", SystemPrompt.VEO_ANALYSIS)
                },
            )
        }

        val payload = buildJsonObject {
            put("model", model.ifBlank { "gpt-4o" })
            put("max_tokens", 600)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", content)
                        },
                    )
                },
            )
        }

        val root = postJson("https://api.openai.com/v1/chat/completions", apiKey, payload.toString())
        val text = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()
        return ResultFormatter.parseProductDna(text)
    }

    fun generateImage(
        apiKey: String,
        imageModel: String,
        prompt: String,
    ): String {
        val model = imageModel.ifBlank { "dall-e-3" }
        val payload = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("size", if (model == "dall-e-3") "1024x1792" else "512x512")
            put("response_format", "url")
            if (model == "dall-e-3") {
                put("quality", "hd")
                put("style", "natural")
            }
        }
        val root = postJson("https://api.openai.com/v1/images/generations", apiKey, payload.toString())
        return root["data"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: error("Bildgenerierung fehlgeschlagen")
    }

    private fun postJson(url: String, apiKey: String, body: String): kotlinx.serialization.json.JsonObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: error("Ungültige OpenAI-Antwort")
                if (!resp.isSuccessful) {
                    val message = parsed["error"]
                        ?.jsonObject
                        ?.get("message")
                        ?.jsonPrimitive
                        ?.contentOrNull
                    error(message ?: "API Fehler ${resp.code}")
                }
                parsed
            }
        } catch (e: IOException) {
            throw IOException("Netzwerkfehler: ${e.message}", e)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(210, TimeUnit.SECONDS)
            .build()
    }
}
