package de.spardirekt.tiktokshop.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

class ClaudeApiClient(
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun generate(
        proxyUrl: String,
        apiKey: String,
        slots: List<ImageSlot>,
        style: String,
        tone: String,
    ): GeneratedContent {
        val key = apiKey.trim()
        if (key.isEmpty()) error("Bitte Anthropic API Key eingeben.")
        val products = slots.filter { it.image != null && it.kind == ImageKind.PRODUCT }
        val description = slots.firstOrNull { it.image != null && it.kind == ImageKind.DESCRIPTION }
        if (products.isEmpty()) error("Bitte zuerst ein Produktbild hochladen (Bild 1).")

        val content = buildJsonArray {
            products.forEach { slot ->
                val img = slot.image!!
                add(imagePart(img.mime, img.base64))
            }
            description?.image?.let { img ->
                add(imagePart(img.mime, img.base64))
            }
            add(
                buildJsonObject {
                    put("type", "text")
                    put(
                        "text",
                        ResultFormatter.buildUserMessage(
                            productCount = products.size,
                            hasDescription = description != null,
                            style = style,
                            tone = tone,
                        ),
                    )
                },
            )
        }

        val payload = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 4000)
            put("system", SystemPrompt.CREATOR)
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

        val base = proxyUrl.trim().ifBlank { DEFAULT_PROXY }.trimEnd('/')
        val request = Request.Builder()
            .url("$base/api/generate")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key-fwd", key)
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException(
                "Proxy nicht erreichbar ($base). Starte: cd proxy && node server.js",
                e,
            )
        }

        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            val parsed = try {
                json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                error("Ungültige Proxy-Antwort (${resp.code}): ${body.take(200)}")
            }
            if (!resp.isSuccessful) {
                val message = parsed["error"]
                    ?.let { err ->
                        when (err) {
                            is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
                                ?: err.toString()
                            else -> err.toString()
                        }
                    }
                    ?: parsed.toString()
                error("API-Fehler ${resp.code}: $message")
            }
            val raw = parsed["content"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                .orEmpty()
            return ResultFormatter.parseGeneratedContent(raw)
        }
    }

    private fun imagePart(mime: String, data: String): JsonObject = buildJsonObject {
        put("type", "image")
        put(
            "source",
            buildJsonObject {
                put("type", "base64")
                put("media_type", mime)
                put("data", data)
            },
        )
    }

    companion object {
        const val DEFAULT_PROXY = "http://10.0.2.2:3001"
        const val MODEL = "claude-opus-4-5"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(210, TimeUnit.SECONDS)
            .build()
    }
}
