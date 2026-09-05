package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

object HookEngine {
    private val weakRu = listOf(
        Regex("удобно расположен"),
        Regex("ручка .{0,24}сбоку"),
        Regex("смотрите какой"),
        Regex("просто товар"),
    )
    private val weakDe = listOf(
        Regex("liegt praktisch"),
        Regex("ist praktisch seitlich"),
        Regex("schau mal (dieses|das) produkt", RegexOption.IGNORE_CASE),
        Regex("hier ist das produkt", RegexOption.IGNORE_CASE),
    )
    private val weakEn = listOf(
        Regex("conveniently (placed|located)", RegexOption.IGNORE_CASE),
        Regex("check (this|it) out", RegexOption.IGNORE_CASE),
        Regex("this product is", RegexOption.IGNORE_CASE),
    )
    private val unsupported = listOf(
        Regex("\\b(best|perfect|always|never|guaranteed|100%|revolutionary)\\b", RegexOption.IGNORE_CASE),
        Regex("bpa", RegexOption.IGNORE_CASE),
    )

    fun generate(analysis: JSONObject?, language: String): String {
        val russian = language.equals("РУССКИЙ", true)
        val use = analysis?.optString("observed_use_case").orEmpty().lowercase()
        val category = analysis?.optString("product_category").orEmpty().lowercase()
        val microwave = use.contains("microwave") || use.contains("микроволн") ||
            category.contains("kitchen") && (use.contains("cover") || use.contains("splash") || use.contains("брызг") || use.contains("крыш"))
        val hook = when {
            microwave && russian -> "Надоело отмывать микроволновку после каждого разогрева?"
            microwave && !russian -> "Keine Lust, die Mikrowelle nach jedem Aufwärmen zu putzen?"
            russian -> "Если после использования приходится всё вытирать — смотри."
            else -> "Wenn du danach immer alles abwischen musst, schau mal."
        }
        return ensureStrong(hook, analysis, language)
    }

    fun isWeak(hook: String, language: String): Boolean {
        val text = hook.trim()
        if (text.isBlank()) return true
        if (text.length > 110) return true
        if (text.split(Regex("\\s+")).size > 16) return true
        if (unsupported.any { it.containsMatchIn(text) }) return true
        val weak = weakEn + if (language.equals("РУССКИЙ", true)) weakRu else weakDe
        if (weak.any { it.containsMatchIn(text) }) return true
        val hasFriction = listOf("?", "если", "wenn", "надоел", "lust", "почему", "deshalb", "брызг", "spritz", "вытир", "abwisch")
            .any { text.lowercase().contains(it) }
        return !hasFriction
    }

    fun ensureStrong(hook: String, analysis: JSONObject?, language: String): String {
        return if (isWeak(hook, language)) generateFallback(analysis, language) else hook.trim()
    }

    fun qualityScore(hook: String, language: String): Double {
        if (isWeak(hook, language)) return 0.35
        val lengthBonus = when (hook.trim().length) {
            in 24..90 -> 0.3
            else -> 0.1
        }
        return (0.7 + lengthBonus).coerceAtMost(0.98)
    }

    fun speechBlock(hook: String, language: String): String {
        val langLine = if (language.equals("РУССКИЙ", true)) {
            "The person speaks naturally in Russian."
        } else {
            "The person speaks naturally in German."
        }
        return """
SPEECH:
$langLine
Spoken hook begins around 0.3–0.8 seconds:
"$hook"
The spoken line must finish before the 8.0-second endpoint.
""".trimIndent()
    }

    private fun generateFallback(analysis: JSONObject?, language: String): String {
        val russian = language.equals("РУССКИЙ", true)
        val use = analysis?.optString("observed_use_case").orEmpty().lowercase()
        return when {
            (use.contains("microwave") || use.contains("микроволн")) && russian ->
                "Вот почему микроволновка постоянно в брызгах."
            (use.contains("microwave") || use.contains("микроволн")) && !russian ->
                "Deshalb ist die Mikrowelle nach dem Aufwärmen voller Spritzer."
            russian -> "Если после разогрева приходится всё вытирать — смотри."
            else -> "Keine Lust, nach jedem Aufwärmen alles abzuwischen?"
        }
    }
}
