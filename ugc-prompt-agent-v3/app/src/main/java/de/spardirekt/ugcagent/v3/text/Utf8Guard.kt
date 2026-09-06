package de.spardirekt.ugcagent.v3.text

import org.json.JSONArray
import org.json.JSONObject

object Utf8Guard {
    val SAMPLE_RU = "«Сейчас просто накрою тарелку.»"

    private val broken = Regex("Ã.|Â.|â€.|â€™|âœ.|Ð.|Ñ.|Â«|Â»|Ã—")

    fun looksBroken(text: String): Boolean = broken.containsMatchIn(text)

    fun repair(text: String): String {
        if (text.isEmpty() || !looksBroken(text)) return text
        val whole = decodeLatin1(text)
        if (whole != null && !looksBroken(whole) && whole != text) return whole
        return text.lineSequence().joinToString("\n") { line ->
            if (!looksBroken(line)) line else decodeLatin1(line) ?: line
        }
    }

    private fun decodeLatin1(text: String): String? {
        return try {
            val repaired = String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
            if (repaired.isEmpty() || (looksBroken(repaired) && repaired.length >= text.length)) null else repaired
        } catch (_: Exception) {
            null
        }
    }

    fun decodeUtf8Bytes(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    fun simulateLatin1Mojibake(text: String): String =
        String(text.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)

    fun repairJson(value: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out.put(repair(key), repairValue(value.opt(key)))
        }
        return out
    }

    private fun repairValue(value: Any?): Any? = when (value) {
        is String -> repair(value)
        is JSONObject -> repairJson(value)
        is JSONArray -> {
            val arr = JSONArray()
            for (i in 0 until value.length()) arr.put(repairValue(value.opt(i)))
            arr
        }
        else -> value
    }
}
