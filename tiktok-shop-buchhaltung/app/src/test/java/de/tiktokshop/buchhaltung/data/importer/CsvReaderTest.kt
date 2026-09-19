package de.tiktokshop.buchhaltung.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvReaderTest {

    private fun parse(text: String) = CsvReader.read(text.byteInputStream(Charsets.UTF_8))

    @Test
    fun `parses comma-separated CSV with header`() {
        val rows = parse("Datum,Betrag,Haendler\n01.01.2026,12.50,Canva\n")
        assertThat(rows).containsExactly(
            listOf("Datum", "Betrag", "Haendler"),
            listOf("01.01.2026", "12.50", "Canva"),
        )
    }

    @Test
    fun `auto-detects semicolon delimiter (common in German exports)`() {
        val rows = parse("Datum;Betrag;Haendler\n01.01.2026;12,50;Canva\n")
        assertThat(rows).containsExactly(
            listOf("Datum", "Betrag", "Haendler"),
            listOf("01.01.2026", "12,50", "Canva"),
        )
    }

    @Test
    fun `handles quoted fields with embedded delimiter and escaped quotes`() {
        val rows = parse("Datum,Beschreibung\n01.01.2026,\"Kauf, \"\"Sonderangebot\"\"\"\n")
        assertThat(rows[1]).containsExactly("01.01.2026", "Kauf, \"Sonderangebot\"").inOrder()
    }

    @Test
    fun `strips UTF-8 BOM`() {
        val rows = parse("﻿Datum,Betrag\n01.01.2026,1.00\n")
        assertThat(rows.first().first()).isEqualTo("Datum")
    }

    @Test
    fun `handles last line without trailing newline`() {
        val rows = parse("Datum,Betrag\n01.01.2026,1.00")
        assertThat(rows).hasSize(2)
        assertThat(rows[1]).containsExactly("01.01.2026", "1.00").inOrder()
    }

    @Test
    fun `empty input yields no rows`() {
        assertThat(parse("")).isEmpty()
    }
}
