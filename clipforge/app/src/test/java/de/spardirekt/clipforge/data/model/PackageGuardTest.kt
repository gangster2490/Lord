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
        assertThat(hardened.hooks.single().split(" ")).hasSize(6)
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
