package de.tiktokshop.buchhaltung.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountParserTest {

    // TC03 - Deutsche Dezimalzahl
    @Test
    fun `parses German thousands separator correctly`() {
        assertThat(AmountParser.parseSingleAmountToCents("1.234,56 €")).isEqualTo(123456L)
    }

    @Test
    fun `parses simple German decimal`() {
        assertThat(AmountParser.parseSingleAmountToCents("702,61 €")).isEqualTo(70261L)
    }

    @Test
    fun `parses ambiguous single dot as decimal separator per OCR_RULES example`() {
        assertThat(AmountParser.parseSingleAmountToCents("€4.22")).isEqualTo(422L)
    }

    @Test
    fun `extracts the only amount candidate in text`() {
        val candidate = AmountParser.extractAmount("Rechnung\nBetrag: 49,99 €\nDanke")
        assertThat(candidate).isNotNull()
        assertThat(candidate!!.amountCents).isEqualTo(4999L)
    }

    // TC06 - OCR unsicher: kein erfundener Betrag bei mehreren widersprüchlichen Kandidaten.
    @Test
    fun `returns null when multiple conflicting amounts without a total label`() {
        val candidate = AmountParser.extractAmount("Artikel A 12,00 €\nArtikel B 8,50 €")
        assertThat(candidate).isNull()
    }

    @Test
    fun `returns null when no amount is present`() {
        assertThat(AmountParser.extractAmount("Kein Betrag hier")).isNull()
    }

    @Test
    fun `picks amount labelled as total among multiple candidates`() {
        val candidate = AmountParser.extractAmount("Artikel A 12,00 €\nArtikel B 8,50 €\nGesamt: 20,50 €")
        assertThat(candidate).isNotNull()
        assertThat(candidate!!.amountCents).isEqualTo(2050L)
    }

    @Test
    fun `frozen payout example line parses to 702,61 EUR`() {
        val text = "Provision wurde eingefroren\nVerfügbare Auszahlung 702,61 €\nAbheben nicht möglich"
        val candidate = AmountParser.extractAmount(text)
        assertThat(candidate).isNotNull()
        assertThat(candidate!!.amountCents).isEqualTo(70261L)
    }
}
