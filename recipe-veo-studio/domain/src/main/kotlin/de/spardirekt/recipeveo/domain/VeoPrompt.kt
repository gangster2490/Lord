package de.spardirekt.recipeveo.domain

object VeoPrompt {
    fun compile(photoCount: Int, wish: String, compiledAt: Long): Prompt {
        require(photoCount > 0) { "Нужно фото товара." }
        val note = wish.trim()
        val title = when {
            note.length in 3..42 -> note.replaceFirstChar { it.uppercase() }
            else -> "Product film"
        }
        val refs = buildString {
            append("Uploaded product photos are the only visual reference. $photoCount frame(s).")
            if (note.isNotEmpty()) append(" Wish: $note")
        }
        val text = buildString {
            appendLine("FORMAT")
            appendLine("Vertical 9:16. Photorealistic product film. Exactly 8.0s.")
            appendLine()
            appendLine("REFERENCES")
            appendLine(refs)
            appendLine()
            appendLine("PRODUCT LOCK")
            appendLine("Match uploaded product photos exactly. Do not replace or redesign.")
            appendLine("Preserve silhouette, proportions, colors, materials and markings from the photos.")
            appendLine()
            appendLine("SETTING")
            appendLine("Uncluttered premium studio.")
            appendLine()
            appendLine("SHOT SEQUENCE")
            appendLine("0.0–2.0s — HOOK: distinctive surface catch light")
            appendLine("2.0–4.0s — IDENTITY: full product, locked geometry")
            appendLine("4.0–6.0s — FEATURE / DEMO: one confirmed product beat")
            appendLine("6.0–8.0s — HERO / CTA: product hero hold")
            appendLine()
            appendLine("ON-SCREEN TEXT")
            appendLine("None.")
            appendLine()
            appendLine("VOICEOVER")
            appendLine("OFF")
            appendLine()
            appendLine("AUDIO")
            appendLine("Subtle music. Voice off.")
            appendLine()
            appendLine("CRITICAL")
            appendLine("Keep product identity. Exactly 8.0s. Four blocks only. Photorealistic. No morphing.")
            appendLine()
            appendLine("NEGATIVE PROMPT")
            appendLine("- no generic replacement product")
            appendLine("- no redesign / wrong proportions / colors / materials")
            appendLine("- no invented parts")
            appendLine("- no product morphing")
            appendLine("- no marketplace UI")
            appendLine("- no CGI/cartoon look")
            appendLine()
            appendLine("TITLE")
            appendLine(title)
            appendLine()
            appendLine("HASHTAGS")
            appendLine("#ProductFilm #Studio #VEO #Prompt #Ad")
        }.trim()
        return Prompt(text = text, title = title, compiledAt = compiledAt)
    }
}
