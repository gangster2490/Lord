package de.spardirekt.tiktokshop.data.remote

import de.spardirekt.tiktokshop.data.model.AnthropicResponse
import de.spardirekt.tiktokshop.data.model.EncodedImage
import de.spardirekt.tiktokshop.data.model.GenerateResult
import de.spardirekt.tiktokshop.data.prompt.SystemPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GenerateException(message: String) : Exception(message)

interface ContentGenerator {
    suspend fun generate(
        apiKey: String,
        proxyUrl: String,
        productImages: List<EncodedImage>,
        descriptionImage: EncodedImage?,
        videoStyle: String,
        tone: String,
    ): GenerateResult
}

class ProxyContentGenerator(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
) : ContentGenerator {

    override suspend fun generate(
        apiKey: String,
        proxyUrl: String,
        productImages: List<EncodedImage>,
        descriptionImage: EncodedImage?,
        videoStyle: String,
        tone: String,
    ): GenerateResult {
        val base = proxyUrl.trim().trimEnd('/')
        if (base.isEmpty()) {
            throw GenerateException("Bitte eine Proxy-URL eingeben.")
        }

        val userContent = buildJsonArray {
            productImages.forEach { add(imageBlock(it)) }
            descriptionImage?.let { add(imageBlock(it)) }
            add(
                buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(userText(productImages.size, descriptionImage != null, videoStyle, tone)))
                },
            )
        }

        val payload = buildJsonObject {
            put("model", JsonPrimitive("claude-opus-4-5"))
            put("max_tokens", JsonPrimitive(4000))
            put("system", JsonPrimitive(SystemPrompt.VALUE))
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", userContent)
                        },
                    )
                },
            )
        }

        val request = Request.Builder()
            .url("$base/api/generate")
            .header("Content-Type", "application/json")
            .header("x-api-key-fwd", apiKey.trim())
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val body = try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw GenerateException(parseError(text, response.code))
                }
                text
            }
        } catch (e: GenerateException) {
            throw e
        } catch (e: IOException) {
            throw GenerateException("Proxy nicht erreichbar ($base). ${e.message ?: ""}".trim())
        }

        val anthropic = try {
            json.decodeFromString(AnthropicResponse.serializer(), body)
        } catch (_: Exception) {
            throw GenerateException("Ungültige Proxy-Antwort: ${body.take(200)}")
        }

        anthropic.error?.message?.let { throw GenerateException(it) }

        val raw = anthropic.content.firstOrNull()?.text?.trim().orEmpty()
        if (raw.isEmpty()) {
            throw GenerateException("Leere Antwort von Anthropic.")
        }
        return parseGenerateResult(raw, json)
    }

    private fun imageBlock(image: EncodedImage): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("image"))
        put(
            "source",
            buildJsonObject {
                put("type", JsonPrimitive("base64"))
                put("media_type", JsonPrimitive(image.mime))
                put("data", JsonPrimitive(image.base64))
            },
        )
    }

    private fun userText(
        productCount: Int,
        hasDescription: Boolean,
        videoStyle: String,
        tone: String,
    ): String {
        val parts = mutableListOf("$productCount Produktbild(er) hochgeladen.")
        if (hasDescription) {
            parts += "Zusätzlich ein Beschreibungs-/Spezifikationsbild – OCR anwenden und alle Fakten extrahieren und zusammenführen."
        } else {
            parts += "Kein Beschreibungsbild vorhanden. Setze unbekannte productFacts auf \"Nicht erkennbar\"."
        }
        return "${parts.joinToString(" ")}\n\nVideo-Stil: $videoStyle\nTon: $tone\n\nAlle Videos sind exakt 8 Sekunden lang.\n\nNur JSON zurückgeben."
    }

    private fun parseError(body: String, code: Int): String {
        val parsed = runCatching { json.decodeFromString(AnthropicResponse.serializer(), body) }.getOrNull()
        val message = parsed?.error?.message
            ?: runCatching {
                json.decodeFromString(JsonObject.serializer(), body)["error"]
                    ?.let { if (it is JsonPrimitive) it.content else it.toString() }
            }.getOrNull()
        return "API-Fehler $code: ${message ?: body.take(200)}"
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(210, TimeUnit.SECONDS)
            .build()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

fun parseGenerateResult(raw: String, json: Json = ProxyContentGenerator.defaultJson()): GenerateResult {
    val stripped = raw
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    return try {
        json.decodeFromString(GenerateResult.serializer(), stripped)
    } catch (e: Exception) {
        throw GenerateException("Kein gültiges JSON: ${raw.take(400)}")
    }
}

