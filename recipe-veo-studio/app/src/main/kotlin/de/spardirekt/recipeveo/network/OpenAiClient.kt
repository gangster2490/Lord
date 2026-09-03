package de.spardirekt.recipeveo.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class OpenAiClient(private val apiKey: String) {

    suspend fun chat(systemPrompt: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 90_000
            }

            val body = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("temperature", 0.7)
                put("max_tokens", 4096)
                put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                })
            }.toString()

            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                if (code !in 200..299) throw OpenAiException(humanMessage(code, raw))
                val content = JSONObject(raw)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content")
                if (content.isBlank()) throw OpenAiException("Пустой ответ модели. Нажмите «Создать» ещё раз.")
                content
            } catch (e: OpenAiException) {
                throw e
            } catch (_: SocketTimeoutException) {
                throw OpenAiException("OpenAI не ответил вовремя. Проверьте сеть и попробуйте снова.")
            } catch (_: UnknownHostException) {
                throw OpenAiException("Нет сети. Подключитесь к интернету и попробуйте снова.")
            } finally {
                conn.disconnect()
            }
        }

    private fun humanMessage(code: Int, raw: String): String {
        val api = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        val lower = "$api $raw".lowercase()
        return when {
            code == 401 || lower.contains("invalid_api_key") || lower.contains("incorrect api key") ->
                "Неверный ключ OpenAI. Откройте настройки и вставьте рабочий ключ."
            code == 429 || lower.contains("rate_limit") ->
                "Слишком много запросов. Подождите минуту."
            lower.contains("insufficient_quota") || lower.contains("exceeded your current quota") ->
                "На ключе закончилась квота OpenAI."
            code == 400 -> "Модель отклонила запрос. Попробуйте другое название блюда."
            else -> "OpenAI ошибка $code. Попробуйте ещё раз."
        }
    }
}

class OpenAiException(message: String) : RuntimeException(message)
