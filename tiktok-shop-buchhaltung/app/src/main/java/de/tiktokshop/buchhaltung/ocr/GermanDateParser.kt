package de.tiktokshop.buchhaltung.ocr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Erkennt Datumsangaben in deutschen (TT.MM.JJJJ) und ISO-Formaten (JJJJ-MM-TT) aus OCR-Text. */
object GermanDateParser {

    private val GERMAN_DATE = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b""")
    private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")

    /** Sucht die erste plausible Datumsangabe. Liefert null statt einer Vermutung, wenn keine gefunden wird. */
    fun extractDate(text: String): LocalDate? {
        ISO_DATE.find(text)?.let { match ->
            runCatching {
                return LocalDate.parse(match.value, DateTimeFormatter.ISO_LOCAL_DATE)
            }
        }
        GERMAN_DATE.find(text)?.let { match ->
            val (day, month, yearRaw) = match.destructured
            val year = if (yearRaw.length == 2) "20$yearRaw" else yearRaw
            return runCatching {
                LocalDate.of(year.toInt(), month.toInt(), day.toInt())
            }.getOrNull()
        }
        return null
    }

    fun parseOrNull(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
}
