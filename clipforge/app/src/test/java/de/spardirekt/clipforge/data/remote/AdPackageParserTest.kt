package de.spardirekt.clipforge.data.remote

import com.google.common.truth.Truth.assertThat
import de.spardirekt.clipforge.data.model.AdFormula
import de.spardirekt.clipforge.data.model.AdLanguage
import de.spardirekt.clipforge.data.model.AdLength
import de.spardirekt.clipforge.data.model.AdPackage
import de.spardirekt.clipforge.data.model.GenerateBrief
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.data.model.VisualStyle
import de.spardirekt.clipforge.data.model.canonicalCta
import de.spardirekt.clipforge.data.model.copyAll
import de.spardirekt.clipforge.data.model.copyCaptionPack
import de.spardirekt.clipforge.data.model.copyVeoPack
import de.spardirekt.clipforge.data.model.ensureVeoDuration
import de.spardirekt.clipforge.data.model.guarded
import de.spardirekt.clipforge.data.model.normalizeHashtag
import de.spardirekt.clipforge.data.model.validateGenerate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class AdPackageParserTest {

    private val sample = """
        {
          "product": {
            "name": "Outdoor Rucksack 40L",
            "category": "Bags",
            "visualLock": "Olive ripstop, chest strap",
            "sellingAngle": "All-day comfort",
            "audience": "hikers",
            "keyFeatures": ["40L", "laptop sleeve"]
          },
          "hooks": ["Stop scrolling", "All day comfort", "No more digging", "Trail ready", "Light as air"],
          "caption": "The pack that sits right. Tap the cart below.",
          "hashtags": ["TikTokShop", "#Rucksack", "#Outdoor"],
          "onScreenTexts": ["All-day comfort", "40L ripstop", "Tap the cart below"],
          "cta": "Tap the cart below",
          "storyboard": [
            {"startSec": 0, "endSec": 2, "shot": "medium", "action": "Hiker already wearing the pack", "overlay": "Stop"}
          ],
          "voiceover": "0s – Stop.",
          "music": "Deep house 118",
          "soundEffects": "0s – whoosh",
          "veoPrompt": "Person hiking with locked olive backpack.",
          "thumbnailPrompt": "9:16 hiker with olive pack",
          "whyItConverts": "Product is on a person in frame one."
        }
    """.trimIndent()

    @Test
    fun parsesFullPayload() {
        val ad = parseAdPackage(sample)
        assertThat(ad.product.name).isEqualTo("Outdoor Rucksack 40L")
        assertThat(ad.hooks).hasSize(5)
        assertThat(ad.storyboard).hasSize(1)
        assertThat(ad.cta).contains("cart")
    }

    @Test
    fun stripsMarkdownFencesAndFindsObject() {
        val ad = parseAdPackage("Sure.\n```json\n$sample\n```\n")
        assertThat(ad.product.category).isEqualTo("Bags")
    }

    @Test
    fun rejectsInvalidJson() {
        assertThrows(GenerateException::class.java) {
            parseAdPackage("not json at all")
        }
    }

    @Test
    fun guardedEnforcesPlatformLimitsAndCta() {
        val brief = GenerateBrief(
            platform = Platform.SHORTS,
            length = AdLength.FIFTEEN,
            formula = AdFormula.HOOK_DEMO_CTA,
            style = VisualStyle.CINEMATIC,
            language = AdLanguage.EN,
            wish = "",
            photoCount = 1,
        )
        val longCaption = "x".repeat(400)
        val ad = parseAdPackage(sample).copy(
            caption = longCaption,
            cta = "",
            hashtags = (1..12).map { "#tag$it" },
            veoPrompt = "Just a scene, no header.",
        ).guarded(brief)
        assertThat(ad.caption.length).isAtMost(Platform.SHORTS.captionMax)
        assertThat(ad.hashtags).hasSize(Platform.SHORTS.hashtagCount)
        assertThat(ad.cta).isEqualTo(Platform.SHORTS.canonicalCta(AdLanguage.EN))
        assertThat(ad.veoPrompt).contains("Exactly 15 seconds")
        assertThat(ad.veoPrompt).contains("YouTube Shorts")
    }

    @Test
    fun copyPacksIncludeCoreSections() {
        val ad = parseAdPackage(sample)
        val brief = GenerateBrief(
            platform = Platform.TIKTOK_SHOP,
            length = AdLength.EIGHT,
            formula = AdFormula.HOOK_DEMO_CTA,
            style = VisualStyle.CINEMATIC,
            language = AdLanguage.EN,
            wish = "",
            photoCount = 1,
        )
        assertThat(ad.copyCaptionPack(brief.platform)).contains("Tap the cart below")
        assertThat(ad.copyVeoPack(brief.length)).contains("VEO 3.1 PROMPT")
        assertThat(ad.copyAll(brief.platform, brief.length, brief.formula, brief.language))
            .contains("Outdoor Rucksack 40L")
    }

    @Test
    fun hashtagNormalizer() {
        assertThat(normalizeHashtag(" TikTokShop ")).isEqualTo("#TikTokShop")
        assertThat(normalizeHashtag("#Already")).isEqualTo("#Already")
        assertThat(normalizeHashtag("")).isEmpty()
    }

    @Test
    fun ensureVeoDurationKeepsExistingHeader() {
        val prompt = "VIDEO LENGTH: Exactly 8 seconds. Hello."
        assertThat(ensureVeoDuration(prompt, AdLength.EIGHT, Platform.REELS))
            .isEqualTo(prompt)
    }

    @Test
    fun validationMessages() {
        assertThat(validateGenerate("", 1)).contains("OpenAI")
        assertThat(validateGenerate("sk-demo", 0)).contains("фото")
        assertThat(validateGenerate("sk-demo", 2)).isNull()
    }
}

