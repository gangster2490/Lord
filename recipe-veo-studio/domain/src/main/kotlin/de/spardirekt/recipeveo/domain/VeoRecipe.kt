package de.spardirekt.recipeveo.domain

object VeoRecipe {
    fun compile(input: GenerateInput, compiledAt: Long): PromptPackage {
        require(input.photoCount > 0) { "Нужно хотя бы одно фото товара." }
        val title = titleFor(input)
        val voiceover = voiceoverFor(input)
        val hashtags = hashtagsFor(input, title)
        val prompt = buildString {
            appendLine("FORMAT")
            appendLine(formatLine(input))
            appendLine()
            appendLine("REFERENCES")
            appendLine(refsFor(input))
            appendLine()
            appendLine("PRODUCT LOCK")
            appendLine("Match uploaded product photos exactly. Do not replace or redesign.")
            appendLine(lockFor(input))
            appendLine()
            appendLine("SETTING")
            appendLine(settingFor(input.style))
            appendLine()
            appendLine("SHOT SEQUENCE")
            shotsFor(input).forEach { appendLine(it) }
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
            appendLine("Keep product identity. Exactly 8.0s. Four blocks only. Photorealistic. No morphing. Marketplace UI is reference only.")
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

    fun fromProject(project: Project, compiledAt: Long): PromptPackage = compile(
        GenerateInput(
            photoCount = project.photos.size,
            wish = project.wish,
            voice = project.voice,
            style = project.style,
            tiktokShop = project.tiktokShop,
            profile = Catalog.fromPhotos(project.photos, project.productId),
        ),
        compiledAt,
    )

    data class GenerateInput(
        val photoCount: Int,
        val wish: String,
        val voice: VoiceLang,
        val style: ShotStyle,
        val tiktokShop: Boolean,
        val profile: ProductProfile? = null,
    )

    private fun formatLine(input: GenerateInput): String {
        val kind = if (input.tiktokShop) "TikTok Shop ad" else "product film"
        val style = if (input.style == ShotStyle.Auto) "HighPerformingProductAd" else input.style.name
        return "Vertical 9:16. Photorealistic $kind. Exactly 8.0s. $style."
    }

    private fun refsFor(input: GenerateInput): String {
        val base = "All uploaded images analyzed together. ${input.photoCount} frame(s): product, detail, demo, listing."
        val wish = input.wish.trim().takeIf { it.isNotEmpty() }?.let { " Wish: $it" } ?: ""
        val named = input.profile?.let { " Subject: ${it.title}." } ?: ""
        return base + named + wish
    }

    private fun titleFor(input: GenerateInput): String {
        val wish = input.wish.trim()
        if (wish.length in 3..42) return wish.replaceFirstChar { it.uppercase() }
        return input.profile?.title ?: "Product film"
    }

    private fun lockFor(input: GenerateInput): String {
        val wish = input.wish.trim().takeIf { it.isNotEmpty() }
            ?.let { " Honor wish only if it does not change the photographed product: $it." } ?: ""
        val details = input.profile?.lockDetails
            ?: "Preserve silhouette, proportions, colors, materials, markings and distinctive parts from the uploaded photos. Do not invent parts."
        return details + wish
    }

    private fun settingFor(style: ShotStyle): String = when (style) {
        ShotStyle.Lifestyle -> "Lived-in interior, practical window light, product still hero."
        ShotStyle.Macro -> "Macro tabletop, shallow depth, specular highlights only on the product."
        ShotStyle.Satisfying -> "Clean tabletop, slow tactile motion, no extra props."
        ShotStyle.Unboxing -> "Neutral studio table, box and product only."
        ShotStyle.Demo -> "Uncluttered premium studio, room for one hand."
        else -> "Uncluttered premium studio."
    }

    private fun shotsFor(input: GenerateInput): List<String> {
        val named = input.profile != null
        val identity = if (named) input.profile!!.title else "the photographed product"
        return when (input.style) {
            ShotStyle.Macro -> listOf(
                "0.0–2.0s — HOOK: extreme close-up of a confirmed surface on $identity",
                "2.0–4.0s — IDENTITY: locked silhouette fills the frame",
                "4.0–6.0s — FEATURE / DEMO: one confirmed texture or control",
                "6.0–8.0s — HERO / CTA: hero still, identity locked",
            )
            ShotStyle.Demo, ShotStyle.Unboxing -> listOf(
                "0.0–2.0s — HOOK: distinctive surface catch light",
                "2.0–4.0s — IDENTITY: full $identity, locked geometry",
                "4.0–6.0s — FEATURE / DEMO: one confirmed hand beat, product dominant",
                "6.0–8.0s — HERO / CTA: product hero hold",
            )
            ShotStyle.Lifestyle -> listOf(
                "0.0–2.0s — HOOK: $identity enters warm interior light",
                "2.0–4.0s — IDENTITY: product on a real surface, identity locked",
                "4.0–6.0s — FEATURE / DEMO: one quiet human beat, product dominant",
                "6.0–8.0s — HERO / CTA: product hero hold",
            )
            ShotStyle.Satisfying -> listOf(
                "0.0–2.0s — HOOK: slow tactile motion on a confirmed part",
                "2.0–4.0s — IDENTITY: full $identity, materials locked",
                "4.0–6.0s — FEATURE / DEMO: one slow confirmed action",
                "6.0–8.0s — HERO / CTA: product hero hold",
            )
            else -> listOf(
                "0.0–2.0s — HOOK: distinctive surface catch light",
                "2.0–4.0s — IDENTITY: full $identity, locked geometry",
                "4.0–6.0s — FEATURE / DEMO: one confirmed product beat",
                "6.0–8.0s — HERO / CTA: product hero hold",
            )
        }
    }

    private fun voiceoverFor(input: GenerateInput): String {
        if (input.voice == VoiceLang.OFF) return ""
        val shopDe = if (input.tiktokShop) " Schau ihn dir im TikTok Shop an." else ""
        val shopRu = if (input.tiktokShop) " Загляни скорее в TikTok Shop." else ""
        val subjectDe = input.profile?.title ?: "Das Produkt"
        val subjectRu = input.profile?.title ?: "Товар"
        return if (input.voice == VoiceLang.DE) {
            "$subjectDe bleibt originalgetreu im Bild.$shopDe"
        } else {
            "$subjectRu остаётся таким, как на фото.$shopRu"
        }
    }

    private fun hashtagsFor(input: GenerateInput, title: String): List<String> {
        val shop = if (input.tiktokShop) "#TikTokShop" else "#ProductFilm"
        val mode = when (input.style) {
            ShotStyle.Macro -> "#Macro"
            ShotStyle.Satisfying -> "#Satisfying"
            ShotStyle.Lifestyle -> "#Lifestyle"
            ShotStyle.Unboxing -> "#Unboxing"
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
