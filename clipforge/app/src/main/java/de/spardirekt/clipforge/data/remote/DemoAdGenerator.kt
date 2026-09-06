package de.spardirekt.clipforge.data.remote

import de.spardirekt.clipforge.data.model.AdFormula
import de.spardirekt.clipforge.data.model.AdLanguage
import de.spardirekt.clipforge.data.model.AdLength
import de.spardirekt.clipforge.data.model.AdPackage
import de.spardirekt.clipforge.data.model.EncodedImage
import de.spardirekt.clipforge.data.model.GenerateBrief
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.data.model.ProductInsight
import de.spardirekt.clipforge.data.model.StoryboardShot
import de.spardirekt.clipforge.data.model.VisualStyle
import de.spardirekt.clipforge.data.model.canonicalCta
import de.spardirekt.clipforge.data.model.guarded
import de.spardirekt.clipforge.data.prompt.AdSystemPrompt

/**
 * Offline stand-in used when the API key starts with `sk-demo`.
 * Produces a complete, platform-aware package so Studio → Result works without OpenAI.
 */
object DemoAdGenerator : AdGenerator {

    override suspend fun generate(
        apiKey: String,
        images: List<EncodedImage>,
        brief: GenerateBrief,
    ): AdPackage {
        val copy = Copy.forLanguage(brief.language)
        val cta = brief.platform.canonicalCta(brief.language)
        val shots = storyboard(brief.length, brief.formula, copy, cta)
        val platformTag = when (brief.platform) {
            Platform.TIKTOK_SHOP -> "#TikTokShop"
            Platform.REELS -> "#Reels"
            Platform.SHORTS -> "#Shorts"
        }
        val hashtags = (copy.hashtags + platformTag).distinct().take(brief.platform.hashtagCount)
        val veo = buildVeoPrompt(brief, copy, cta, shots)
        return AdPackage(
            product = ProductInsight(
                name = copy.productName,
                category = copy.category,
                visualLock = copy.visualLock,
                sellingAngle = foldWish(copy.angle(brief.formula), brief.wish, brief.language),
                audience = copy.audience,
                keyFeatures = copy.features,
            ),
            hooks = copy.hooks,
            caption = copy.caption(brief.platform, brief.style),
            hashtags = hashtags,
            onScreenTexts = copy.overlays + cta,
            cta = cta,
            storyboard = shots,
            voiceover = copy.voiceover(brief.length, cta),
            music = copy.music(brief.style),
            soundEffects = copy.sfx(brief.length),
            veoPrompt = veo,
            thumbnailPrompt = copy.thumbnail,
            whyItConverts = copy.why(brief.platform, brief.formula),
        ).guarded(brief.copy(photoCount = images.size.coerceAtLeast(brief.photoCount)))
    }

    internal fun foldWish(angle: String, wish: String, language: AdLanguage): String {
        val extra = wish.trim()
        if (extra.isBlank()) return angle
        val label = when (language) {
            AdLanguage.RU -> "Пожелание"
            AdLanguage.DE -> "Wunsch"
            AdLanguage.EN -> "Wish"
        }
        return "$angle. $label: $extra"
    }

    private fun storyboard(
        length: AdLength,
        formula: AdFormula,
        copy: Copy,
        cta: String,
    ): List<StoryboardShot> {
        val beats = when (length) {
            AdLength.EIGHT -> listOf(0.0 to 2.0, 2.0 to 5.0, 5.0 to 7.0, 7.0 to 8.0)
            AdLength.FIFTEEN -> listOf(0.0 to 3.0, 3.0 to 9.0, 9.0 to 13.0, 13.0 to 15.0)
        }
        val actions = copy.actions(formula)
        val overlays = copy.overlays + cta
        val shots = listOf("medium close-up", "tracking medium", "insert close-up", "medium")
        return beats.mapIndexed { i, (start, end) ->
            StoryboardShot(
                startSec = start,
                endSec = end,
                shot = shots[i],
                action = actions[i],
                overlay = overlays.getOrElse(i) { cta },
            )
        }
    }

