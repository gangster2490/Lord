package de.tiktokshop.buchhaltung.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransactionGrouperTest {

    private fun line(text: String, top: Int, bottom: Int) = RecognizedLine(text, top, bottom, left = 0)

    // Beispiel aus der Spezifikation: 3 Transaktionen auf einem Screenshot müssen als
    // 3 getrennte Blöcke erkannt werden, nicht als ein einziger Beleg.
    @Test
    fun `groups three visually separated transactions into three blocks`() {
        val lines = listOf(
            line("Google AI Pro", top = 0, bottom = 20),
            line("03.07.2026   10,99 €", top = 24, bottom = 44),
            line("ChatGPT Plus", top = 120, bottom = 140),
            line("02.07.2026   21,99 €", top = 144, bottom = 164),
            line("TikTok Multi Quantity", top = 240, bottom = 260),
            line("08.07.2026   2,00 €", top = 264, bottom = 284),
        )

        val groups = TransactionGrouper.group(lines)

        assertThat(groups).hasSize(3)
        assertThat(groups[0].map { it.text }).containsExactly("Google AI Pro", "03.07.2026   10,99 €").inOrder()
        assertThat(groups[1].map { it.text }).containsExactly("ChatGPT Plus", "02.07.2026   21,99 €").inOrder()
        assertThat(groups[2].map { it.text }).containsExactly("TikTok Multi Quantity", "08.07.2026   2,00 €").inOrder()
    }

    @Test
    fun `lines close together stay in a single group`() {
        val lines = listOf(
            line("TikTok Shop", top = 0, bottom = 20),
            line("Standard commission", top = 22, bottom = 42),
            line("03.07.2026   5,00 €", top = 44, bottom = 64),
        )

        val groups = TransactionGrouper.group(lines)

        assertThat(groups).hasSize(1)
        assertThat(groups[0]).hasSize(3)
    }

    @Test
    fun `empty input yields no groups`() {
        assertThat(TransactionGrouper.group(emptyList())).isEmpty()
    }
}
