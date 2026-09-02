package de.spardirekt.recipeveo.domain

object VeoRecipe {
    fun compile(input: GenerateInput, compiledAt: Long): PromptPackage {
        require(input.photoCount > 0) { "Нужно хотя бы одно фото товара." }
        val title = titleFor(input)
        val voiceover = voiceoverFor(input)
        val hashtags = hashtagsFor(input, title)
        val lock = lockFor(input)
        val setting = settingFor(input.creative)
        val shots = shotsFor(input)
        val refs = buildString {
            append("All uploaded images are analyzed together. ${input.photoCount} frame(s): product photos, details, demo shots, and description/listing screenshots.")
            if (input.wish.isNotBlank()) append(" Wish: ${input.wish.trim()}")
            if (input.demoProduct) append(" Demo subject: Velvet Gold Night Cream.")
        }
        val prompt = buildString {
            appendLine("FORMAT")
            appendLine(formatLine(input))
            appendLine()
            appendLine("REFERENCES")
            appendLine(refs)
            appendLine()
            appendLine("PRODUCT LOCK")
            appendLine("Match uploaded product photos exactly. Do not replace or redesign.")
            appendLine(lock)
            appendLine()
            appendLine("SETTING")
            appendLine(setting)
            appendLine()
            appendLine("SHOT SEQUENCE")
            shots.forEach { appendLine(it) }
            appendLine()
            appendLine("ON-SCREEN TEXT")
            appendLine("None.")
            appendLine()
            appendLine("VOICEOVER")
            appendLine(if (input.voice == VoiceLang.OFF) "OFF" else voiceover)
            appendLine()
            appendLine("AUDIO")
            appendLine(if (input.voice == VoiceLang.OFF) "Subtle music. Voice off." else "Subtle music. Clear voice.")
            appendLine()
            appendLine("CRITICAL")
            appendLine("Keep product identity. Exactly 8.0s. Four blocks only. Photorealistic. No morphing. Marketplace UI is reference only, never a video frame.")
            appendLine()
            appendLine("NEGATIVE PROMPT")
            appendLine("- no generic replacement product")
            appendLine("- no redesign / wrong proportions / colors / materials")
            appendLine("- no missing confirmed parts or invented accessories")
            appendLine("- no product morphing")
            appendLine("- no marketplace UI or phone interface")
            appendLine("- no CGI/cartoon look")
            appendLine()
            appendLine("TITLE")
            appendLine(title)
            appendLine()
            appendLine("HASHTAGS")
            appendLine(hashtags.joinToString(" "))
        }.trim()
        return PromptPackage(
            veoPrompt = prompt,
            voiceover = if (input.voice == VoiceLang.OFF) "" else voiceover,
            title = title,
            hashtags = hashtags,
            compiledAt = compiledAt,
        )
    }

    fun sharePackage(pkg: PromptPackage): String = buildString {
        appendLine(pkg.veoPrompt)
        appendLine()
        appendLine("---")
        if (pkg.voiceover.isNotBlank()) {
            appendLine("VOICEOVER")
            appendLine(pkg.voiceover)
            appendLine()
        }
        appendLine("TITLE")
        appendLine(pkg.title)
        appendLine()
        appendLine("HASHTAGS")
        appendLine(pkg.hashtags.joinToString(" "))
    }.trim()

    data class GenerateInput(
        val photoCount: Int,
        val wish: String,
        val voice: VoiceLang,
        val creative: CreativeMode,
        val tiktokShop: Boolean,
        val demoProduct: Boolean = false,
    )

    private fun formatLine(input: GenerateInput): String {
        val kind = if (input.tiktokShop) "TikTok Shop ad" else "product film"
        val style = if (input.creative == CreativeMode.Auto) "HighPerformingProductAd" else input.creative.name
        return "Vertical 9:16. Photorealistic $kind. Exactly 8.0s. $style."
    }

    private fun titleFor(input: GenerateInput): String {
        val wish = input.wish.trim()
        if (wish.length in 3..42) return wish.replaceFirstChar { it.uppercase() }
        return if (input.demoProduct) "Velvet Gold Night Cream" else "Product film"
    }

    private fun lockFor(input: GenerateInput): String {
        val wish = input.wish.trim().takeIf { it.isNotEmpty() }?.let { " Honor wish only if it does not change the photographed product: $it." } ?: ""
        return if (input.demoProduct) {
            "gold cap, ivory jar, cream texture, short jar silhouette, visible brand mark.$wish"
        } else {
            "Preserve silhouette, proportions, colors, materials, markings and distinctive parts from the uploaded photos. Do not invent parts.$wish"
        }
    }

    private fun settingFor(creative: CreativeMode): String = when (creative) {
        CreativeMode.Lifestyle -> "Lived-in interior, practical window light, product still hero."
        CreativeMode.Macro -> "Macro tabletop, shallow depth, specular highlights only on the product."
        CreativeMode.Satisfying -> "Clean tabletop, slow tactile motion, no extra props."
        CreativeMode.Unboxing -> "Neutral studio table, box and product only."
        CreativeMode.Demo -> "Uncluttered premium studio, room for one hand."
        else -> "Uncluttered premium studio."
    }

