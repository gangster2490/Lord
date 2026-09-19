package de.tiktokshop.buchhaltung.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class GermanDateParserTest {

    @Test
    fun `parses German dd-mm-yyyy date`() {
        assertThat(GermanDateParser.extractDate("Belegdatum: 20.09.2026")).isEqualTo(LocalDate.of(2026, 9, 20))
    }

    @Test
    fun `parses ISO date`() {
        assertThat(GermanDateParser.extractDate("date: 2026-09-19 receipt")).isEqualTo(LocalDate.of(2026, 9, 19))
    }

    @Test
    fun `parses two digit year as 20xx`() {
        assertThat(GermanDateParser.extractDate("19.09.26")).isEqualTo(LocalDate.of(2026, 9, 19))
    }

    @Test
    fun `returns null when no date present`() {
        assertThat(GermanDateParser.extractDate("Kein Datum hier")).isNull()
    }
}
