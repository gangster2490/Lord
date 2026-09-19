package de.tiktokshop.buchhaltung.ai.dto

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.scan.RecognitionSource
import org.junit.Test
import java.time.LocalDate

class AiVisionMapperTest {

    // Beispiel aus der Spezifikation.
    @Test
    fun `maps a complete expense DTO`() {
        val dto = AiVisionTransactionDto(
            date = "2026-07-03",
            merchant = "Google AI Pro",
            amount = 10.99,
            currency = "EUR",
            type = "expense",
            category = "AI-Dienste",
            businessPercentage = 100,
            status = "normal",
            confidence = 0.98f,
        )

        val candidate = AiVisionMapper.toDomain(dto)

        assertThat(candidate.entryType).isEqualTo(EntryType.EXPENSE)
        assertThat(candidate.date).isEqualTo(LocalDate.of(2026, 7, 3))
        assertThat(candidate.merchant).isEqualTo("Google AI Pro")
        assertThat(candidate.amountCents).isEqualTo(1099L)
        assertThat(candidate.category).isEqualTo(ExpenseCategory.AI_SERVICES)
        assertThat(candidate.businessUsePercent).isEqualTo(100)
        assertThat(candidate.confidence).isEqualTo(0.98f)
        assertThat(candidate.source).isEqualTo(RecognitionSource.AI_VISION)
    }

    // "AI не должен придумывать отсутствующие данные" - fehlende Felder bleiben null.
    @Test
    fun `missing fields stay null instead of being invented`() {
        val dto = AiVisionTransactionDto(merchant = "Unbekannt", type = "expense")

        val candidate = AiVisionMapper.toDomain(dto)

        assertThat(candidate.date).isNull()
        assertThat(candidate.amountCents).isNull()
        assertThat(candidate.category).isNull()
        assertThat(candidate.needsReview).isTrue()
    }

    @Test
    fun `unparsable date does not crash and stays null`() {
        val dto = AiVisionTransactionDto(date = "not-a-date", type = "expense")
        assertThat(AiVisionMapper.toDomain(dto).date).isNull()
    }

    @Test
    fun `income type is inferred from a recognised status when type is missing`() {
        val dto = AiVisionTransactionDto(status = "frozen", amount = 5.0)
        val candidate = AiVisionMapper.toDomain(dto)
        assertThat(candidate.entryType).isEqualTo(EntryType.INCOME)
    }

    @Test
    fun `unknown category string does not crash and stays null`() {
        val dto = AiVisionTransactionDto(type = "expense", category = "Voellig Unbekannt")
        assertThat(AiVisionMapper.toDomain(dto).category).isNull()
    }
}
