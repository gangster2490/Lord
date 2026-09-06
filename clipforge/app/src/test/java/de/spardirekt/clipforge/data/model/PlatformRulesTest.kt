package de.spardirekt.clipforge.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlatformRulesTest {

    @Test
    fun tikTokNeverUsesLinkInBio() {
        AdLanguage.entries.forEach { lang ->
            val cta = Platform.TIKTOK_SHOP.canonicalCta(lang)
            assertThat(cta.lowercase()).doesNotContain("bio")
            assertThat(cta.lowercase()).doesNotContain("link in")
        }
    }

    @Test
    fun eachPlatformHasDistinctCta() {
        val ru = AdLanguage.RU
        assertThat(Platform.TIKTOK_SHOP.canonicalCta(ru)).isEqualTo("Сейчас в корзине")
        assertThat(Platform.REELS.canonicalCta(ru)).isEqualTo("Товар в профиле")
        assertThat(Platform.SHORTS.canonicalCta(ru)).isEqualTo("Смотрите описание")
    }

    @Test
    fun shortsCaptionIsTightest() {
        assertThat(Platform.SHORTS.captionMax).isLessThan(Platform.REELS.captionMax)
        assertThat(Platform.SHORTS.hashtagCount).isEqualTo(5)
        assertThat(Platform.TIKTOK_SHOP.hashtagCount).isEqualTo(8)
    }

    @Test
    fun fromIdFallbacks() {
        assertThat(Platform.fromId("nope")).isEqualTo(Platform.TIKTOK_SHOP)
        assertThat(AdLength.fromSeconds(15)).isEqualTo(AdLength.FIFTEEN)
        assertThat(AdLength.fromSeconds(8)).isEqualTo(AdLength.EIGHT)
        assertThat(AdFormula.fromId("ugc")).isEqualTo(AdFormula.UGC)
        assertThat(AdLanguage.fromId("de")).isEqualTo(AdLanguage.DE)
    }
}
