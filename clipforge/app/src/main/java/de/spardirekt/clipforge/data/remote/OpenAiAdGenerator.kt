package de.spardirekt.clipforge.data.remote

import de.spardirekt.clipforge.data.model.AdPackage
import de.spardirekt.clipforge.data.model.EncodedImage
import de.spardirekt.clipforge.data.model.GenerateBrief
import de.spardirekt.clipforge.data.model.PackageGuard
import de.spardirekt.clipforge.data.model.guarded
import de.spardirekt.clipforge.data.prompt.AdSystemPrompt
import de.spardirekt.clipforge.data.prompt.asUserMessage
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiAdGenerator(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
    private val model: String = DEFAULT_MODEL,
) : AdGenerator {

    override suspend fun generate(
        apiKey: String,
        images: List<EncodedImage>,
        brief: GenerateBrief,
    ): AdPackage {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            throw GenerateException("Добавьте OpenAI ключ в Настройках.")
        }

        var lastError: GenerateException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val content = executeChat(key, images, brief)
                if (content.isBlank()) {
                    throw GenerateException("Пустой ответ модели.")
                }
                return parseAdPackage(content, json).guarded(brief)
            } catch (e: GenerateException) {
                lastError = e
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS - 1) throw e
                delay(1200L * (attempt + 1))
            }
        }
        throw lastError ?: GenerateException("OpenAI недоступен.")
    }

    private fun executeChat(
        key: String,
        images: List<EncodedImage>,
        brief: GenerateBrief,
    ): String {

        val userContent = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(brief.asUserMessage()))
                },
            )
            images.forEach { image ->
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("image_url"))
                        put(
                            "image_url",
                            buildJsonObject {
                                put("url", JsonPrimitive(image.dataUrl()))
                                put("detail", JsonPrimitive("high"))
                            },
                        )
                    },
                )
            }
        }

        val payload = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("temperature", JsonPrimitive(0.6))
            put("max_tokens", JsonPrimitive(4000))
            put(
                "response_format",
                buildJsonObject { put("type", JsonPrimitive("json_object")) },
            )
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("system"))
                            put("content", JsonPrimitive(AdSystemPrompt.VALUE))
                        },
                    )
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
            .url(CHAT_URL)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val body = try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw GenerateException(parseHttpError(text, response.code, json))
                }
                text
            }
        } catch (e: GenerateException) {
            throw e
        } catch (e: IOException) {
            throw GenerateException("OpenAI недоступен. ${e.message ?: ""}".trim())
        }

        return extractMessageContent(body)
    }

    private fun isRetryable(error: GenerateException): Boolean {
        val message = error.message.orEmpty()
        if (message.contains("недоступен", ignoreCase = true)) return true
        val code = Regex("""Ошибка API (\d+)""").find(message)?.groupValues?.get(1)?.toIntOrNull()
        return code != null && PackageGuard.isRetryableHttp(code)
    }

    fun testConnection(apiKey: String): String {
        val key = apiKey.trim()
        if (isDemoKey(key)) return "Демо-режим готов — генерация идёт локально."
        if (key.isEmpty()) throw GenerateException("Вставьте ключ OpenAI.")
        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw GenerateException(parseHttpError(text, response.code, json))
                }
                "Соединение успешно"
            }
        } catch (e: GenerateException) {
            throw e
        } catch (e: IOException) {
            throw GenerateException("Сеть недоступна. ${e.message ?: ""}".trim())
        }
    }

    private fun extractMessageContent(body: String): String {
        val root = runCatching { json.decodeFromString(JsonObject.serializer(), body) }.getOrNull()
            ?: throw GenerateException("Некорректный ответ OpenAI: ${body.take(200)}")
        val error = root["error"]
        if (error is JsonObject) {
            throw GenerateException(error["message"]?.jsonPrimitive?.contentOrNull ?: "Ошибка OpenAI")
        }
        val choices = root["choices"] as? JsonArray
            ?: throw GenerateException("В ответе нет choices.")
        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        return message?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o"
        private const val MAX_ATTEMPTS = 3
        private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(210, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
