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
        Regex("купи(те)? сейчас"),
        Regex("скидк"),
        Regex("изделие обладает"),
    )
    private val weakDe = listOf(
        Regex("liegt praktisch"),
        Regex("ist praktisch seitlich"),
        Regex("schau mal (dieses|das) produkt", RegexOption.IGNORE_CASE),
        Regex("hier ist das produkt", RegexOption.IGNORE_CASE),
        Regex("dieses produkt ist", RegexOption.IGNORE_CASE),
        Regex("empfehlen wir (dieses|das) produkt", RegexOption.IGNORE_CASE),
        Regex("jetzt kaufen", RegexOption.IGNORE_CASE),
        Regex("das produkt verfügt", RegexOption.IGNORE_CASE),
    )
    private val weakEn = listOf(
        Regex("conveniently (placed|located)", RegexOption.IGNORE_CASE),
        Regex("check (this|it) out", RegexOption.IGNORE_CASE),
        Regex("this product is", RegexOption.IGNORE_CASE),
        Regex("buy now", RegexOption.IGNORE_CASE),
        Regex("the product features", RegexOption.IGNORE_CASE),
    )
    private val unsupported = listOf(
        Regex("\\b(best|perfect|always|never|guaranteed|100%|revolutionary)\\b", RegexOption.IGNORE_CASE),
        Regex("bpa", RegexOption.IGNORE_CASE),
        Regex("сохраня(ет|ют)?\\s+влаг", RegexOption.IGNORE_CASE),
        Regex("feuchtigkeit", RegexOption.IGNORE_CASE),
        Regex("anti[- ]?scratch", RegexOption.IGNORE_CASE),
    )
    private val warmRu = listOf(
        "люблю", "приятно", "уютн", "просто и удобно", "для кухни", "домашн", "по-домашнему",
        "роднее", "спокойн", "живой", "на кухне", "на плите", "дома", "теплее",
    )
    private val warmDe = listOf(
        "mag's", "mag es", "zu hause", "zuhause", "gemütlich", "einfach", "küche", "anfühlt",
    )

    data class HookScore(
        val naturalness: Double,
        val warmth: Double,
        val purchaseAppeal: Double,
        val claimSafety: Double,
        val relevance: Double,
    ) {
        val total: Double
            get() = (naturalness + warmth + purchaseAppeal + claimSafety + relevance) / 5.0
    }

    fun generate(analysis: JSONObject?, language: String): String {
        val ranked = candidates(analysis, language)
            .map { it to score(it, language, analysis) }
            .sortedByDescending { it.second.total }
        val best = ranked.firstOrNull { !isWeak(it.first, language, analysis) }?.first
        return best ?: generateFallback(analysis, language)
    }

    fun candidates(analysis: JSONObject?, language: String): List<String> {
        val russian = language.equals("РУССКИЙ", true)
        val kitchen = isKitchen(analysis)
        val pan = ProductIdentity.looksLikeCookwarePan(null, analysis)
        return if (russian) {
            when {
                pan -> listOf(
                    "Вот за такую посуду я и люблю домашнюю кухню.",
                    "Люблю, когда на плите всё выглядит просто и по-домашнему.",
                    "С такой вещью дома сразу как-то спокойнее.",
                )
                kitchen -> listOf(
                    "Люблю, когда домашняя кухня остаётся простой и живой.",
                    "Приятно, когда обычная кухонная вещь сразу вписывается в дом.",
                    "Вот такие мелочи делают кухню чуть роднее.",
                )
                else -> listOf(
                    "Приятно, когда дома всё просто и по-своему уютно.",
                    "Люблю такие спокойные домашние вещи.",
                    "С такой штукой дома сразу как-то теплее.",
                )
            }
        } else {
            when {
                pan -> listOf(
                    "Ich mag's, wenn so eine Pfanne die Küche nach Zuhause anfühlen lässt.",
                    "Solche Sachen haben wir gern einfach zu Hause am Herd.",
                    "Mit so was wird's in der Küche gleich gemütlicher.",
                )
                kitchen -> listOf(
                    "Ich mag's, wenn die Küche sich nach Zuhause anfühlt.",
                    "Solche Sachen haben wir gern einfach zu Hause.",
                    "Mit so was wird's in der Küche gleich gemütlicher.",
                )
                else -> listOf(
                    "So was hat man gern zu Hause.",
                    "Ich mag's, wenn daheim alles einfach bleibt.",
                    "Solche Dinge machen das Zuhause irgendwie wärmer.",
                )
            }
        }
    }

    fun isWeak(hook: String, language: String, analysis: JSONObject? = null): Boolean {
        val text = hook.trim()
        if (text.isBlank()) return true
        if (text.length > 90) return true
        if (text.split(Regex("\\s+")).size > 14) return true
        if (unsupported.any { it.containsMatchIn(text) }) return true
        if (EvidenceModel.containsUnverifiedClaim(text)) return true
        val weak = weakEn + if (language.equals("РУССКИЙ", true)) weakRu else weakDe
        if (weak.any { it.containsMatchIn(text) }) return true
        if (isUnrelated(text, analysis)) return true
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
        return if (isWeak(hook, language, analysis)) generate(analysis, language) else hook.trim()
    }

    fun score(hook: String, language: String, analysis: JSONObject? = null): HookScore {
        if (isWeak(hook, language, analysis)) {
            return HookScore(0.2, 0.2, 0.2, 0.2, 0.2)
        }
        val lower = hook.lowercase()
        val naturalness = when {
            lower.contains("изделие") || lower.contains("produkt verfügt") -> 0.3
            hook.length in 24..80 && hook.split(Regex("\\s+")).size in 5..12 -> 0.95
            else -> 0.7
        }
        val warmth = if (isWarm(hook, language)) 0.95 else 0.55
        val purchaseAppeal = when {
            lower.contains("купи") || lower.contains("kaufen") -> 0.2
            listOf("люблю", "приятно", "уют", "gern", "mag", "gemütlich").any { lower.contains(it) } -> 0.9
            else -> 0.6
        }
        val claimSafety = if (EvidenceModel.containsUnverifiedClaim(hook) || unsupported.any { it.containsMatchIn(hook) }) 0.1 else 0.95
        val relevance = relevanceScore(hook, analysis)
        return HookScore(naturalness, warmth, purchaseAppeal, claimSafety, relevance)
    }

    fun qualityScore(hook: String, language: String): Double = score(hook, language, null).total

    fun speechBlock(hook: String, language: String): String {
        val langLine = if (language.equals("РУССКИЙ", true)) {
            "The person speaks naturally in casual home Russian, like chatting in their own kitchen, not presenting a product."
        } else {
            "The person speaks naturally in casual home German, like chatting in their own kitchen, not presenting a product."
        }
        return """
SPEECH:
$langLine
"$hook"
$SPEECH_END
""".trimIndent()
    }

    private const val SPEECH_END = "The spoken line must finish before the 8.0-second endpoint."

    private fun generateFallback(analysis: JSONObject?, language: String): String {
        val russian = language.equals("РУССКИЙ", true)
        return when {
            isKitchen(analysis) && russian -> "Люблю, когда домашняя кухня остаётся простой и живой."
            isKitchen(analysis) && !russian -> "Ich mag's, wenn die Küche sich nach Zuhause anfühlt."
            russian -> "Приятно, когда дома всё просто и по-своему уютно."
            else -> "So was hat man gern zu Hause."
        }
    }

    fun isKitchen(analysis: JSONObject?): Boolean {
        val use = analysis?.optString("observed_use_case").orEmpty().lowercase()
        val category = analysis?.optString("product_category").orEmpty().lowercase()
        val blob = "$use $category ${analysis?.toString().orEmpty().lowercase()}"
        return category.contains("kitchen") ||
            category.contains("кухн") ||
            use.contains("kitchen") ||
            use.contains("кухн") ||
            use.contains("microwave") ||
            use.contains("микроволн") ||
            use.contains("cover") ||
            use.contains("крыш") ||
            blob.contains("pan") ||
            blob.contains("сковород") ||
            blob.contains("кастрюл")
    }

    private fun isUnrelated(hook: String, analysis: JSONObject?): Boolean {
        if (analysis == null) return false
        val lower = hook.lowercase()
        if (!isKitchen(analysis)) return false
        val offKitchen = listOf("ванн", "bathroom", "garage", "auto ", "машин", "office", "офис")
        val onKitchen = listOf("кух", "дом", "home", "hause", "küche", "плит", "herd", "посуд")
        return offKitchen.any { lower.contains(it) } && onKitchen.none { lower.contains(it) }
    }

    private fun relevanceScore(hook: String, analysis: JSONObject?): Double {
        val lower = hook.lowercase()
        if (analysis == null) return 0.7
        return when {
            isKitchen(analysis) && (lower.contains("кух") || lower.contains("дом") || lower.contains("küche") || lower.contains("hause") || lower.contains("herd") || lower.contains("плит")) -> 0.95
            ProductIdentity.looksLikeCookwarePan(null, analysis) && (lower.contains("посуд") || lower.contains("pfanne") || lower.contains("herd") || lower.contains("плит")) -> 0.95
            else -> 0.65
        }
    }

    private fun warmEn(): List<String> = listOf("love having", "nice to have at home", "cozy", "home")
}
