package de.spardirekt.clipforge.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PackageGuardTest {

    private fun brief(
        platform: Platform = Platform.TIKTOK_SHOP,
        length: AdLength = AdLength.EIGHT,
        language: AdLanguage = AdLanguage.RU,
    ) = GenerateBrief(
        platform = platform,
        length = length,
        formula = AdFormula.HOOK_DEMO_CTA,
        style = VisualStyle.CINEMATIC,
        language = language,
        wish = "",
        photoCount = 1,
    )

    @Test
    fun replacesLinkInBioCta() {
        val ad = AdPackage(cta = "Link in bio now")
        val hardened = ad.guarded(brief())
        assertThat(hardened.cta).isEqualTo("Сейчас в корзине")
        assertThat(PackageGuard.isForbiddenCta("ссылка в био")).isTrue()
    }

    @Test
    fun clipsHooksToSixWords() {
        val ad = AdPackage(
            hooks = listOf("one two three four five six seven eight"),
        )
        val hardened = ad.guarded(brief())
        assertThat(hardened.hooks.first().split(" ")).hasSize(6)
        assertThat(hardened.hooks).hasSize(5)
    }

    @Test
    fun padsHooksAndHashtags() {
        val hardened = AdPackage(
            hooks = listOf("Стой"),
            hashtags = listOf("#one"),
        ).guarded(brief(platform = Platform.SHORTS))
        assertThat(hardened.hooks).hasSize(5)
        assertThat(hardened.hooks.first()).isEqualTo("Стой")
        assertThat(hardened.hashtags).hasSize(Platform.SHORTS.hashtagCount)
        assertThat(hardened.hashtags.first()).isEqualTo("#one")
    }

    @Test
    fun lastStoryboardOverlayIsCta() {
        val hardened = AdPackage(
            cta = "Сейчас в корзине",
            onScreenTexts = listOf("Не скролл"),
            storyboard = listOf(
                StoryboardShot(0.0, 2.0, "medium", "hook", "Не скролл"),
                StoryboardShot(2.0, 8.0, "medium", "end", "Wrong"),
            ),
        ).guarded(brief())
        assertThat(hardened.storyboard.last().overlay).isEqualTo("Сейчас в корзине")
        assertThat(hardened.onScreenTexts.last()).isEqualTo("Сейчас в корзине")
        assertThat(hardened.onScreenTexts).contains("Не скролл")
    }

    @Test
    fun keepsCtaWhenFiveOverlaysAlreadyPresent() {
        val hardened = AdPackage(
            cta = "Сейчас в корзине",
            onScreenTexts = listOf("A", "B", "C", "D", "E"),
        ).guarded(brief())
        assertThat(hardened.onScreenTexts).hasSize(5)
        assertThat(hardened.onScreenTexts.last()).isEqualTo("Сейчас в корзине")
        assertThat(hardened.onScreenTexts).contains("A")
        assertThat(hardened.onScreenTexts).doesNotContain("E")
    }

    @Test
    fun rewritesWrongVeoDurationHeader() {
        val ad = AdPackage(
            veoPrompt = "VIDEO LENGTH: Exactly 8 seconds. 9:16 vertical. TikTok Shop. Use the uploaded images as the exact locked product reference.",
        )
        val hardened = ad.guarded(brief(platform = Platform.REELS, length = AdLength.FIFTEEN))
        assertThat(hardened.veoPrompt).contains("Exactly 15 seconds")
        assertThat(hardened.veoPrompt).contains("Instagram Reels")
        val lengthLines = hardened.veoPrompt.lines().filter { it.contains("VIDEO LENGTH:", ignoreCase = true) }
        assertThat(lengthLines).hasSize(1)
        assertThat(lengthLines.single()).contains("15 seconds")
    }

    @Test
    fun stripsPriceSpamFromCaption() {
        val ad = AdPackage(caption = "Крем 19,90 € со скидкой 50%")
        val hardened = ad.guarded(brief())
        assertThat(hardened.caption.lowercase()).doesNotContain("€")
        assertThat(hardened.caption.lowercase()).doesNotContain("скидк")
    }

    @Test
    fun completesVeoWithLockAndHumanBlocks() {
        val ad = AdPackage(veoPrompt = "A person holds the jar.")
        val hardened = ad.guarded(brief(platform = Platform.REELS, length = AdLength.FIFTEEN))
        assertThat(hardened.veoPrompt).contains("Exactly 15 seconds")
        assertThat(hardened.veoPrompt).contains("Instagram Reels")
        assertThat(hardened.veoPrompt).contains("HUMAN INTERACTION RULES")
        assertThat(hardened.veoPrompt).contains("Negative prompt")
        assertThat(hardened.veoPrompt).contains("locked product")
    }

    @Test
    fun storyboardCoversFullDuration() {
        val ad = AdPackage(
            storyboard = listOf(
                StoryboardShot(0.0, 2.0, "medium", "hook", "Stop"),
            ),
        )
        val hardened = ad.guarded(brief(length = AdLength.FIFTEEN))
        assertThat(hardened.storyboard.first().startSec).isEqualTo(0.0)
        assertThat(hardened.storyboard.last().endSec).isEqualTo(15.0)
    }

    @Test
    fun emptyStoryboardGetsDefaultBeats() {
        val hardened = AdPackage().guarded(brief(length = AdLength.EIGHT))
        assertThat(hardened.storyboard).hasSize(4)
        assertThat(hardened.storyboard.last().endSec).isEqualTo(8.0)
    }

    @Test
    fun retryableHttpCodes() {
        assertThat(PackageGuard.isRetryableHttp(429)).isTrue()
        assertThat(PackageGuard.isRetryableHttp(503)).isTrue()
        assertThat(PackageGuard.isRetryableHttp(400)).isFalse()
        assertThat(PackageGuard.isRetryableHttp(401)).isFalse()
    }
}