    private fun buildVeoPrompt(
        brief: GenerateBrief,
        copy: Copy,
        cta: String,
        shots: List<StoryboardShot>,
    ): String = buildString {
        appendLine(AdSystemPrompt.durationHeader(brief.length, brief.platform))
        appendLine()
        appendLine(AdSystemPrompt.HUMAN_INTERACTION_BLOCK)
        appendLine()
        appendLine("FORMAT: Photorealistic vertical 9:16 ${brief.platform.labelEn} product advertisement. Exactly ${brief.length.seconds}.0 seconds. Visual style: ${brief.style.id}.")
        appendLine("LOCKED PRODUCT: ${copy.visualLock}")
        appendLine("FORMULA: ${brief.formula.id}.")
        appendLine()
        shots.forEach { shot ->
            appendLine("${shot.startSec}–${shot.endSec}s ${shot.shot.uppercase()}: ${shot.action} On-screen text: \"${shot.overlay}\".")
        }
        appendLine()
        appendLine("Final CTA overlay (exact): \"$cta\".")
        appendLine("Natural daylight or motivated practical lights. Handheld-stable commercial camera. No marketplace UI. No prices.")
        appendLine()
        append(AdSystemPrompt.NEGATIVE_PROMPT)
    }

    private data class Copy(
        val productName: String,
        val category: String,
        val visualLock: String,
        val audience: String,
        val features: List<String>,
        val hooks: List<String>,
        val overlays: List<String>,
        val hashtags: List<String>,
        val thumbnail: String,
        val angle: (AdFormula) -> String,
        val caption: (Platform, VisualStyle) -> String,
        val voiceover: (AdLength, String) -> String,
        val music: (VisualStyle) -> String,
        val sfx: (AdLength) -> String,
        val why: (Platform, AdFormula) -> String,
        val actions: (AdFormula) -> List<String>,
    ) {
        companion object {
            fun forLanguage(language: AdLanguage): Copy = when (language) {
                AdLanguage.RU -> RU
                AdLanguage.DE -> DE
                AdLanguage.EN -> EN
            }

            private val RU = Copy(
                productName = "Крем с золотой крышкой",
                category = "Уход / косметика",
                visualLock = "Круглая банка, кремовая текстура, матовое стекло, золотая крышка, этикетка как на фото",
                audience = "25–40, уход за кожей вечером",
                features = listOf("Золотая крышка", "Кремовая текстура", "Компактная банка"),
                hooks = listOf(
                    "Стой, это не фильтр",
                    "Крышка золотая — эффект тоже",
                    "Вечером кожа другая",
                    "Одно касание — и видно",
                    "Тот самый ритуал",
                ),
                overlays = listOf("Не скролл", "Живая текстура", "Как на фото"),
                hashtags = listOf("#уход", "#ритуал", "#косметика", "#обзор", "#musthave", "#viral", "#beauty"),
                thumbnail = "9:16 still, person holding the exact jar with gold lid close to camera, warm evening light, no text, no price, product identity locked.",
                angle = { formula ->
                    when (formula) {
                        AdFormula.UGC -> "Честный вечерний ритуал, а не рекламный глянец"
                        AdFormula.PROBLEM_SOLUTION -> "Сухая кожа вечером → один ритуал"
                        AdFormula.UNBOXING -> "Распаковка банки, которая выглядит как на фото"
                        AdFormula.BEFORE_AFTER -> "До ритуала / после одного нанесения — без обещаний чуда"
                        AdFormula.ASMR -> "Крышка, текстура, пальцы — звук и крупный план"
                        AdFormula.HOOK_DEMO_CTA -> "Хук на текстуру → живое нанесение → корзина"
                    }
                },
                caption = { platform, _ ->
                    when (platform) {
                        Platform.TIKTOK_SHOP -> "Золотая крышка и та самая кремовая текстура. Смотри, как ложится — и бери в корзине."
                        Platform.REELS -> "Вечерний ритуал без постановки. Банка как на фото, текстура крупным планом."
                        Platform.SHORTS -> "Кремовая текстура. Крышка как на фото. Описание — ниже."
                    }
                },
                voiceover = { length, cta ->
                    if (length == AdLength.EIGHT) {
                        "0с – Стой.\n2с – Крышка золотая, текстура как на фото.\n5с – Смотри, как ложится.\n7с – $cta."
                    } else {
                        "0с – Стой, это не фильтр.\n3с – Золотая крышка и кремовая текстура остаются как на фото.\n9с – Один вечерний ритуал, живые руки, крупный план.\n13с – $cta."
                    }
                },
                music = { style ->
                    when (style) {
                        VisualStyle.UGC_RAW -> "Lo-fi pop, 96 BPM, тёплый, домашний"
                        VisualStyle.BRIGHT_POP -> "Dance pop, 118 BPM, яркий"
                        VisualStyle.PREMIUM_DARK -> "Deep house, 112 BPM, низкий и уверенный"
                        VisualStyle.MINIMAL -> "Ambient keys, 80 BPM, воздух"
                        VisualStyle.LIFESTYLE -> "Indie pop, 104 BPM, светлый"
                        VisualStyle.CINEMATIC -> "Cinematic pop, 108 BPM, тёплый вечер"
                    }
                },
                sfx = { length ->
                    if (length == AdLength.EIGHT) {
                        "0с – мягкий whoosh\n2с – клик крышки\n5с – крем по коже\n7с – тихий UI-тик"
                    } else {
                        "0с – whoosh\n3с – клик крышки\n9с – крем, пальцы\n13с – мягкий hit на CTA"
                    }
                },
                why = { platform, formula ->
                    "Первый кадр уже с руками и товаром — палец не успевает скроллить. Формула ${formula.labelRu} показывает реальное использование, а не крутящуюся банку. Финал ведёт в нативный CTA ${platform.labelRu}, без цены и фальшивой срочности."
                },
                actions = { formula ->
                    when (formula) {
                        AdFormula.UNBOXING -> listOf(
                            "Человек открывает коробку, банка сразу в кадре как на фото.",
                            "Снимает золотую крышку, показывает текстуру пальцем.",
                            "Наносит на кожу, крупный план банки рядом.",
                            "Смотрит в камеру, банка в руке, CTA.",
                        )
                        AdFormula.ASMR -> listOf(
                            "Макро: пальцы на золотой крышке, товар заполняет кадр.",
                            "Крышка откручивается, звук и кремовая текстура.",
                            "Нанесение, блики на стекле, лицо в расфокусе.",
                            "Средний план, банка как на фото, CTA.",
                        )
                        else -> listOf(
                            "Человек уже наносит крем, банка в кадре, прямой взгляд.",
                            "Живое использование у зеркала, камера вокруг рук и товара.",
                            "Крупный план текстуры и золотой крышки, ничего не меняя.",
                            "Средний план, товар в руке, финальный CTA.",
                        )
                    }
                },
            )

            private val DE = Copy(
                productName = "Creme mit Golddeckel",
                category = "Pflege / Kosmetik",
                visualLock = "Runde Dose, cremige Textur, mattes Glas, Golddeckel, Etikett wie im Foto",
                audience = "25–40, Abendpflege",
                features = listOf("Golddeckel", "Cremige Textur", "Kompakte Dose"),
                hooks = listOf(
                    "Stopp, kein Filter",
                    "Golddeckel, echter Look",
                    "Abends andere Haut",
                    "Eine Berührung reicht",
                    "Das Ritual bleibt",
                ),
                overlays = listOf("Nicht scrollen", "Echte Textur", "Wie im Foto"),
                hashtags = listOf("#pflege", "#ritual", "#kosmetik", "#review", "#musthave", "#viral", "#beauty"),
                thumbnail = "9:16 still, person holding the exact gold-lid jar to camera, warm evening light, no text, product locked.",
                angle = { formula ->
                    when (formula) {
                        AdFormula.UGC -> "Ehrliches Abendritual statt Hochglanz"
                        else -> "Textur-Hook, echte Anwendung, native CTA"
                    }
                },
                caption = { platform, _ ->
                    when (platform) {
                        Platform.TIKTOK_SHOP -> "Golddeckel, cremige Textur wie im Foto. Schau, wie sie liegt — unten im Warenkorb."
                        Platform.REELS -> "Abendritual ohne Posing. Die Dose bleibt, wie du sie siehst."
                        Platform.SHORTS -> "Cremige Textur. Deckel wie im Foto. Details unten."
                    }
                },
                voiceover = { length, cta ->
                    if (length == AdLength.EIGHT) {
                        "0s – Stopp.\n2s – Golddeckel, Textur wie im Foto.\n5s – Sieh, wie sie liegt.\n7s – $cta."
                    } else {
                        "0s – Stopp, das ist kein Filter.\n3s – Golddeckel und cremige Textur bleiben wie im Foto.\n9s – Ein Abendritual, echte Hände, Close-up.\n13s – $cta."
                    }
                },
                music = { _ -> "Cinematic pop, 108 BPM, warmer Abend" },
                sfx = { length ->
                    if (length == AdLength.EIGHT) "0s – Whoosh\n2s – Deckelklick\n5s – Creme\n7s – Soft tick"
                    else "0s – Whoosh\n3s – Deckelklick\n9s – Creme\n13s – CTA hit"
                },
                why = { platform, formula ->
                    "Der erste Frame zeigt schon Hände und Produkt. Formel ${formula.labelRu} beweist echte Nutzung. Der Cut endet auf ${platform.labelEn}-CTA, ohne Preis."
                },
                actions = { _ ->
                    listOf(
                        "Person trägt die Creme schon auf, Dose im Frame, Blick in die Kamera.",
                        "Natürliche Nutzung am Spiegel, Kamera um Hände und Produkt.",
                        "Close-up der Textur und des Golddeckels, nichts verändern.",
                        "Medium shot, Produkt in der Hand, finaler CTA.",
                    )
                },
            )

            private val EN = Copy(
                productName = "Gold-lid cream jar",
                category = "Skincare",
                visualLock = "Round jar, creamy texture, frosted glass, gold lid, label matching the photo",
                audience = "25–40, evening skincare",
                features = listOf("Gold lid", "Creamy texture", "Compact jar"),
                hooks = listOf(
                    "Stop — not a filter",
                    "Gold lid, real texture",
                    "Night skin, different",
                    "One touch, you see it",
                    "Keep the ritual",
                ),
                overlays = listOf("Don't scroll", "Real texture", "As photographed"),
                hashtags = listOf("#skincare", "#ritual", "#beauty", "#review", "#musthave", "#viral", "#glow"),
                thumbnail = "9:16 still, person holding the exact gold-lid jar to camera, warm evening light, no text, product locked.",
                angle = { formula ->
                    when (formula) {
                        AdFormula.UGC -> "Honest evening ritual, not glossy ad-speak"
                        else -> "Texture hook, live application, native CTA"
                    }
                },
                caption = { platform, _ ->
                    when (platform) {
                        Platform.TIKTOK_SHOP -> "Gold lid. Creamy texture exactly as photographed. Watch it melt in — then tap the cart."
                        Platform.REELS -> "Evening ritual, no posing. The jar stays identical to the photo."
                        Platform.SHORTS -> "Creamy texture. Lid as photographed. Shop in the description."
                    }
                },
                voiceover = { length, cta ->
                    if (length == AdLength.EIGHT) {
                        "0s – Stop.\n2s – Gold lid, texture as photographed.\n5s – Watch it melt in.\n7s – $cta."
                    } else {
                        "0s – Stop. This is not a filter.\n3s – Gold lid and creamy texture stay locked to the photo.\n9s – One evening ritual, real hands, close-up.\n13s – $cta."
                    }
                },
                music = { _ -> "Cinematic pop, 108 BPM, warm evening" },
                sfx = { length ->
                    if (length == AdLength.EIGHT) "0s – whoosh\n2s – lid click\n5s – cream on skin\n7s – soft tick"
                    else "0s – whoosh\n3s – lid click\n9s – cream\n13s – CTA hit"
                },
                why = { platform, formula ->
                    "The first frame already has hands on the product so the thumb stops. Formula ${formula.labelRu} proves real use. The ending uses a native ${platform.labelEn} CTA with no price."
                },
                actions = { _ ->
                    listOf(
                        "Person already applying cream, jar in frame, eye contact.",
                        "Natural use at a mirror, camera around hands and product.",
                        "Close-up of texture and gold lid, product unchanged.",
                        "Medium shot, product in hand, final CTA.",
                    )
                },
            )
        }
    }
}
