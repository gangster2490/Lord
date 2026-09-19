package de.tiktokshop.buchhaltung.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExpenseEntryTest {

    // TC02 - Ausgabe mit Geschäftsanteil: 100 EUR brutto, 40% -> 40 EUR anrechenbar.
    @Test
    fun `computes business amount as percentage of gross amount`() {
        val businessAmount = ExpenseEntry.computeBusinessAmountCents(grossAmountCents = 10000, businessUsePercent = 40)
        assertThat(businessAmount).isEqualTo(4000L)
    }

    @Test
    fun `full business use keeps the full gross amount`() {
        val businessAmount = ExpenseEntry.computeBusinessAmountCents(grossAmountCents = 4999, businessUsePercent = 100)
        assertThat(businessAmount).isEqualTo(4999L)
    }

    @Test
    fun `zero business use results in zero`() {
        val businessAmount = ExpenseEntry.computeBusinessAmountCents(grossAmountCents = 6000, businessUsePercent = 0)
        assertThat(businessAmount).isEqualTo(0L)
    }
}
