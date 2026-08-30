package de.spardirekt.tiktokshop

import de.spardirekt.tiktokshop.data.GeneratedContent
import de.spardirekt.tiktokshop.data.ProductDna
import de.spardirekt.tiktokshop.data.ProductFacts
import de.spardirekt.tiktokshop.data.ResultFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultFormatterTest {

    private val sample = GeneratedContent(
        productFacts = ProductFacts(
            name = "Angelkoffer Pro",
            color = "Olivgrün",
            keyFeatures = listOf("Wasserdicht", "3 Fächer"),
            useCases = listOf("Angeln"),
        ),
        hooks = listOf("Nie wieder Chaos", "Platz für alles"),
        title = "🎣 Der Koffer den Angler lieben",
        hashtags = listOf("#Angeln", "#Outdoor", "#TikTokShop"),
        bannerText = listOf("Wasserdicht", "3 Fächer", "Outdoor-ready", "Jetzt im Warenkorb"),
        bannerPrompt = "9:16 neon banner",
        voiceoverText = "0s – Hook\n2s – Feature",
        musicSuggestion = "Lo-Fi, 90 BPM",
        soundEffects = "0s – Whoosh\n2s – Click",
        veoPrompt = "VIDEO LENGTH: Exactly 8 seconds...",
        liveScript = "0:00 | Hook\n0:15 | Produkt",
    )

    @Test
    fun stripMarkdownFence_removesJsonFence() {
        val raw = "```json\n{\"title\":\"Hi\"}\n```"
        assertEquals("{\"title\":\"Hi\"}", ResultFormatter.stripMarkdownFence(raw))
    }

    @Test
    fun parseGeneratedContent_readsAllFields() {
        val json = """
            {
              "productFacts": {
                "name": "Testprodukt",
                "color": "Rot",
                "keyFeatures": ["A"],
                "includedItems": [],
                "warnings": [],
                "useCases": []
              },
              "hooks": ["H1","H2","H3","H4","H5"],
              "title": "🔥 Titel",
              "hashtags": ["#A","#B"],
              "bannerText": ["Z1","Z2","Z3","CTA"],
              "bannerPrompt": "banner",
              "voiceoverText": "0s – Hallo",
              "musicSuggestion": "Pop",
              "soundEffects": "0s – Boom",
              "veoPrompt": "VIDEO LENGTH: Exactly 8 seconds",
              "liveScript": "0:00 | Start"
            }
        """.trimIndent()

        val parsed = ResultFormatter.parseGeneratedContent(json)
        assertEquals("Testprodukt", parsed.productFacts.name)
        assertEquals("Rot", parsed.productFacts.color)
        assertEquals(listOf("H1", "H2", "H3", "H4", "H5"), parsed.hooks)
        assertEquals("🔥 Titel", parsed.title)
        assertEquals("VIDEO LENGTH: Exactly 8 seconds", parsed.veoPrompt)
    }

    @Test
    fun parseGeneratedContent_acceptsMarkdownWrappedJson() {
        val parsed = ResultFormatter.parseGeneratedContent("```json\n{\"title\":\"Wrapped\"}\n```")
        assertEquals("Wrapped", parsed.title)
    }

    @Test
    fun buildMasterText_includesRequiredSections() {
        val text = ResultFormatter.buildMasterText(sample)
        assertTrue(text.contains("=== VIDEO LENGTH ==="))
        assertTrue(text.contains("8 Seconds"))
        assertTrue(text.contains("=== BANNER TEXT ==="))
        assertTrue(text.contains("Jetzt im Warenkorb"))
        assertTrue(text.contains("=== VOICE SCRIPT ==="))
        assertTrue(text.contains("=== MUSIC ==="))
        assertTrue(text.contains("=== SOUND EFFECTS ==="))
        assertTrue(text.contains("=== VEO 3.1 PROMPT ==="))
        assertTrue(text.contains(sample.veoPrompt))
    }

    @Test
    fun buildVeoKomplett_excludesBannerAndLive() {
        val text = ResultFormatter.buildVeoKomplett(sample)
        assertTrue(text.contains("GERMAN VOICEOVER"))
        assertFalse(text.contains("BANNER TEXT"))
        assertFalse(text.contains("Live Script"))
    }

    @Test
    fun buildCopyAll_includesEveryCard() {
        val text = ResultFormatter.buildCopyAll(sample)
        listOf(
            "Produktdaten",
            "TikTok Titel",
            "5 Hook Ideas",
            "Hashtags",
            "Banner Text",
            "Banner Prompt",
            "Voice Script",
            "Music Suggestion",
            "Sound Effects",
            "Veo 3.1 Prompt",
            "Live Script",
        ).forEach { label ->
            assertTrue("missing $label", text.contains(label))
        }
        assertTrue(text.contains("Angelkoffer Pro"))
        assertTrue(text.contains("#Angeln"))
    }

    @Test
    fun formatFactsPlain_usesNichtErkennbarForEmptyLists() {
        val text = ResultFormatter.formatFactsPlain(ProductFacts(name = "X"))
        assertTrue(text.contains("Produktname: X"))
        assertTrue(text.contains("Lieferumfang: Nicht erkennbar"))
        assertTrue(text.contains("Features: Nicht erkennbar"))
    }

    @Test
    fun buildUserMessage_describesDescriptionImage() {
        val withDesc = ResultFormatter.buildUserMessage(2, true, "Unboxing", "Humorvoll")
        assertTrue(withDesc.contains("2 Produktbild(er)"))
        assertTrue(withDesc.contains("Beschreibungs-/Spezifikationsbild"))
        assertTrue(withDesc.contains("Video-Stil: Unboxing"))
        assertTrue(withDesc.contains("Ton: Humorvoll"))
        assertTrue(withDesc.contains("8 Sekunden"))

        val without = ResultFormatter.buildUserMessage(1, false, "Storytelling", "Freundlich")
        assertTrue(without.contains("Kein Beschreibungsbild"))
        assertTrue(without.contains("Nicht erkennbar"))
    }

    @Test
    fun buildVeoReferencePrompt_locksProductAndUsesNineSixteen() {
        val prompt = ResultFormatter.buildVeoReferencePrompt(
            ProductDna(
                name = "Rucksack",
                shape = "rectangular backpack",
                material = "nylon",
                color = "black",
                details = "2 straps",
                doNotChange = "keep zipper count",
                antiDistortion = "no warped straps",
            ),
        )
        assertTrue(prompt.contains("9:16"))
        assertTrue(prompt.contains("Rucksack"))
        assertTrue(prompt.contains("keep zipper count"))
        assertTrue(prompt.contains("no warped straps"))
        assertTrue(prompt.contains("No text, no logo, no price"))
    }

    @Test
    fun parseProductDna_readsSnakeCaseFields() {
        val dna = ResultFormatter.parseProductDna(
            """{"name":"Tasche","do_not_change":"Form","anti_distortion":"Kanten"}""",
        )
        assertEquals("Tasche", dna.name)
        assertEquals("Form", dna.doNotChange)
        assertEquals("Kanten", dna.antiDistortion)
    }
}
