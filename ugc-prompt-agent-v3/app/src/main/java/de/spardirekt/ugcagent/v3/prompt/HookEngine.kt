package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

object HookEngine {
    private val weakRu = listOf(
        Regex("удобно расположен"),
        Regex("ручка .{0,24}сбоку"),
        Regex("смотрите какой"),
        Regex("просто товар"),
        Regex("данный товар"),
        Regex("данное изделие"),
        Regex("рекомендуем приобрести"),
    )
    private val weakDe = listOf(
        Regex("liegt praktisch"),
        Regex("ist praktisch seitlich"),
        Regex("schau mal (dieses|das) produkt", RegexOption.IGNORE_CASE),
        Regex("hier ist das produkt", RegexOption.IGNORE_CASE),
        Regex("dieses produkt ist", RegexOption.IGNORE_CASE),
        Regex("empfehlen wir (dieses|das) produkt", RegexOption.IGNORE_CASE),
    )
    private val weakEn = listOf(
        Regex("conveniently (placed|located)", RegexOption.IGNORE_CASE),
        Regex("check (this|it) out", RegexOption.IGNORE_CASE),
        Regex("this product is", RegexOption.IGNORE_CASE),
        Regex("buy now", RegexOption.IGNORE_CASE),
    )
    private val unsupported = listOf(
        Regex("\\b(best|perfect|always|never|guaranteed|100%|revolutionary)\\b", RegexOption.IGNORE_CASE),
        Regex("bpa", RegexOption.IGNORE_CASE),
    )
    private val warmRu = listOf("люблю", "приятно иметь дома", "уютн", "просто и удобно", "для кухни")
    private val warmDe = listOf("mag's", "mag es", "zu hause", "zuhause", "gemütlich", "einfach und")

    fun generate(analysis: JSONObject?, language: String): String {
        val russian = language.equals("РУССКИЙ", true)
        val kitchen = isKitchen(analysis)
        val hook = when {
            kitchen && russian -> "Люблю, когда на кухне всё просто и удобно."
            kitchen && !russian -> "Ich mag's, wenn in der Küche alles einfach und gemütlich bleibt."
            russian -> "Вот такую вещь приятно иметь дома."
            else -> "So was hat man gern zu Hause."
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
        if (isWarm(text, language)) return false
        val hasHuman = listOf("?", "люблю", "приятно", "уют", "дома", "кухн", "mag", "gemütlich", "hause", "einfach")
            .any { text.lowercase().contains(it) }
        return !hasHuman
    }

    fun isWarm(hook: String, language: String): Boolean {
        val lower = hook.lowercase()
        val tokens = if (language.equals("РУССКИЙ", true)) warmRu else warmDe + warmEn()
        return tokens.any { lower.contains(it) }
    }

    fun ensureStrong(hook: String, analysis: JSONObject?, language: String): String {
        return if (isWeak(hook, language)) generateFallback(analysis, language) else hook.trim()
    }

    fun qualityScore(hook: String, language: String): Double {
        if (isWeak(hook, language)) return 0.35
        val warmth = if (isWarm(hook, language)) 0.2 else 0.0
        val lengthBonus = when (hook.trim().length) {
            in 24..90 -> 0.2
            else -> 0.1
        }
        return (0.7 + warmth + lengthBonus).coerceAtMost(0.98)
    }

    fun speechBlock(hook: String, language: String): String {
        val langLine = if (language.equals("РУССКИЙ", true)) {
            "The person speaks naturally in casual home Russian, like chatting in their own kitchen, not presenting a product."
        } else {
            "The person speaks naturally in casual home German, like chatting in their own kitchen, not presenting a product."
        }
        return """
SPEECH:
$langLine
Spoken hook begins around 0.3–0.8 seconds:
"$hook"
$SPEECH_END
""".trimIndent()
    }

    private const val SPEECH_END = "The spoken line must finish before the 8.0-second endpoint."

    private fun generateFallback(analysis: JSONObject?, language: String): String {
        val russian = language.equals("РУССКИЙ", true)
        return when {
            isKitchen(analysis) && russian -> "Для кухни — очень уютная и удобная вещь."
            isKitchen(analysis) && !russian -> "Für die Küche — gemütlich und einfach praktisch."
            russian -> "Вот такую вещь приятно иметь дома."
            else -> "So was hat man gern zu Hause."
        }
    }

    private fun isKitchen(analysis: JSONObject?): Boolean {
        val use = analysis?.optString("observed_use_case").orEmpty().lowercase()
        val category = analysis?.optString("product_category").orEmpty().lowercase()
        return category.contains("kitchen") ||
            category.contains("кухн") ||
            use.contains("kitchen") ||
            use.contains("кухн") ||
            use.contains("microwave") ||
            use.contains("микроволн") ||
            use.contains("cover") ||
            use.contains("крыш")
    }

    private fun warmEn(): List<String> = listOf("love having", "nice to have at home", "cozy")
}