class DemoAdGeneratorTest {

    private fun brief(
        platform: Platform,
        length: AdLength = AdLength.EIGHT,
        language: AdLanguage = AdLanguage.RU,
        formula: AdFormula = AdFormula.HOOK_DEMO_CTA,
    ) = GenerateBrief(
        platform = platform,
        length = length,
        formula = formula,
        style = VisualStyle.CINEMATIC,
        language = language,
        wish = "",
        photoCount = 1,
    )

    @Test
    fun tikTokShopRussianEightSeconds() = runBlocking {
        val ad = DemoAdGenerator.generate("sk-demo", emptyList(), brief(Platform.TIKTOK_SHOP))
        assertThat(ad.hooks).hasSize(5)
        assertThat(ad.cta).isEqualTo("Сейчас в корзине")
        assertThat(ad.caption.length).isAtMost(Platform.TIKTOK_SHOP.captionMax)
        assertThat(ad.hashtags).hasSize(Platform.TIKTOK_SHOP.hashtagCount)
        assertThat(ad.storyboard.last().endSec).isEqualTo(8.0)
        assertThat(ad.veoPrompt).contains("Exactly 8 seconds")
        assertThat(ad.veoPrompt).contains("HUMAN INTERACTION RULES")
        assertThat(ad.veoPrompt).contains("Negative prompt")
        assertThat(ad.veoPrompt.lowercase()).doesNotContain("link in bio")
    }

    @Test
    fun reelsGermanFifteenSeconds() = runBlocking {
        val ad = DemoAdGenerator.generate(
            "sk-demo",
            emptyList(),
            brief(Platform.REELS, AdLength.FIFTEEN, AdLanguage.DE, AdFormula.UGC),
        )
        assertThat(ad.cta).isEqualTo("Produkt im Profil")
        assertThat(ad.storyboard.last().endSec).isEqualTo(15.0)
        assertThat(ad.veoPrompt).contains("Instagram Reels")
        assertThat(ad.caption.length).isAtMost(Platform.REELS.captionMax)
        assertThat(ad.voiceover).contains("13s")
    }

    @Test
    fun shortsEnglishUsesDescriptionCta() = runBlocking {
        val ad = DemoAdGenerator.generate(
            "sk-demo",
            emptyList(),
            brief(Platform.SHORTS, AdLength.EIGHT, AdLanguage.EN),
        )
        assertThat(ad.cta).isEqualTo("See description to shop")
        assertThat(ad.hashtags).hasSize(Platform.SHORTS.hashtagCount)
        assertThat(ad.caption.length).isAtMost(Platform.SHORTS.captionMax)
        assertThat(ad.whyItConverts).contains("YouTube Shorts")
    }

    @Test
    fun factoryPicksDemoForSkDemo() {
        assertThat(isDemoKey("sk-demo")).isTrue()
        assertThat(isDemoKey("SK-DEMO-local")).isTrue()
        assertThat(isDemoKey("sk-proj-real")).isFalse()
        assertThat(adGeneratorFor("sk-demo")).isSameInstanceAs(DemoAdGenerator)
    }
}

class JsonExtractorTest {
    @Test
    fun extractsFirstToLastBrace() {
        val raw = "prefix {\"a\":1} trailing"
        assertThat(JsonExtractor.extractObject(raw)).isEqualTo("{\"a\":1}")
    }

    @Test
    fun throwsWhenMissing() {
        assertThrows(GenerateException::class.java) {
            JsonExtractor.extractObject("no object")
        }
    }
}
