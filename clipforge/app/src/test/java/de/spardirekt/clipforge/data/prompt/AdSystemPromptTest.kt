package de.spardirekt.clipforge.data.prompt

import com.google.common.truth.Truth.assertThat
import de.spardirekt.clipforge.data.model.AdLanguage
import de.spardirekt.clipforge.data.model.AdLength
import de.spardirekt.clipforge.data.model.GenerateBrief
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.data.model.canonicalCta
import org.junit.Test

class AdSystemPromptTest {

    @Test
    fun locksProductAndRequiresHumanUse() {
        assertThat(AdSystemPrompt.VALUE).contains("locked asset")
        assertThat(AdSystemPrompt.VALUE).contains("HUMAN INTERACTION RULES")
        assertThat(AdSystemPrompt.VALUE).contains("At least 80%")
        assertThat(AdSystemPrompt.PRODUCT_LOCK_BLOCK).contains("{SECONDS}")
        assertThat(AdSystemPrompt.NEGATIVE_PROMPT).contains("No prices")
    }

    @Test
    fun forbidsTikTokLinkInBio() {
        assertThat(AdSystemPrompt.VALUE).contains("Never use \"Link in Bio\"")
        assertThat(AdSystemPrompt.VALUE).contains("TikTok Shop")
        assertThat(AdSystemPrompt.VALUE).contains("Instagram Reels")
        assertThat(AdSystemPrompt.VALUE).contains("YouTube Shorts")
    }

    @Test
    fun requiresJsonOnly() {
        assertThat(AdSystemPrompt.VALUE).contains("Reply with a single JSON object")
        assertThat(AdSystemPrompt.VALUE).contains("veoPrompt")
        assertThat(AdSystemPrompt.VALUE).contains("whyItConverts")
    }

    @Test
    fun durationHeaderSubstitutesPlatformAndSeconds() {
        val header = AdSystemPrompt.durationHeader(AdLength.FIFTEEN, Platform.REELS)
        assertThat(header).contains("Exactly 15 seconds")
        assertThat(header).contains("Instagram Reels")
        assertThat(header).doesNotContain("{SECONDS}")
        assertThat(header).doesNotContain("{PLATFORM}")
    }

    @Test
    fun userBriefCarriesPlatformCta() {
        val brief = GenerateBrief(
            platform = Platform.SHORTS,
            length = AdLength.FIFTEEN,
            formula = de.spardirekt.clipforge.data.model.AdFormula.UGC,
            style = de.spardirekt.clipforge.data.model.VisualStyle.UGC_RAW,
            language = AdLanguage.RU,
            wish = "на кухне",
            photoCount = 3,
        )
        val text = AdSystemPrompt.userBrief(brief)
        assertThat(text).contains("exactly 15 seconds")
        assertThat(text).contains(Platform.SHORTS.canonicalCta(AdLanguage.RU))
        assertThat(text).contains("на кухне")
        assertThat(text).contains("Photos uploaded: 3")
    }
}
