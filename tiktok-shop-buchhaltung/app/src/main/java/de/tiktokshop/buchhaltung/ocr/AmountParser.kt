package de.tiktokshop.buchhaltung.ocr

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.toCents
import java.math.BigDecimal

/** Ergebnis der Betragserkennung inkl. Confidence. Kein Betrag = keine Vermutung (OCR_RULES.md: "Bei Unsicherheit Feld leer lassen"). */
data class AmountCandidate(val amountCents: Cents, val confidence: Float, val rawText: String)

/**
 * Erkennt deutsche und englische Dezimalschreibweisen aus OCR-Text und wandelt sie in
 * Cent-Beträge um. Rät nie, wenn der Betrag mehrdeutig ist (TC06).
 */
object AmountParser {

    // z. B. "702,61", "1.234,56", "€4.22", "49.99", "1,234.56"
    private val NUMBER_PATTERN = Regex("""[€]?\s?(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?)\s?€?""")
    private val TOTAL_KEYWORDS = Regex("""(gesamt|total|summe|betrag|verfügbare auszahlung|amount)""", RegexOption.IGNORE_CASE)

    // Datumsangaben (TT.MM.JJJJ / JJJJ-MM-TT) stehen in Transaktionslisten oft auf derselben
    // Zeile wie der Betrag (z. B. "03.07.2026 10,99 €"). Ohne diesen Strip hält die Suche das
    // Datum für einen zweiten, widersprüchlichen Betragskandidaten und gibt (korrekt, aber
    // unnötig) null zurück, statt den eindeutigen Betrag zu erkennen.
    private val DATE_LIKE = Regex("""\b\d{1,2}\.\d{1,2}\.\d{2,4}\b|\b\d{4}-\d{2}-\d{2}\b""")

    /** Parst genau eine bekannte Zahl-Zeichenkette (ohne Mehrdeutigkeits-Suche). Wirft bei unparsbarem Text. */
    fun parseSingleAmountToCents(raw: String): Cents {
        val cleaned = raw.trim().removePrefix("€").removeSuffix("€").trim()
        val normalized = normalizeToDecimalString(cleaned)
        return BigDecimal(normalized).toCents()
    }

    /**
     * Sucht im OCR-Rohtext nach Geldbeträgen. Liefert nur dann ein Ergebnis, wenn
     * genau ein Betrag vorkommt, oder wenn bei mehreren Kandidaten einer eindeutig
     * durch ein "Gesamt/Summe/Betrag"-Schlüsselwort in derselben Zeile ausgezeichnet ist.
     * Andernfalls null, damit die App nichts erfindet.
     */
    fun extractAmount(text: String): AmountCandidate? {
        val lines = text.replace(DATE_LIKE, " ").lines()
        val candidates = mutableListOf<Pair<String, String>>() // raw match, containing line

        for (line in lines) {
            for (match in NUMBER_PATTERN.findAll(line)) {
                val numeric = match.groupValues[1]
                if (looksLikeAmount(numeric)) {
                    candidates += numeric to line
                }
            }
        }

        if (candidates.isEmpty()) return null

        val distinctValues = candidates.map { it.first }.distinct()
        if (distinctValues.size == 1) {
            val raw = distinctValues.first()
            return runCatching {
                AmountCandidate(parseSingleAmountToCents(raw), confidence = 0.95f, rawText = raw)
            }.getOrNull()
        }

        val labelled = candidates.filter { (_, line) -> TOTAL_KEYWORDS.containsMatchIn(line) }
        val labelledValues = labelled.map { it.first }.distinct()
        if (labelledValues.size == 1) {
            val raw = labelledValues.first()
            return runCatching {
                AmountCandidate(parseSingleAmountToCents(raw), confidence = 0.75f, rawText = raw)
            }.getOrNull()
        }

        // Mehrere widersprüchliche Beträge ohne eindeutiges Label: nicht raten.
        return null
    }

    private fun looksLikeAmount(numeric: String): Boolean {
        // Reine 1-2-stellige Jahreszahlen o.ä. ohne Trennzeichen sind selten gemeinte Beträge,
        // aber wir überlassen die Plausibilisierung bewusst dem Nutzer (Bestätigungsscreen).
        return numeric.any { it.isDigit() }
    }

    /**
     * Normalisiert eine Zahl-Zeichenkette mit gemischten Tausender-/Dezimaltrennzeichen
     * in einen kanonischen Dezimalstring mit '.' als Dezimaltrennzeichen.
     */
    internal fun normalizeToDecimalString(numeric: String): String {
        val hasComma = numeric.contains(',')
        val hasDot = numeric.contains('.')

        return when {
            hasComma && hasDot -> {
                val lastComma = numeric.lastIndexOf(',')
                val lastDot = numeric.lastIndexOf('.')
                if (lastComma > lastDot) {
                    // Deutsch: Punkt = Tausender, Komma = Dezimal. "1.234,56" -> "1234.56"
                    numeric.replace(".", "").replace(',', '.')
                } else {
                    // Englisch: Komma = Tausender, Punkt = Dezimal. "1,234.56" -> "1234.56"
                    numeric.replace(",", "")
                }
            }
            hasComma -> {
                // Nur Komma vorhanden: deutsches Dezimaltrennzeichen. "702,61" -> "702.61"
                numeric.replace('.', ' ').trim().replace(',', '.')
            }
            hasDot -> {
                val afterLastDot = numeric.substringAfterLast('.')
                val dotCount = numeric.count { it == '.' }
                if (dotCount == 1 && afterLastDot.length <= 2) {
                    // Einzelner Punkt mit 1-2 Nachkommastellen: Dezimaltrennzeichen. "4.22" -> "4.22"
                    numeric
                } else {
                    // Mehrere Punkte oder 3 Nachkommastellen: Tausendertrennzeichen. "1.234" -> "1234"
                    numeric.replace(".", "")
                }
            }
            else -> numeric
        }
    }
}
