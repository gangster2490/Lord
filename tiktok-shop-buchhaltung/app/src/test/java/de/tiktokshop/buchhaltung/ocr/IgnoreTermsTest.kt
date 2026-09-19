package de.tiktokshop.buchhaltung.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IgnoreTermsTest {

    @Test
    fun `month names are ignored as merchant lines`() {
        assertThat(IgnoreTerms.isIgnoredMerchantLine("Juli")).isTrue()
        assertThat(IgnoreTerms.isIgnoredMerchantLine("januar")).isTrue()
        assertThat(IgnoreTerms.isIgnoredMerchantLine("Dezember")).isTrue()
    }

    @Test
    fun `chart and header labels are ignored`() {
        assertThat(IgnoreTerms.isIgnoredMerchantLine("Budget & Verlauf")).isTrue()
        assertThat(IgnoreTerms.isIgnoredMerchantLine("Sheet1")).isTrue()
        assertThat(IgnoreTerms.isIgnoredMerchantLine("Fields explanation")).isTrue()
    }

    @Test
    fun `real merchant names are not ignored`() {
        assertThat(IgnoreTerms.isIgnoredMerchantLine("Google AI Pro")).isFalse()
        assertThat(IgnoreTerms.isIgnoredMerchantLine("ChatGPT Plus")).isFalse()
    }

    @Test
    fun `blank lines are ignored`() {
        assertThat(IgnoreTerms.isIgnoredMerchantLine("   ")).isTrue()
    }
}
