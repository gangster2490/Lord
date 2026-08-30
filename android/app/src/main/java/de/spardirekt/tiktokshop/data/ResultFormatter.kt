package de.spardirekt.tiktokshop.data

/**
 * Pure formatting helpers ported from the legacy web app copy buttons.
 * Kept free of Android types so they can be unit-tested on the JVM.
 */
object ResultFormatter {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun stripMarkdownFence(raw: String): String =
        raw.trim()
            .replace(Regex("^```(?:json)?\\n?"), "")
            .replace(Regex("\\n?```$"), "")
            .trim()

    fun parseGeneratedContent(raw: String): GeneratedContent {
        val cleaned = stripMarkdownFence(raw)
        if (cleaned.isBlank()) {
            error("Leere Antwort von Anthropic.")
        }
        return try {
            json.decodeFromString(GeneratedContent.serializer(), cleaned)
        } catch (e: Exception) {
            throw IllegalArgumentException("Kein gültiges JSON: ${cleaned.take(400)}", e)
        }
    }

    fun parseProductDna(raw: String): ProductDna {
        val cleaned = stripMarkdownFence(raw)
        return json.decodeFromString(ProductDna.serializer(), cleaned)
    }

    fun buildMasterText(content: GeneratedContent): String {
        val banner = content.bannerText.joinToString("\n")
        return listOf(
            "=== VIDEO LENGTH ===\n8 Seconds",
            "=== BANNER TEXT ===\n$banner",
            "=== VOICE SCRIPT ===\n${content.voiceoverText}",
            "=== MUSIC ===\n${content.musicSuggestion}",
            "=== SOUND EFFECTS ===\n${content.soundEffects}",
            "=== VEO 3.1 PROMPT ===\n${content.veoPrompt}",
        ).joinToString("\n\n")
    }

    fun buildVeoKomplett(content: GeneratedContent): String = listOf(
        "=== VIDEO LENGTH ===\n8 Seconds",
        "=== VEO 3.1 PROMPT ===\n${content.veoPrompt}",
        "=== GERMAN VOICEOVER ===\n${content.voiceoverText}",
        "=== MUSIC ===\n${content.musicSuggestion}",
        "=== SOUND EFFECTS ===\n${content.soundEffects}",
    ).joinToString("\n\n")

    fun buildCopyAll(content: GeneratedContent): String {
        val facts = formatFactsPlain(content.productFacts)
        val hooks = content.hooks.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString("\n")
        val hashtags = content.hashtags.joinToString(" ")
        val banner = content.bannerText.joinToString("\n")
        return listOf(
            "=== Produktdaten ===\n$facts",
            "=== TikTok Titel ===\n${content.title}",
            "=== 5 Hook Ideas ===\n$hooks",
            "=== Hashtags ===\n$hashtags",
            "=== Banner Text ===\n$banner",
            "=== Banner Prompt ===\n${content.bannerPrompt}",
            "=== Voice Script ===\n${content.voiceoverText}",
            "=== Music Suggestion ===\n${content.musicSuggestion}",
            "=== Sound Effects ===\n${content.soundEffects}",
            "=== Veo 3.1 Prompt ===\n${content.veoPrompt}",
            "=== Live Script ===\n${content.liveScript}",
        ).joinToString("\n\n")
    }

    fun formatFactsPlain(facts: ProductFacts): String {
        val rows = listOf(
            "Produktname: ${facts.name}",
            "Maße: ${facts.dimensions}",
            "Kapazität: ${facts.capacity}",
            "Material: ${facts.material}",
            "Gewicht: ${facts.weight}",
            "Farbe: ${facts.color}",
            "Lieferumfang: ${facts.includedItems.joinToString(", ").ifBlank { "Nicht erkennbar" }}",
            "Features: ${facts.keyFeatures.joinToString(", ").ifBlank { "Nicht erkennbar" }}",
            "Warnhinweise: ${facts.warnings.joinToString(", ").ifBlank { "Nicht erkennbar" }}",
            "Anwendung: ${facts.useCases.joinToString(", ").ifBlank { "Nicht erkennbar" }}",
        )
        return rows.joinToString("\n")
    }

    fun buildVeoReferencePrompt(dna: ProductDna?): String {
        val name = dna?.name?.ifBlank { "product" } ?: "product"
        val shape = dna?.shape?.ifBlank { "compact form" } ?: "compact form"
        val material = dna?.material.orEmpty()
        val color = dna?.color.orEmpty()
        val details = dna?.details.orEmpty()
        val doNotChange = dna?.doNotChange?.ifBlank { "original proportions and form" }
            ?: "original proportions and form"
        val antiDistortion = dna?.antiDistortion?.ifBlank { "do not distort edges or proportions" }
            ?: "do not distort edges or proportions"

        return """
            PRODUCT REFERENCE MODE — VEO 3.1

            Create one ultra-clean vertical 9:16 studio product reference image.

            MAIN SUBJECT:
            One single $name.

            PRODUCT DNA:
            - Shape: $shape
            - Material: $material
            - Color: $color
            - Key details: $details

            COMPOSITION:
            - Vertical 9:16, 1080x1920px
            - Product centered, 3/4 front angle
            - Top 15% and bottom 15% empty space
            - Clean white or very light gray background (#FAFAFA)
            - Soft professional studio lighting
            - Subtle drop shadow under product
            - No text, no logo, no price, no hands, no people

            ACCURACY RULES:
            - $doNotChange
            - $antiDistortion
            - Do not add extra elements
            - Do not change proportions
            - Do not invent new features
            - Keep original materials and colors

            STYLE:
            Ultra photorealistic premium product photo.
            Amazon/Otto.de style. Sharp, clean, commercial photography.
            High detail, 8K quality.
        """.trimIndent()
    }

    fun buildUserMessage(productCount: Int, hasDescription: Boolean, style: String, tone: String): String {
        val parts = mutableListOf("$productCount Produktbild(er) hochgeladen.")
        if (hasDescription) {
            parts += "Zusätzlich ein Beschreibungs-/Spezifikationsbild – OCR anwenden und alle Fakten extrahieren und zusammenführen."
        } else {
            parts += "Kein Beschreibungsbild vorhanden. Setze unbekannte productFacts auf \"Nicht erkennbar\"."
        }
        return parts.joinToString(" ") +
            "\n\nVideo-Stil: $style\nTon: $tone\n\nAlle Videos sind exakt 8 Sekunden lang.\n\nNur JSON zurückgeben."
    }
}
