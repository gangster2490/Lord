package de.tiktokshop.buchhaltung.data.importer

import java.text.Normalizer

/** Rolle, die eine Spalte in einer importierten Tabelle einnehmen kann (§2/§4 "Spalten zuordnen"). */
enum class ColumnRole {
    DATE,
    AMOUNT_INCOME,
    AMOUNT_EXPENSE,
    AMOUNT_GENERIC,
    MERCHANT,
    CATEGORY,
    CURRENCY,
    TRANSACTION_ID,
}

/**
 * Ordnet Kopfzeilen-Texte (Deutsch oder Englisch, beliebige Groß-/Kleinschreibung und
 * Interpunktion) den bekannten [ColumnRole]s zu (§2). Toleranz-Strategie:
 * 1. Normalisieren: Unicode-Diakritika entfernen (Ä->A, damit "Händler"/"Handler" gleich
 *    behandelt werden), klein schreiben, Satzzeichen/Sonderzeichen durch Leerzeichen ersetzen,
 *    Mehrfach-/Rand-Leerzeichen trimmen.
 * 2. Exakter Treffer gegen die Synonymliste je Rolle.
 * 3. Fallback: "enthält" (z. B. "Transaction Date" enthält normalisiert "date").
 * Eine Spalte kann nur EINER Rolle zugeordnet werden; bei mehreren passenden Spalten für
 * dieselbe Rolle gewinnt die erste (linkeste) Spalte.
 */
object HeaderMatcher {

    private val SYNONYMS: Map<ColumnRole, List<String>> = mapOf(
        ColumnRole.DATE to listOf(
            "datum", "date", "belegdatum", "transaction date", "date utc 0", "buchungsdatum",
        ),
        ColumnRole.AMOUNT_INCOME to listOf(
            "income", "einnahme", "einnahmen",
        ),
        ColumnRole.AMOUNT_EXPENSE to listOf(
            "expense", "ausgabe", "ausgaben",
        ),
        ColumnRole.AMOUNT_GENERIC to listOf(
            "betrag", "betrag eur", "amount", "gesamtbetrag", "total", "gross amount", "summe",
        ),
        ColumnRole.MERCHANT to listOf(
            "handler", "handler dienst", "merchant", "vendor", "anbieter", "payer", "payee",
        ),
        ColumnRole.CATEGORY to listOf(
            "kategorie", "category", "type", "type of earnings",
        ),
        ColumnRole.CURRENCY to listOf(
            "wahrung", "currency",
        ),
        ColumnRole.TRANSACTION_ID to listOf(
            "transaction id", "id", "external id", "belegnummer",
        ),
    )

    /** Normalisiert einen Kopfzeilentext für den toleranten Vergleich - siehe Klassenkommentar. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val withoutDiacritics = decomposed.replace(Regex("\\p{M}"), "")
        return withoutDiacritics
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Ordnet jede Kopfzellen-Spalte höchstens einer [ColumnRole] zu. Reihenfolge der Rollen
     * ist bewusst so gewählt, dass spezifischere Rollen (AMOUNT_INCOME/AMOUNT_EXPENSE) vor der
     * generischen AMOUNT_GENERIC geprüft werden, damit ein TikTok-Report mit "Income"+"Expense"
     * nicht fälschlich beide als AMOUNT_GENERIC einordnet.
     */
    fun matchColumns(headerRow: List<String>): Map<ColumnRole, Int> {
        val normalizedHeaders = headerRow.map { normalize(it) }
        val result = linkedMapOf<ColumnRole, Int>()
        val roleOrder = listOf(
            ColumnRole.DATE,
            ColumnRole.TRANSACTION_ID,
            ColumnRole.AMOUNT_INCOME,
            ColumnRole.AMOUNT_EXPENSE,
            ColumnRole.AMOUNT_GENERIC,
            ColumnRole.CURRENCY,
            ColumnRole.MERCHANT,
            ColumnRole.CATEGORY,
        )
        val takenColumns = mutableSetOf<Int>()

        for (role in roleOrder) {
            val synonyms = SYNONYMS.getValue(role)
            val exactMatch = normalizedHeaders.withIndex().firstOrNull { (idx, header) ->
                idx !in takenColumns && header in synonyms
            }
            val match = exactMatch ?: normalizedHeaders.withIndex().firstOrNull { (idx, header) ->
                idx !in takenColumns && header.isNotBlank() && synonyms.any { syn -> header.contains(syn) }
            }
            if (match != null) {
                result[role] = match.index
                takenColumns += match.index
            }
        }
        return result
    }
}
