package de.spardirekt.ugcclean.gen

object JsonExtractor {
    fun extractObject(raw: String): String {
        val text = stripFence(raw.trim())
        return extractBalancedObject(text)
            ?: run {
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                if (start >= 0 && end > start) text.substring(start, end + 1) else text
            }
    }

    fun stripFence(raw: String): String {
        var text = raw.trim()
        if (!text.startsWith("```")) return text
        text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
        val fence = text.lastIndexOf("```")
        if (fence >= 0) text = text.substring(0, fence)
        return text.trim()
    }

    fun extractBalancedObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    fun repairLiteralControlsInStrings(json: String): String {
        val out = StringBuilder(json.length + 64)
        var inString = false
        var escape = false
        for (c in json) {
            if (inString) {
                when {
                    escape -> {
                        out.append(c)
                        escape = false
                    }
                    c == '\\' -> {
                        out.append(c)
                        escape = true
                    }
                    c == '"' -> {
                        out.append(c)
                        inString = false
                    }
                    c == '\n' -> out.append("\\n")
                    c == '\r' -> out.append("\\r")
                    c == '\t' -> out.append("\\t")
                    else -> out.append(c)
                }
            } else {
                if (c == '"') inString = true
                out.append(c)
            }
        }
        return out.toString()
    }
}
