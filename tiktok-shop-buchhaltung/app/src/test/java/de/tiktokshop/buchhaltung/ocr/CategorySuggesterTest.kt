package de.tiktokshop.buchhaltung.ocr

import com.google.common.truth.Truth.assertThat
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import org.junit.Test

class CategorySuggesterTest {

    @Test
    fun `ChatGPT Plus maps to AI-Dienste`() {
        assertThat(CategorySuggester.suggest("ChatGPT Plus")).isEqualTo(ExpenseCategory.AI_SERVICES)
    }

    @Test
    fun `Google AI Pro maps to AI-Dienste`() {
        assertThat(CategorySuggester.suggest("Google AI Pro")).isEqualTo(ExpenseCategory.AI_SERVICES)
    }

    @Test
    fun `Claude Pro maps to AI-Dienste`() {
        assertThat(CategorySuggester.suggest("Claude Pro")).isEqualTo(ExpenseCategory.AI_SERVICES)
    }

    @Test
    fun `Kling AI maps to AI-Dienste`() {
        assertThat(CategorySuggester.suggest("Kling AI")).isEqualTo(ExpenseCategory.AI_SERVICES)
    }

    @Test
    fun `CapCut maps to Video-Software not generic software`() {
        assertThat(CategorySuggester.suggest("CapCut")).isEqualTo(ExpenseCategory.VIDEO_EDITING_SOFTWARE)
    }

    @Test
    fun `TikTok Multi Quantity maps to Werbung`() {
        assertThat(CategorySuggester.suggest("TikTok Multi Quantity")).isEqualTo(ExpenseCategory.ADVERTISING)
    }

    @Test
    fun `TikTok Coins maps to Werbung`() {
        assertThat(CategorySuggester.suggest("TikTok Coins")).isEqualTo(ExpenseCategory.ADVERTISING)
    }
}

class IncomeTypeSuggesterTest {

    @Test
    fun `Standard commission maps to Provision`() {
        assertThat(IncomeTypeSuggester.suggest("TikTok Shop Standard commission")).isEqualTo("Provision")
    }

    @Test
    fun `Shop ads commission maps to Provision`() {
        assertThat(IncomeTypeSuggester.suggest("Shop ads commission")).isEqualTo("Provision")
    }

    @Test
    fun `Seller bonus maps to Bonus`() {
        assertThat(IncomeTypeSuggester.suggest("Seller bonus")).isEqualTo("Bonus")
    }

    @Test
    fun `Rewards maps to Rewards`() {
        assertThat(IncomeTypeSuggester.suggest("Rewards")).isEqualTo("Rewards")
    }

    @Test
    fun `unrelated text yields null`() {
        assertThat(IncomeTypeSuggester.suggest("Google AI Pro")).isNull()
    }
}
