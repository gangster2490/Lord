package de.spardirekt.tiktokshop.data.remote

import com.google.common.truth.Truth.assertThat
import de.spardirekt.tiktokshop.data.model.copyAll
import de.spardirekt.tiktokshop.data.model.masterCopy
import de.spardirekt.tiktokshop.data.model.veoKomplett
import de.spardirekt.tiktokshop.ui.validateGenerate
import org.junit.Assert.assertThrows
import org.junit.Test

class GenerateResultParserTest {

    private val sampleJson = """
        {
          "productFacts": {
            "name": "Outdoor Rucksack 40L",
            "dimensions": "55 x 32 x 22 cm",
            "capacity": "40 L",
            "material": "Ripstop Nylon",
            "weight": "1,2 kg",
            "color": "Olivgrün",
            "includedItems": ["Regenhülle"],
            "keyFeatures": ["Brustgurt", "Laptop-Fach"],
            "warnings": [],
            "useCases": ["Wandern", "Pendeln"]
          },
          "hooks": ["Der sitzt den ganzen Tag","Kein Drücken mehr","Endlich Ordnung drin","Outdoor ready","Leicht wie nie"],
          "title": "🎒 Der Rucksack, der den Rücken rettet",
          "hashtags": ["#TikTokShop","#Rucksack","#Outdoor","#Wandern","#Alltag","#Deutschland","#MustHave"],
          "bannerText": ["40L Ripstop","Den ganzen Tag bequem","Laptop-Fach dabei","Jetzt unten im Warenkorb"],
          "bannerPrompt": "9:16 black background neon green backpack banner",
          "voiceoverText": "0s – Der sitzt.\n2s – Den ganzen Tag.\n4s – Ordnung drin.\n6s – Jetzt unten im Warenkorb.",
          "musicSuggestion": "Deep House, 118 BPM, ruhig und selbstbewusst",
          "soundEffects": "0s – Whoosh\n2s – Zipper\n4s – Soft click",
          "veoPrompt": "VIDEO LENGTH: Exactly 8 seconds. Person hiking with backpack.",
          "liveScript": "0:00 | Hook-Eröffnung\n0:15 | Produktvorstellung\n1:45 | CTA"
        }
    """.trimIndent()

    @Test
    fun parsesFullPayload() {
        val result = parseGenerateResult(sampleJson)
        assertThat(result.productFacts.name).isEqualTo("Outdoor Rucksack 40L")
        assertThat(result.hooks).hasSize(5)
        assertThat(result.hashtags).hasSize(7)
        assertThat(result.bannerText).hasSize(4)
        assertThat(result.title).contains("Rucksack")
        assertThat(result.veoPrompt).contains("8 seconds")
    }

    @Test
    fun stripsMarkdownFences() {
        val result = parseGenerateResult("```json\n$sampleJson\n```")
        assertThat(result.productFacts.capacity).isEqualTo("40 L")
    }

    @Test
    fun rejectsInvalidJson() {
        assertThrows(GenerateException::class.java) {
            parseGenerateResult("not json at all")
        }
    }

    @Test
    fun masterCopyContainsAllVideoSections() {
        val result = parseGenerateResult(sampleJson)
        val master = result.masterCopy()
        assertThat(master).contains("=== VIDEO LENGTH ===")
        assertThat(master).contains("8 Seconds")
        assertThat(master).contains("=== BANNER TEXT ===")
        assertThat(master).contains("=== VOICE SCRIPT ===")
        assertThat(master).contains("=== MUSIC ===")
        assertThat(master).contains("=== SOUND EFFECTS ===")
        assertThat(master).contains("=== VEO 3.1 PROMPT ===")
        assertThat(master).contains("Jetzt unten im Warenkorb")
    }

    @Test
    fun veoKomplettOmitsBannerAndLive() {
        val result = parseGenerateResult(sampleJson)
        val bundle = result.veoKomplett()
        assertThat(bundle).contains("GERMAN VOICEOVER")
        assertThat(bundle).doesNotContain("Live Script")
        assertThat(bundle).doesNotContain("BANNER TEXT")
    }

    @Test
    fun copyAllIncludesFactsAndHooks() {
        val result = parseGenerateResult(sampleJson)
        val all = result.copyAll()
        assertThat(all).contains("Produktdaten")
        assertThat(all).contains("Outdoor Rucksack 40L")
        assertThat(all).contains("5 Hook Ideas")
        assertThat(all).contains("TikTok Live Script")
    }

    @Test
    fun validationRequiresApiKey() {
        assertThat(validateGenerate("", true)).isEqualTo("Bitte Anthropic API Key eingeben.")
    }

    @Test
    fun validationRequiresProductImage() {
        assertThat(validateGenerate("sk-ant-test", false))
            .isEqualTo("Bitte zuerst ein Produktbild hochladen (Bild 1).")
    }

    @Test
    fun validationPassesWhenReady() {
        assertThat(validateGenerate("sk-ant-test", true)).isNull()
    }
}
