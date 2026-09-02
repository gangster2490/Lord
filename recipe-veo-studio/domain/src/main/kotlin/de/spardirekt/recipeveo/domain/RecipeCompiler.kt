package de.spardirekt.recipeveo.domain

object RecipeCompiler {
    val sectionOrder = listOf(
        "FORMAT",
        "REFERENCES",
        "PRODUCT LOCK",
        "SETTING",
        "SHOT SEQUENCE",
        "ON-SCREEN TEXT",
        "VOICEOVER",
        "AUDIO",
        "CRITICAL",
        "NEGATIVE PROMPT",
        "TITLE",
        "HASHTAGS",
    )

    fun compile(recipe: Recipe, clock: AppClock): CompiledPrompt {
        val title = recipe.title.trim().ifBlank { recipe.subject.trim().ifBlank { "Untitled Recipe" } }
        val subject = recipe.subject.trim().ifBlank { title }
        val voiceover = voiceoverLine(recipe.voice, subject, recipe.kind)
        val hashtags = hashtagsFor(title, recipe.kind, recipe.style)
        val lock = lockLines(recipe, subject)
        val setting = recipe.setting.trim().ifBlank { defaultSetting(recipe.style) }
        val beats = filledBeats(recipe, subject)
        val onScreen = recipe.onScreenText.trim().ifBlank { "None." }
        val wish = recipe.wish.trim()

        val body = buildString {
            appendLine("FORMAT")
            appendLine(formatLine(recipe))
            appendLine()
            appendLine("REFERENCES")
            appendLine(referenceLine(recipe, subject, wish))
            appendLine()
            appendLine("PRODUCT LOCK")
            appendLine("Match the described subject exactly. Do not replace or redesign.")
            lock.forEach { appendLine(it) }
            appendLine()
            appendLine("SETTING")
            appendLine(setting)
            appendLine()
            appendLine("SHOT SEQUENCE")
            beats.forEach { beat ->
                appendLine(
                    "${fmt(beat.startSec)}–${fmt(beat.endSec)}s — ${beat.role.name}: ${beat.action.trim()}",
                )
            }
            appendLine()
            appendLine("ON-SCREEN TEXT")
            appendLine(onScreen)
            appendLine()
            appendLine("VOICEOVER")
            appendLine(if (recipe.voice == VoiceLang.OFF) "OFF" else voiceover)
            appendLine()
            appendLine("AUDIO")
            appendLine(audioLine(recipe.style, recipe.voice))
            appendLine()
            appendLine("CRITICAL")
            appendLine("Keep subject identity. Exactly 8.0s. Four blocks only. Photorealistic. No morphing.")
            if (wish.isNotEmpty()) appendLine("Honor wish: $wish")
            appendLine()
            appendLine("NEGATIVE PROMPT")
            appendLine("- no generic replacement subject")
            appendLine("- no redesign / wrong proportions / colors / materials")
            appendLine("- no invented accessories or missing confirmed parts")
            appendLine("- no product morphing")
            appendLine("- no marketplace UI or phone interface")
            appendLine("- no CGI/cartoon look")
            appendLine("- no extra people unless the beat requires hands")
            appendLine()
            appendLine("TITLE")
            appendLine(title)
            appendLine()
            appendLine("HASHTAGS")
            appendLine(hashtags.joinToString(" "))
        }.trim()

        return CompiledPrompt(
            veoPrompt = body,
            voiceover = if (recipe.voice == VoiceLang.OFF) "" else voiceover,
            title = title,
            hashtags = hashtags,
            compiledAt = clock.nowMillis(),
        )
    }

    fun looksComplete(prompt: String): Boolean =
        sectionOrder.all { heading ->
            Regex("^${Regex.escape(heading)}$", RegexOption.MULTILINE).containsMatchIn(prompt)
        } && prompt.contains("Exactly 8.0s")

    fun sharePackage(compiled: CompiledPrompt): String = buildString {
        appendLine(compiled.veoPrompt)
        appendLine()
        appendLine("---")
        if (compiled.voiceover.isNotBlank()) {
            appendLine("VOICEOVER")
            appendLine(compiled.voiceover)
            appendLine()
        }
        appendLine("TITLE")
        appendLine(compiled.title)
        appendLine()
        appendLine("HASHTAGS")
        appendLine(compiled.hashtags.joinToString(" "))
    }.trim()

    private fun formatLine(recipe: Recipe): String {
        val genre = when (recipe.kind) {
            RecipeKind.FOOD, RecipeKind.DRINK -> "food film"
            RecipeKind.PRODUCT -> "TikTok Shop ad"
            RecipeKind.BEAUTY -> "beauty film"
            RecipeKind.LIFESTYLE -> "lifestyle film"
        }
        return "Vertical 9:16. Photorealistic $genre. Exactly 8.0s. Style: ${recipe.style.label()}."
    }

    private fun referenceLine(recipe: Recipe, subject: String, wish: String): String {
        val base = "Recipe brief confirms: $subject. Category ${recipe.kind.label()}."
        return if (wish.isBlank()) base else "$base Wish: $wish"
    }

