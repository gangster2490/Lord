package de.tiktokshop.buchhaltung.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeaderMatcherTest {

    @Test
    fun `matches German headers with diacritics and punctuation`() {
        val mapping = HeaderMatcher.matchColumns(listOf("Datum", "Betrag (€)", "Händler / Dienst", "Kategorie", "Währung"))
        assertThat(mapping[ColumnRole.DATE]).isEqualTo(0)
        assertThat(mapping[ColumnRole.AMOUNT_GENERIC]).isEqualTo(1)
        assertThat(mapping[ColumnRole.MERCHANT]).isEqualTo(2)
        assertThat(mapping[ColumnRole.CATEGORY]).isEqualTo(3)
        assertThat(mapping[ColumnRole.CURRENCY]).isEqualTo(4)
    }

    @Test
    fun `matches English TikTok-style headers`() {
        val mapping = HeaderMatcher.matchColumns(
            listOf("Date (UTC+0)", "Transaction ID", "Type of earnings", "Currency", "Income", "Expense"),
        )
        assertThat(mapping[ColumnRole.DATE]).isEqualTo(0)
        assertThat(mapping[ColumnRole.TRANSACTION_ID]).isEqualTo(1)
        assertThat(mapping[ColumnRole.CATEGORY]).isEqualTo(2)
        assertThat(mapping[ColumnRole.CURRENCY]).isEqualTo(3)
        assertThat(mapping[ColumnRole.AMOUNT_INCOME]).isEqualTo(4)
        assertThat(mapping[ColumnRole.AMOUNT_EXPENSE]).isEqualTo(5)
    }

    @Test
    fun `is case- and whitespace-insensitive`() {
        val mapping = HeaderMatcher.matchColumns(listOf("  DATUM  ", "AMOUNT"))
        assertThat(mapping[ColumnRole.DATE]).isEqualTo(0)
        assertThat(mapping[ColumnRole.AMOUNT_GENERIC]).isEqualTo(1)
    }

    @Test
    fun `unknown headers are not mapped`() {
        val mapping = HeaderMatcher.matchColumns(listOf("Foo", "Bar"))
        assertThat(mapping).isEmpty()
    }

    @Test
    fun `each role maps to at most one column`() {
        val mapping = HeaderMatcher.matchColumns(listOf("Date", "Transaction Date", "Amount"))
        // "Date" gewinnt (linkeste Spalte), "Transaction Date" bleibt unzugeordnet.
        assertThat(mapping[ColumnRole.DATE]).isEqualTo(0)
        assertThat(mapping.values.toSet()).hasSize(mapping.size)
    }
}
