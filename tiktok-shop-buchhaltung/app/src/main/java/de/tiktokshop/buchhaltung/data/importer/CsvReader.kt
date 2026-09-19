package de.tiktokshop.buchhaltung.data.importer

import java.io.InputStream

/**
 * Minimaler, RFC4180-naher CSV-Parser für den [UniversalSpreadsheetImporter] (§2). Erkennt das
 * Trennzeichen automatisch (Komma oder Semikolon - deutsche Exporte aus Excel/Banking-Apps
 * verwenden meist Semikolon, weil das Komma als Dezimaltrennzeichen belegt ist), unterstützt
 * angeführte Felder mit eingebetteten Trennzeichen/Zeilenumbrüchen sowie `""`-Escaping, und
 * entfernt ein eventuelles UTF-8-BOM am Dateianfang.
 */
object CsvReader {

    /** Liest die gesamte CSV in Zeilen von Zellwerten ein - analog zu [de.tiktokshop.buchhaltung.data.xlsx.XlsxRow]. */
    fun read(input: InputStream): List<List<String>> {
        val text = input.readBytes().toString(Charsets.UTF_8).removePrefix("﻿")
        if (text.isBlank()) return emptyList()

        val delimiter = detectDelimiter(text)
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            currentRow += field.toString()
            field.clear()
        }

        fun endRow() {
            endField()
            rows += currentRow.toList()
            currentRow.clear()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == delimiter -> endField()
                c == '\r' -> Unit // Zeilenumbruch wird über '\n' behandelt (CRLF und LF)
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        // Letzte Zeile hat evtl. keinen abschließenden Zeilenumbruch.
        if (field.isNotEmpty() || currentRow.isNotEmpty()) endRow()

        return rows.filterNot { row -> row.size == 1 && row[0].isBlank() }
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val semicolons = firstLine.count { it == ';' }
        val commas = firstLine.count { it == ',' }
        return if (semicolons > commas) ';' else ','
    }
}
