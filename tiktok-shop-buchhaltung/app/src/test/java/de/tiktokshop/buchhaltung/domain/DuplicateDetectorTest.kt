package de.tiktokshop.buchhaltung.domain

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import org.junit.Test
import java.time.LocalDate

class DuplicateDetectorTest {

    // TC04 - Dublette: gleicher Betrag, Datum, Händler/Plattform und Bild-Hash.
    @Test
    fun `finds duplicate with same date amount platform and image hash`() {
        val date = LocalDate.of(2026, 9, 20)
        val existing = IncomeEntry(
            date = date,
            platform = "TikTok Shop",
            incomeType = "Provision",
            amountCents = 70261,
            status = IncomeStatus.FROZEN,
            imageHash = "hash-abc",
        )

        val duplicate = DuplicateDetector.findIncomeDuplicate(
            date = date,
            amountCents = 70261,
            platform = "TikTok Shop",
            imageHash = "hash-abc",
            existing = listOf(existing),
        )

        assertThat(duplicate).isNotNull()
    }

    @Test
    fun `does not flag entries with different amount as duplicate`() {
        val date = LocalDate.of(2026, 9, 20)
        val existing = IncomeEntry(
            date = date,
            platform = "TikTok Shop",
            incomeType = "Provision",
            amountCents = 70261,
            status = IncomeStatus.FROZEN,
        )

        val duplicate = DuplicateDetector.findIncomeDuplicate(
            date = date,
            amountCents = 5000,
            platform = "TikTok Shop",
            imageHash = null,
            existing = listOf(existing),
        )

        assertThat(duplicate).isNull()
    }
}
