package de.tiktokshop.buchhaltung.ocr

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import org.junit.Test

class StatusClassifierTest {

    // TC01 - Eingefrorene TikTok-Provision: darf NICHT als PAID_OUT erkannt werden,
    // obwohl der Text auch das Wort "verfügbar" (als Label) enthält.
    @Test
    fun `frozen screenshot with available label is classified as FROZEN not PAID_OUT`() {
        val text = "Provision wurde eingefroren\nVerfügbare Auszahlung 702,61 €\nAbheben nicht möglich"
        val result = StatusClassifier.classify(text)
        assertThat(result.status).isEqualTo(IncomeStatus.FROZEN)
        assertThat(result.status).isNotEqualTo(IncomeStatus.PAID_OUT)
    }

    @Test
    fun `english withdrawal unavailable is classified as FROZEN`() {
        val result = StatusClassifier.classify("Your commission is visible but withdrawal unavailable")
        assertThat(result.status).isEqualTo(IncomeStatus.FROZEN)
    }

    @Test
    fun `payout completed is classified as PAID_OUT`() {
        val result = StatusClassifier.classify("Auszahlung erfolgt: payout completed am 20.09.2026")
        assertThat(result.status).isEqualTo(IncomeStatus.PAID_OUT)
    }

    @Test
    fun `verfuegbar without frozen indicators is classified as AVAILABLE`() {
        val result = StatusClassifier.classify("Ihre Provision ist jetzt verfügbar zur Auszahlung")
        assertThat(result.status).isEqualTo(IncomeStatus.AVAILABLE)
    }

    @Test
    fun `cancelled commission is classified as REVERSED even if also mentions frozen`() {
        val result = StatusClassifier.classify("cancelled commission - vorher eingefroren")
        assertThat(result.status).isEqualTo(IncomeStatus.REVERSED)
    }

    @Test
    fun `plain visible commission without indicators defaults to ACCRUED with low confidence`() {
        val result = StatusClassifier.classify("Provision: 15,00 € - TikTok Shop")
        assertThat(result.status).isEqualTo(IncomeStatus.ACCRUED)
        assertThat(result.confidence).isLessThan(0.75f)
    }

    @Test
    fun `refunded is classified as REFUNDED`() {
        val result = StatusClassifier.classify("Provision wurde rückerstattet")
        assertThat(result.status).isEqualTo(IncomeStatus.REFUNDED)
    }

    @Test
    fun `english refunded keyword is classified as REFUNDED`() {
        val result = StatusClassifier.classify("Your commission was refunded")
        assertThat(result.status).isEqualTo(IncomeStatus.REFUNDED)
    }
}
