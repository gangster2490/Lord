package de.spardirekt.ugcagent.compliance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComplianceCheckerTest {

    @Test
    fun flagsSuperlativesAndMedicalClaims() {
        val text = "Das beste Produkt heilt alles und ist garantiert einzigartig, 100% limitiert, nur heute schnell geliefert."
        val hits = ComplianceChecker.checkCompliance(text)
        assertThat(hits).isNotEmpty()
        assertThat(ComplianceChecker.evaluate(text).hasForbiddenLanguage).isTrue()
        assertThat(ComplianceChecker.evaluate(text).forbiddenHits.map { it.label })
            .containsAtLeast("beste", "einzigartig", "garantiert")
    }

    @Test
    fun cleanUgcCopyPassesForbiddenList() {
        val prompt = "Handyaufnahme, leicht verzittert, Fensterlicht, jemand greift beiläufig zu und lacht kurz. Kein Studio."
        assertThat(ComplianceChecker.checkCompliance(prompt)).isEmpty()
    }

    @Test
    fun adDisclosureDetectsWerbungAndAnzeige() {
        assertThat(ComplianceChecker.checkAdDisclosure("ehrlich gesagt ganz ok. Werbung")).isTrue()
        assertThat(ComplianceChecker.checkAdDisclosure("kurze Anzeige dazwischen")).isTrue()
        assertThat(ComplianceChecker.checkAdDisclosure("beiläufig ausprobieren im Auto")).isFalse()
    }

    @Test
    fun missingDisclosureIsWarningNotAutoInsert() {
        val result = ComplianceChecker.evaluate(
            prompt = "Handheld, Küche, kurzes Zögern dann Zufriedenheit.",
            caption = "krass eigentlich",
        )
        assertThat(result.hasForbiddenLanguage).isFalse()
        assertThat(result.missingAdDisclosure).isTrue()
        assertThat(result.hasAdDisclosure).isFalse()
    }
}