    private fun shotsFor(input: GenerateInput): List<String> {
        val demo = input.demoProduct
        return when (input.creative) {
            CreativeMode.Macro -> listOf(
                if (demo) "0.0–2.0s — HOOK: extreme close-up, gold lid catch light" else "0.0–2.0s — HOOK: extreme close-up of a confirmed product surface",
                if (demo) "2.0–4.0s — IDENTITY: jar silhouette fills the frame" else "2.0–4.0s — IDENTITY: locked silhouette fills the frame",
                if (demo) "4.0–6.0s — FEATURE / DEMO: cream texture at the rim" else "4.0–6.0s — FEATURE / DEMO: one confirmed texture or control, no invented parts",
                "6.0–8.0s — HERO / CTA: hero still, label locked",
            )
            CreativeMode.Demo, CreativeMode.Unboxing -> listOf(
                if (demo) "0.0–2.0s — HOOK: gold lid catch light" else "0.0–2.0s — HOOK: distinctive surface catch light",
                if (demo) "2.0–4.0s — IDENTITY: full ivory jar" else "2.0–4.0s — IDENTITY: full product, locked geometry",
                if (demo) "4.0–6.0s — FEATURE / DEMO: one hand lifts the lid" else "4.0–6.0s — FEATURE / DEMO: one confirmed hand beat, product dominant",
                "6.0–8.0s — HERO / CTA: product hero hold",
            )
            CreativeMode.Lifestyle -> listOf(
                "0.0–2.0s — HOOK: product enters warm interior light",
                if (demo) "2.0–4.0s — IDENTITY: jar on a real surface" else "2.0–4.0s — IDENTITY: product on a real surface, identity locked",
                "4.0–6.0s — FEATURE / DEMO: one quiet human beat, product dominant",
                "6.0–8.0s — HERO / CTA: product hero hold",
            )
            CreativeMode.Satisfying -> listOf(
                if (demo) "0.0–2.0s — HOOK: lid thread turning in macro" else "0.0–2.0s — HOOK: slow tactile motion on a confirmed part",
                if (demo) "2.0–4.0s — IDENTITY: full jar, cream at the rim" else "2.0–4.0s — IDENTITY: full product, materials locked",
                if (demo) "4.0–6.0s — FEATURE / DEMO: slow lid lift, texture pull" else "4.0–6.0s — FEATURE / DEMO: one slow confirmed action",
                "6.0–8.0s — HERO / CTA: product hero hold",
            )
            else -> listOf(
                if (demo) "0.0–2.0s — HOOK: gold lid catch light" else "0.0–2.0s — HOOK: distinctive surface catch light",
                if (demo) "2.0–4.0s — IDENTITY: full ivory jar" else "2.0–4.0s — IDENTITY: full product, locked geometry",
                if (demo) "4.0–6.0s — FEATURE / DEMO: one hand lifts lid" else "4.0–6.0s — FEATURE / DEMO: one confirmed product beat",
                "6.0–8.0s — HERO / CTA: product hero hold",
            )
        }
    }

    private fun voiceoverFor(input: GenerateInput): String {
        if (input.voice == VoiceLang.OFF) return ""
        val shopDe = if (input.tiktokShop) " Schau ihn dir im TikTok Shop an." else ""
        val shopRu = if (input.tiktokShop) " Загляни скорее в TikTok Shop." else ""
        return if (input.voice == VoiceLang.DE) {
            if (input.demoProduct) {
                "Der goldene Deckel und die cremige Textur bleiben sichtbar.$shopDe"
            } else {
                "Das Produkt bleibt originalgetreu im Bild.$shopDe"
            }
        } else {
            if (input.demoProduct) {
                "Золотая крышка и кремовая текстура остаются на виду вечером.$shopRu"
            } else {
                "Товар остаётся таким, как на фото.$shopRu"
            }
        }
    }

    private fun hashtagsFor(input: GenerateInput, title: String): List<String> {
        val shop = if (input.tiktokShop) "#TikTokShop" else "#ProductFilm"
        val mode = when (input.creative) {
            CreativeMode.Macro -> "#Macro"
            CreativeMode.Satisfying -> "#Satisfying"
            CreativeMode.Lifestyle -> "#Lifestyle"
            CreativeMode.Unboxing -> "#Unboxing"
            else -> "#Studio"
        }
        val stop = setOf("the", "and", "für", "und", "для", "как")
        val slug = title.split(Regex("\\s+"))
            .filter { it.length >= 3 && it.lowercase() !in stop }
            .take(2)
            .map { "#" + it.trim('#', ',', '.').replaceFirstChar { ch -> ch.uppercaseChar() } }
        return (listOf(shop, mode) + slug + listOf("#VEO", "#Prompt", "#Ad")).distinct().take(5)
    }
}
