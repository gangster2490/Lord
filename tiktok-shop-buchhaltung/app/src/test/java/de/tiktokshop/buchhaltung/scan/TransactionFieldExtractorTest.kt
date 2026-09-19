package de.tiktokshop.buchhaltung.scan

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.ocr.RecognizedLine
import org.junit.Test
import java.time.LocalDate

class TransactionFieldExtractorTest {

    private fun line(text: String) = RecognizedLine(text, top = 0, bottom = 20, left = 0)

    // Beispiel aus der Spezifikation: Google AI Pro / ChatGPT Plus / TikTok Multi Quantity
    // müssen als 3 separate, korrekt kategorisierte Ausgaben erkannt werden.
    @Test
    fun `Google AI Pro block extracts as AI-Dienste expense`() {
        val candidate = TransactionFieldExtractor.extract(listOf(line("Google AI Pro"), line("03.07.2026 10,99 €")))

        requireNotNull(candidate)
        assertThat(candidate.entryType).isEqualTo(EntryType.EXPENSE)
        assertThat(candidate.merchant).isEqualTo("Google AI Pro")
        assertThat(candidate.category).isEqualTo(ExpenseCategory.AI_SERVICES)
        assertThat(candidate.amountCents).isEqualTo(1099L)
        assertThat(candidate.date).isEqualTo(LocalDate.of(2026, 7, 3))
    }

    @Test
    fun `ChatGPT Plus block extracts as AI-Dienste expense`() {
        val candidate = TransactionFieldExtractor.extract(listOf(line("ChatGPT Plus"), line("02.07.2026 21,99 €")))

        requireNotNull(candidate)
        assertThat(candidate.merchant).isEqualTo("ChatGPT Plus")
        assertThat(candidate.category).isEqualTo(ExpenseCategory.AI_SERVICES)
        assertThat(candidate.amountCents).isEqualTo(2199L)
    }

    @Test
    fun `TikTok Multi Quantity block extracts as Werbung expense`() {
        val candidate = TransactionFieldExtractor.extract(listOf(line("TikTok Multi Quantity"), line("08.07.2026 2,00 €")))

        requireNotNull(candidate)
        assertThat(candidate.merchant).isEqualTo("TikTok Multi Quantity")
        assertThat(candidate.category).isEqualTo(ExpenseCategory.ADVERTISING)
        assertThat(candidate.amountCents).isEqualTo(200L)
    }

    @Test
    fun `TikTok Shop commission block extracts as income with Provision type`() {
        val candidate = TransactionFieldExtractor.extract(
            listOf(line("TikTok Shop"), line("Standard commission"), line("03.07.2026 5,00 €")),
        )

        requireNotNull(candidate)
        assertThat(candidate.entryType).isEqualTo(EntryType.INCOME)
        assertThat(candidate.incomeType).isEqualTo("Provision")
        assertThat(candidate.amountCents).isEqualTo(500L)
    }

    // Kernproblem aus dem Bugreport: ein Monatslabel aus einem Balkendiagramm darf niemals
    // als Händlername interpretiert werden.
    @Test
    fun `month label is never used as merchant name`() {
        val candidate = TransactionFieldExtractor.extract(listOf(line("Juli"), line("10,99 €")))

        requireNotNull(candidate)
        assertThat(candidate.merchant).isNull()
        assertThat(candidate.needsReview).isTrue()
    }

    @Test
    fun `group without any amount yields no candidate`() {
        val candidate = TransactionFieldExtractor.extract(listOf(line("Budget & Verlauf"), line("Juli")))
        assertThat(candidate).isNull()
    }
}
