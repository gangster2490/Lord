package de.spardirekt.recipeveo.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OpenAiClient(private val apiKey: String) {

    suspend fun chat(systemPrompt: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            val body = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("max_tokens", 1800)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                })
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            val raw = if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } else {
                val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                throw RuntimeException("OpenAI HTTP $code: $err")
            }
            conn.disconnect()

            JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
}
