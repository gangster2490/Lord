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
        Regex("выдерживает любой"),
        Regex("гарантированн"),
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
        Regex("hält jedes gewicht", RegexOption.IGNORE_CASE),
        Regex("garantierter komfort", RegexOption.IGNORE_CASE),
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
    private val genericAnyProductRu = listOf(
        "приятно, когда дома всё просто",
        "люблю такие спокойные домашние вещи",
        "с такой штукой дома сразу как-то теплее",
    )
    private val genericAnyProductDe = listOf(
        "so was hat man gern zu hause",
        "ich mag's, wenn daheim alles einfach bleibt",
        "solche dinge machen das zuhause irgendwie wärmer",
    )

    data class HookScore(
        val attention: Double,
        val naturalness: Double,
        val relevance: Double,
        val purchaseAppeal: Double,
        val claimSafety: Double,
        val emotionalFit: Double,
        val lengthFit: Double,
    ) {
        val total: Double
            get() = (attention + naturalness + relevance + purchaseAppeal + claimSafety + emotionalFit + lengthFit) / 7.0
        val warmth: Double get() = emotionalFit
    }

    fun generate(
        analysis: JSONObject?,
        language: String,
        fingerprint: JSONObject? = null,
        plan: CreativeStrategyEngine.Plan? = null,
    ): String {
        val resolved = plan ?: CreativeStrategyEngine.plan(analysis, fingerprint)
        val ranked = candidates(analysis, language, fingerprint, resolved)
            .map { it to score(it, language, analysis, resolved, fingerprint) }
            .sortedByDescending { it.second.total }
        val best = ranked.firstOrNull { !isWeak(it.first, language, analysis, resolved, fingerprint) }?.first
        return best ?: generateFallback(analysis, language, resolved, fingerprint)
    }

    fun candidates(
        analysis: JSONObject?,
        language: String,
        fingerprint: JSONObject? = null,
        plan: CreativeStrategyEngine.Plan? = null,
    ): List<String> {
        val resolved = plan ?: CreativeStrategyEngine.plan(analysis, fingerprint)
        val russian = language.equals("РУССКИЙ", true)
        return safeHooks(resolved, russian, analysis, fingerprint)
    }

    fun isWeak(
        hook: String,
        language: String,
        analysis: JSONObject? = null,
        plan: CreativeStrategyEngine.Plan? = null,
        fingerprint: JSONObject? = null,
    ): Boolean {
        val text = hook.trim()
        if (text.isBlank()) return true
        if (text.length > 90) return true
        if (text.split(Regex("\\s+")).size > 14) return true
        if (unsupported.any { it.containsMatchIn(text) }) return true
        if (EvidenceModel.containsUnverifiedClaim(text)) return true
        val weak = weakEn + if (language.equals("РУССКИЙ", true)) weakRu else weakDe
        if (weak.any { it.containsMatchIn(text) }) return true
        val resolved = plan ?: CreativeStrategyEngine.plan(analysis, fingerprint)
        if (CrossProductGuard.containsLeak(text, fingerprint, analysis, resolved)) return true
        if (isGenericAnyProduct(text) && resolved.primary != CreativeStrategyEngine.Motivation.HOME_COZY) return true
        if (isUnrelated(text, analysis, resolved)) return true
        if (isFeatureOnly(text)) return true
        if (matchesPlan(text, resolved)) return false
        if (isWarm(text, language) && resolved.kitchenDefault) return false
        val hasHuman = listOf("?", "люблю", "приятно", "уют", "дома", "кухн", "рыбал", "берег", "удобн", "сидеть", "отдых", "mag", "gemütlich", "hause", "einfach", "sitzen", "angeln", "ufer", "wasser", "putzen", "aufwärm", "draußen")
            .any { text.lowercase().contains(it) }
        return !hasHuman
    }

    fun isWarm(hook: String, language: String): Boolean {
        val lower = hook.lowercase()
        val tokens = if (language.equals("РУССКИЙ", true)) warmRu else warmDe + warmEn()
        return tokens.any { lower.contains(it) }
    }

    fun ensureStrong(
        hook: String,
        analysis: JSONObject?,
        language: String,
        fingerprint: JSONObject? = null,
        plan: CreativeStrategyEngine.Plan? = null,
    ): String {
        val resolved = plan ?: CreativeStrategyEngine.plan(analysis, fingerprint)
        return if (isWeak(hook, language, analysis, resolved, fingerprint)) {
            generate(analysis, language, fingerprint, resolved)
        } else {
            hook.trim()
        }
    }

    fun score(
        hook: String,
        language: String,
        analysis: JSONObject? = null,
        plan: CreativeStrategyEngine.Plan? = null,
        fingerprint: JSONObject? = null,
    ): HookScore {
        val resolved = plan ?: CreativeStrategyEngine.plan(analysis, fingerprint)
        if (isWeak(hook, language, analysis, resolved, fingerprint)) {
            return HookScore(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2)
        }
        val lower = hook.lowercase()
        val words = hook.split(Regex("\\s+")).size
        val attention = when {
            hook.contains("?") -> 0.96
            listOf("вот так", "надоел", "когда всё", "смотри", "отдых совсем", "keine lust", "so sitzt", "draußen").any { lower.contains(it) } -> 0.92
            else -> 0.7
        }
        val naturalness = when {
            lower.contains("изделие") || lower.contains("produkt verfügt") || lower.contains("данный товар") -> 0.25
            hook.length in 24..80 && words in 5..12 -> 0.95
            else -> 0.72
        }
        val purchaseAppeal = when {
            lower.contains("купи") || lower.contains("kaufen") -> 0.15
            listOf("удобн", "приятн", "другое дело", "отдых", "anders", "ruhiger", "weniger", "под рукой", "komfort").any { lower.contains(it) } -> 0.92
            isWarm(hook, language) -> 0.88
            else -> 0.7
        }
        val claimSafety = if (EvidenceModel.containsUnverifiedClaim(hook) || unsupported.any { it.containsMatchIn(hook) }) 0.1 else 0.95
        val emotionalFit = if (matchesPlan(hook, resolved)) 0.95 else if (resolved.kitchenDefault && isWarm(hook, language)) 0.8 else 0.45
        val lengthFit = if (hook.length in 28..86 && words in 6..13) 0.95 else 0.7
        return HookScore(attention, naturalness, relevanceScore(hook, analysis, resolved), purchaseAppeal, claimSafety, emotionalFit, lengthFit)
    }

    fun qualityScore(hook: String, language: String, analysis: JSONObject? = null): Double =
        score(hook, language, analysis).total

    fun speechBlock(hook: String, language: String, plan: CreativeStrategyEngine.Plan? = null): String {
        val resolved = plan ?: CreativeStrategyEngine.plan(null, null)
        val langLine = speechIntro(language, resolved)
        return """
SPEECH:
$langLine
"$hook"
$SPEECH_END
""".trimIndent()
    }

    fun speechIntro(language: String, plan: CreativeStrategyEngine.Plan): String {
        val tongue = if (language.equals("РУССКИЙ", true)) "Russian" else "German"
        return "The person speaks naturally in casual $tongue, like chatting ${plan.speechContext}, not presenting a product."
    }

    private const val SPEECH_END = "The spoken line must finish before the 8.0-second endpoint."

    private fun hooksFor(
        plan: CreativeStrategyEngine.Plan,
        russian: Boolean,
        analysis: JSONObject?,
        fingerprint: JSONObject?,
    ): List<String> {
        val type = plan.hookType
        val fishingSpot = plan.settingType == CreativeStrategyEngine.SettingType.FISHING_SPOT
        val microwaveEvidence = ProductIdentity.looksLikeMicrowaveCover(fingerprint) ||
            listOf("microwave", "микроволн", "cover food").any {
                analysis?.toString().orEmpty().lowercase().contains(it)
            }
        val cookware = ProductIdentity.looksLikeCookwarePan(fingerprint, analysis) &&
            plan.settingType == CreativeStrategyEngine.SettingType.HOME_KITCHEN
        if (russian) {
            return when {
                fishingSpot -> listOf(
                    "Вот так на рыбалке сидеть уже совсем другое дело.",
                    "Когда на рыбалке всё удобно — и отдых совсем другой.",
                    "На берегу так сидеть уже намного спокойнее.",
                )
                type == CreativeStrategyEngine.HookType.PROBLEM && microwaveEvidence && plan.kitchenDefault -> listOf(
                    "Надоело после разогрева отмывать микроволновку?",
                    "После разогрева столько возни с этой микроволновкой.",
                    "Вот из-за таких мелочей на кухне потом меньше уборки.",
                )
                type == CreativeStrategyEngine.HookType.PROBLEM || type == CreativeStrategyEngine.HookType.DAILY_FRUSTRATION -> listOf(
                    "Надоело каждый раз возиться с этим вручную?",
                    "Вот ради такой мелочи потом меньше возни по дому.",
                    "Когда эта штука под рукой, обычный момент идёт спокойнее.",
                )
                cookware || (type == CreativeStrategyEngine.HookType.HOME && plan.kitchenDefault) -> listOf(
                    "Вот за такие вещи я и люблю домашнюю кухню.",
                    "Люблю, когда на плите всё выглядит просто и по-домашнему.",
                    "С такой вещью дома сразу как-то спокойнее.",
                )
                type == CreativeStrategyEngine.HookType.OUTDOOR -> listOf(
                    "Вот так на улице уже совсем другой комфорт.",
                    "На улице так сидеть уже намного спокойнее.",
                    "Когда всё удобно снаружи — отдых сразу другой.",
                )
                type == CreativeStrategyEngine.HookType.COMFORT -> listOf(
                    "Вот так сидеть уже совсем другое дело.",
                    "Так сидеть уже намного спокойнее.",
                    "Когда сидеть удобно, обычный момент сразу мягче.",
                )
                type == CreativeStrategyEngine.HookType.VISUAL || type == CreativeStrategyEngine.HookType.OWNERSHIP -> listOf(
                    "Смотри, как аккуратно это выглядит.",
                    "Смотри, как аккуратно это выглядит вживую.",
                    "Приятно, когда вещь вживую выглядит так спокойно.",
                )
                type == CreativeStrategyEngine.HookType.CONVENIENCE || type == CreativeStrategyEngine.HookType.ORGANIZATION -> listOf(
                    "Когда всё нужное прямо под рукой — намного удобнее.",
                    "Когда мелочи лежат рядом, всё идёт как-то проще.",
                    "Вот эта привычка реально экономит нервы в быту.",
                )
                type == CreativeStrategyEngine.HookType.CURIOSITY || type == CreativeStrategyEngine.HookType.SATISFYING_USE ||
                    type == CreativeStrategyEngine.HookType.SIMPLICITY -> listOf(
                    "Вот эта деталь здесь реально решает многое.",
                    "С этой мелочью обычный момент сразу понятнее.",
                    "Вот ради такой детали это и берут домой.",
                )
                plan.kitchenDefault -> listOf(
                    "Вот за такие вещи я и люблю домашнюю кухню.",
                    "Люблю, когда домашняя кухня остаётся простой и живой.",
                    "Приятно, когда обычная кухонная вещь сразу вписывается в дом.",
                )
                else -> listOf(
                    "Вот эта мелочь в обычном дне реально выручает.",
                    "Когда так под рукой — сразу спокойнее.",
                    "Вот ради такой простоты это и оставляют рядом.",
                )
            }
        }
        return when {
            fishingSpot -> listOf(
                "So sitzt sich's beim Angeln schon ganz anders.",
                "Wenn's beim Angeln bequem liegt, fühlt sich die Pause gleich anders an.",
                "Am Wasser so zu sitzen fühlt sich gleich ruhiger an.",
            )
            type == CreativeStrategyEngine.HookType.PROBLEM && microwaveEvidence && plan.kitchenDefault -> listOf(
                "Keine Lust, die Mikrowelle nach jedem Aufwärmen zu putzen?",
                "Nach dem Aufwärmen die Mikrowelle auszuwischen nervt ganz schön.",
                "Genau so eine Kleinigkeit spart später in der Küche Nerven.",
            )
            type == CreativeStrategyEngine.HookType.PROBLEM || type == CreativeStrategyEngine.HookType.DAILY_FRUSTRATION -> listOf(
                "Keine Lust, das jedes Mal extra nachzubereiten?",
                "Solche Kleinigkeiten nehmen einem später Arbeit ab.",
                "Wenn das in Reichweite ist, bleibt der Ablauf ruhiger.",
            )
            cookware || (type == CreativeStrategyEngine.HookType.HOME && plan.kitchenDefault) -> listOf(
                "Ich mag's, wenn so eine Pfanne die Küche nach Zuhause anfühlen lässt.",
                "Solche Sachen haben wir gern einfach zu Hause am Herd.",
                "Mit so was wird's in der Küche gleich gemütlicher.",
            )
            type == CreativeStrategyEngine.HookType.OUTDOOR -> listOf(
                "Draußen sitzt sich's damit schon ganz anders.",
                "So eine Pause draußen fühlt sich gleich ruhiger an.",
                "Wenn's draußen bequem liegt, bleibt der Moment entspannter.",
            )
            type == CreativeStrategyEngine.HookType.COMFORT -> listOf(
                "So sitzt sich's schon ganz anders.",
                "So zu sitzen fühlt sich gleich ruhiger an.",
                "Wenn's bequem liegt, bleibt der Moment weicher.",
            )
            type == CreativeStrategyEngine.HookType.VISUAL || type == CreativeStrategyEngine.HookType.OWNERSHIP -> listOf(
                "Schau, wie ruhig das in echt wirkt.",
                "So sieht das im normalen Licht gleich lebendiger aus.",
                "Ich mag's, wenn so was schlicht und klar dasteht.",
            )
            type == CreativeStrategyEngine.HookType.CONVENIENCE || type == CreativeStrategyEngine.HookType.ORGANIZATION -> listOf(
                "Wenn alles Nötige in Reichweite ist, wird's gleich einfacher.",
                "Solche Kleinigkeiten machen den Ablauf einfach ruhiger.",
                "Ich mag's, wenn man nicht extra suchen muss.",
            )
            type == CreativeStrategyEngine.HookType.CURIOSITY || type == CreativeStrategyEngine.HookType.SATISFYING_USE ||
                type == CreativeStrategyEngine.HookType.SIMPLICITY -> listOf(
                "Genau diese Kleinigkeit macht hier den Unterschied.",
                "An der Stelle merkt man, warum man das dabeihält.",
                "So eine Detailsache nimmt einem im Alltag Arbeit ab.",
            )
            plan.kitchenDefault -> listOf(
                "Ich mag's, wenn die Küche sich nach Zuhause anfühlt.",
                "Solche Sachen haben wir gern einfach zu Hause.",
                "Mit so was wird's in der Küche gleich gemütlicher.",
            )
            else -> listOf(
                "Solche Kleinigkeiten machen den Alltag einfach ruhiger.",
                "Ich mag's, wenn so was einfach in Reichweite bleibt.",
                "Genau so eine Sache lässt den Moment entspannter.",
            )
        }
    }

    private fun safeHooks(
        plan: CreativeStrategyEngine.Plan,
        russian: Boolean,
        analysis: JSONObject?,
        fingerprint: JSONObject?,
    ): List<String> {
        val raw = hooksFor(plan, russian, analysis, fingerprint)
        val clean = raw.filter { !CrossProductGuard.containsLeak(it, fingerprint, analysis, plan) }
        if (clean.isNotEmpty()) return clean
        val generic = if (russian) {
            listOf(
                "Вот эта мелочь в обычном дне реально выручает.",
                "Когда так под рукой — сразу спокойнее.",
                "Вот ради такой простоты это и оставляют рядом.",
            )
        } else {
            listOf(
                "Solche Kleinigkeiten machen den Alltag einfach ruhiger.",
                "Ich mag's, wenn so was einfach in Reichweite bleibt.",
                "Genau so eine Sache lässt den Moment entspannter.",
            )
        }
        return generic.filter { !CrossProductGuard.containsLeak(it, fingerprint, analysis, plan) }.ifEmpty { generic }
    }

    private fun generateFallback(
        analysis: JSONObject?,
        language: String,
        plan: CreativeStrategyEngine.Plan,
        fingerprint: JSONObject?,
    ): String {
        val russian = language.equals("РУССКИЙ", true)
        return safeHooks(plan, russian, analysis, fingerprint).first()
    }

    fun isKitchen(analysis: JSONObject?): Boolean {
        val context = analysis?.optString("observed_context").orEmpty().lowercase()
        val first = analysis?.optString("first_frame_context").orEmpty().lowercase()
        val use = analysis?.optString("observed_use_case").orEmpty().lowercase()
        val category = analysis?.optString("product_category").orEmpty().lowercase()
        val blob = "$first $context $use $category"
        return blob.contains("kitchen") ||
            blob.contains("küche") ||
            blob.contains("кухн") ||
            blob.contains("cookware") ||
            blob.contains("microwave") ||
            blob.contains("микроволн")
    }

    private fun isUnrelated(hook: String, analysis: JSONObject?, plan: CreativeStrategyEngine.Plan): Boolean {
        val lower = hook.lowercase()
        if (plan.primary == CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY) {
            val outdoor = listOf("рыбал", "берег", "вод", "angel", "ufer", "wasser", "sitzen", "draußen")
            val genericHome = listOf("кухн", "küche", "микроволн", "mikrowelle", "pfanne")
            return genericHome.any { lower.contains(it) } || (outdoor.none { lower.contains(it) } && isGenericAnyProduct(hook))
        }
        if (plan.primary == CreativeStrategyEngine.Motivation.PROBLEM_SOLVER && isKitchen(analysis)) {
            val off = listOf("рыбал", "garage", "ангелн", "angel")
            return off.any { lower.contains(it) }
        }
        if (!isKitchen(analysis)) return false
        val offKitchen = listOf("ванн", "bathroom", "garage", "auto ", "машин", "office", "офис")
        val onKitchen = listOf("кух", "дом", "home", "hause", "küche", "плит", "herd", "посуд", "микроволн", "mikrowelle", "aufwärm")
        return offKitchen.any { lower.contains(it) } && onKitchen.none { lower.contains(it) }
    }

    private fun matchesPlan(hook: String, plan: CreativeStrategyEngine.Plan): Boolean {
        val lower = hook.lowercase()
        return when (plan.hookType) {
            CreativeStrategyEngine.HookType.PROBLEM,
            CreativeStrategyEngine.HookType.DAILY_FRUSTRATION,
            -> listOf("надоел", "возн", "убор", "разогрев", "микроволн", "putzen", "aufwärm", "mikrowelle", "nerven", "возиться").any { lower.contains(it) }
            CreativeStrategyEngine.HookType.COMFORT -> listOf("сидеть", "спокойн", "sitzt", "ruhiger", "anders", "bequem").any { lower.contains(it) }
            CreativeStrategyEngine.HookType.CONVENIENCE,
            CreativeStrategyEngine.HookType.ORGANIZATION,
            -> listOf("под рукой", "удобн", "рядом", "reichweite", "einfacher", "nötig", "suchen", "лежат").any { lower.contains(it) }
            CreativeStrategyEngine.HookType.HOME,
            CreativeStrategyEngine.HookType.OWNERSHIP,
            -> listOf("кух", "дом", "посуд", "küche", "hause", "pfanne", "herd", "gemütlich", "mag", "оставить", "dabeihält").any { lower.contains(it) }
            CreativeStrategyEngine.HookType.VISUAL -> listOf("выгляд", "вживую", "смотри", "sieht", "licht", "wirkt", "schau").any { lower.contains(it) }
            CreativeStrategyEngine.HookType.CURIOSITY,
            CreativeStrategyEngine.HookType.SATISFYING_USE,
            CreativeStrategyEngine.HookType.SIMPLICITY,
            -> listOf("детал", "мелоч", "kleinigkeit", "unterschied", "простот", "detailsache").any { lower.contains(it) }
            CreativeStrategyEngine.HookType.OUTDOOR -> listOf("рыбал", "берег", "улиц", "отдых", "комфорт", "sitzt", "angeln", "wasser", "ufer", "draußen", "pause").any { lower.contains(it) }
        }
    }

    private fun isGenericAnyProduct(hook: String): Boolean {
        val lower = hook.lowercase()
        return (genericAnyProductRu + genericAnyProductDe).any { lower.contains(it) }
    }

    private fun isFeatureOnly(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("расположен") || lower.contains("located") || lower.contains("liegt")) &&
            listOf("?", "люблю", "приятно", "mag", "sitzt", "надоел").none { lower.contains(it) }
    }

    private fun relevanceScore(hook: String, analysis: JSONObject?, plan: CreativeStrategyEngine.Plan): Double {
        val lower = hook.lowercase()
        return when {
            plan.primary == CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY &&
                listOf("рыбал", "берег", "angel", "wasser", "ufer", "sitzt").any { lower.contains(it) } -> 0.97
            plan.hookType == CreativeStrategyEngine.HookType.PROBLEM &&
                listOf("микроволн", "разогрев", "mikrowelle", "aufwärm", "putzen").any { lower.contains(it) } -> 0.97
            ProductIdentity.looksLikeCookwarePan(null, analysis) &&
                (lower.contains("посуд") || lower.contains("pfanne") || lower.contains("herd") || lower.contains("плит") || lower.contains("кух")) -> 0.95
            isKitchen(analysis) && (lower.contains("кух") || lower.contains("дом") || lower.contains("küche") || lower.contains("hause")) -> 0.9
            matchesPlan(hook, plan) -> 0.88
            else -> 0.55
        }
    }

    private fun warmEn(): List<String> = listOf("love having", "nice to have at home", "cozy", "home")
}
