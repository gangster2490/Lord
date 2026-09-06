package de.spardirekt.ugcclean.gen

import com.google.common.truth.Truth.assertThat
import de.spardirekt.ugcclean.model.SpeechLanguage
import org.junit.Test

class ComplianceTest {
    @Test
    fun sanitizeCaption_stripsBannedWordsAndAddsDisclosure() {
        val caption = Compliance.sanitizeCaption("Das ist das beste Produkt garantiert", SpeechLanguage.DE, Fixtures.pan)
        assertThat(caption.lowercase()).doesNotContain("beste")
        assertThat(caption.lowercase()).doesNotContain("garantiert")
        assertThat(caption).contains("Werbung")
    }

    @Test
    fun sanitizeCaption_addsRussianDisclosure() {
        val caption = Compliance.sanitizeCaption("Обычный день с этим товаром", SpeechLanguage.RU, Fixtures.chair)
        assertThat(caption).contains("Реклама")
    }

    @Test
    fun hashtags_alwaysFive() {
        val tags = Compliance.sanitizeHashtags(listOf("#one", "two"), SpeechLanguage.DE)
        assertThat(tags).hasSize(5)
        assertThat(tags.all { it.startsWith("#") }).isTrue()
    }

    @Test
    fun details_usePlanNotForeignProduct() {
        val details = Compliance.details(Fixtures.organizer, SpeechLanguage.DE)
        assertThat(details).contains("desk organizer")
        assertThat(details.lowercase()).doesNotContain("microwave")
    }
}