    private fun lockLines(recipe: Recipe, subject: String): List<String> {
        val custom = recipe.lockNotes.map { it.trim() }.filter { it.isNotEmpty() }
        return if (custom.isNotEmpty()) custom else listOf(subject)
    }

    private fun filledBeats(recipe: Recipe, subject: String): List<ShotBeat> {
        val source = recipe.beats.takeIf { it.size == 4 } ?: defaultBeats()
        val fallbacks = defaultActions(subject, recipe.kind, recipe.style)
        return source.mapIndexed { index, beat ->
            val action = beat.action.trim().ifBlank { fallbacks.getOrElse(index) { subject } }
            beat.copy(action = action)
        }
    }

    private fun defaultActions(subject: String, kind: RecipeKind, style: VisualStyle): List<String> {
        val close = if (style == VisualStyle.MACRO) "extreme close-up" else "tight close-up"
        return when (kind) {
            RecipeKind.FOOD, RecipeKind.DRINK -> listOf(
                "$close catch-light on $subject",
                "full plate / vessel identity of $subject",
                "one honest action: pour, cut, steam or drizzle",
                "hero hold, steam or gloss still readable",
            )
            RecipeKind.PRODUCT, RecipeKind.BEAUTY -> listOf(
                "$close signature detail of $subject",
                "full product silhouette of $subject",
                "one hand demonstrates a single real function",
                "hero packshot, label and proportions locked",
            )
            RecipeKind.LIFESTYLE -> listOf(
                "motion hook around $subject",
                "wide identity of $subject in place",
                "one natural human beat with $subject",
                "hero still, $subject dominant in frame",
            )
        }
    }

    private fun defaultSetting(style: VisualStyle): String = when (style) {
        VisualStyle.STUDIO -> "Uncluttered premium studio, soft key light, dark negative space."
        VisualStyle.MACRO -> "Macro tabletop, shallow depth, specular highlights only on the subject."
        VisualStyle.SATISFYING -> "Clean tabletop, top-down bias, slow viscous motion."
        VisualStyle.LIFESTYLE -> "Lived-in interior, practical window light, no clutter competing with subject."
        VisualStyle.CINEMATIC -> "Motivated daylight, gentle haze, cinematic contrast, stable camera."
        VisualStyle.STREET -> "Real street at golden hour, subject isolated from background traffic."
    }

    private fun audioLine(style: VisualStyle, voice: VoiceLang): String {
        val music = when (style) {
            VisualStyle.SATISFYING, VisualStyle.MACRO -> "Soft foley-forward bed, almost no melody."
            VisualStyle.CINEMATIC -> "Low cinematic pulse, no lyrics."
            else -> "Subtle music. Clear voice."
        }
        return if (voice == VoiceLang.OFF) "$music Voice off." else music
    }

    private fun voiceoverLine(voice: VoiceLang, subject: String, kind: RecipeKind): String {
        if (voice == VoiceLang.OFF) return ""
        val deKind = when (kind) {
            RecipeKind.FOOD -> "Sieh dir $subject genauer an."
            RecipeKind.DRINK -> "Ein Schluck, und $subject bleibt im Bild."
            RecipeKind.PRODUCT, RecipeKind.BEAUTY -> "$subject bleibt sichtbar. Schau es dir im Shop an."
            RecipeKind.LIFESTYLE -> "$subject in acht Sekunden, ohne Eile."
        }
        val ruKind = when (kind) {
            RecipeKind.FOOD -> "Смотри, как $subject держит свет и фактуру."
            RecipeKind.DRINK -> "Один жест — и $subject остаётся в кадре."
            RecipeKind.PRODUCT, RecipeKind.BEAUTY -> "$subject остаётся в кадре. Загляни в магазин."
            RecipeKind.LIFESTYLE -> "$subject за восемь секунд, без спешки."
        }
        return if (voice == VoiceLang.DE) deKind else ruKind
    }

    private fun hashtagsFor(title: String, kind: RecipeKind, style: VisualStyle): List<String> {
        val kindTag = when (kind) {
            RecipeKind.FOOD -> "#FoodFilm"
            RecipeKind.DRINK -> "#DrinkFilm"
            RecipeKind.PRODUCT -> "#TikTokShop"
            RecipeKind.BEAUTY -> "#BeautyFilm"
            RecipeKind.LIFESTYLE -> "#Lifestyle"
        }
        val styleTag = when (style) {
            VisualStyle.MACRO -> "#Macro"
            VisualStyle.SATISFYING -> "#Satisfying"
            VisualStyle.CINEMATIC -> "#Cinematic"
            VisualStyle.STREET -> "#Street"
            VisualStyle.STUDIO -> "#Studio"
            VisualStyle.LIFESTYLE -> "#Everyday"
        }
        val slug = title.split(Regex("\\s+"))
            .map { it.trim('#', ',', '.', '!', '?') }
            .filter { it.length >= 3 }
            .take(2)
            .map { "#" + it.replaceFirstChar { ch -> ch.uppercaseChar() } }
        val tags = (listOf(kindTag, styleTag) + slug + listOf("#VEO", "#8s")).distinct()
        return tags.take(5)
    }

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() + ".0" else value.toString()
}
