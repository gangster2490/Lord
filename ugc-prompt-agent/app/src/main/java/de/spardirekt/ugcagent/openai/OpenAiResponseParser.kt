package de.spardirekt.ugcagent.openai

import org.json.JSONArray
import org.json.JSONObject

object OpenAiErrorRules {
    fun isUnsupported(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("unsupported_parameter") ||
            lower.contains("unknown_parameter") ||
            (lower.contains("reasoning_effort") && (lower.contains("unsupported") || lower.contains("unknown") || lower.contains("invalid"))) ||
            (lower.contains("response_format") && (lower.contains("unsupported") || lower.contains("invalid"))) ||
            (lower.contains("\"detail\"") && (lower.contains("invalid") || lower.contains("unsupported")))
    }

    fun isImageTooLarge(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("too large") ||
            lower.contains("context_length") ||
            (lower.contains("image") && lower.contains("limit"))
    }

    fun isRateLimited(code: String, raw: String = ""): Boolean {
        if (code.equals("RATE_LIMIT", ignoreCase = true) || code == "429") return true
        val lower = raw.lowercase()
        return lower.contains("rate_limit") || lower.contains("rate limit") || lower.contains("too many requests")
    }

    fun isMissingModel(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("model") &&
            (lower.contains("not found") || lower.contains("does not exist") || lower.contains("invalid model"))
    }
}

object OpenAiResponseParser {
    fun messageText(raw: String): String {
        val json = JSONObject(raw)
        val message = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: return ""
        return flattenContent(message.opt("content"))
    }

    fun flattenContent(content: Any?): String {
        if (content == null || content === JSONObject.NULL) return ""
        if (content is JSONArray) {
            return buildString {
                for (i in 0 until content.length()) {
                    when (val part = content.opt(i)) {
                        is String -> append(part)
                        is JSONObject -> append(part.optString("text"))
                    }
                }
            }.trim()
        }
        if (content is String) {
            val value = content.trim()
            return if (value.equals("null", ignoreCase = true)) "" else value
        }
        return ""
    }
}
