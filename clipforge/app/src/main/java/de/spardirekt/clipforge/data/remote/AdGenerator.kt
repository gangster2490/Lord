package de.spardirekt.clipforge.data.remote

import de.spardirekt.clipforge.data.model.AdPackage
import de.spardirekt.clipforge.data.model.EncodedImage
import de.spardirekt.clipforge.data.model.GenerateBrief
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class GenerateException(message: String) : Exception(message)

interface AdGenerator {
    suspend fun generate(
        apiKey: String,
        images: List<EncodedImage>,
        brief: GenerateBrief,
    ): AdPackage
}

fun isDemoKey(apiKey: String): Boolean =
    apiKey.trim().startsWith("sk-demo", ignoreCase = true)

fun adGeneratorFor(apiKey: String, live: AdGenerator = OpenAiAdGenerator()): AdGenerator =
    if (isDemoKey(apiKey)) DemoAdGenerator else live

object JsonExtractor {
    fun extractObject(raw: String): String {
        val stripped = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw GenerateException("Модель не вернула JSON.")
        }
        return stripped.substring(start, end + 1)
    }
}

fun parseAdPackage(raw: String, json: Json = defaultJson()): AdPackage {
    val payload = JsonExtractor.extractObject(raw)
    return try {
        json.decodeFromString(AdPackage.serializer(), payload)
    } catch (e: Exception) {
        throw GenerateException("Некорректный JSON пакета: ${e.message ?: raw.take(180)}")
    }
}

fun parseHttpError(body: String, code: Int, json: Json = defaultJson()): String {
    val parsed = runCatching { json.decodeFromString(JsonObject.serializer(), body) }.getOrNull()
    val message = parsed
        ?.get("error")
        ?.let { err ->
            if (err is JsonObject) err["message"]?.jsonPrimitive?.content else err.jsonPrimitive.content
        }
    return "Ошибка API $code: ${message ?: body.take(220)}"
}

fun defaultJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}
