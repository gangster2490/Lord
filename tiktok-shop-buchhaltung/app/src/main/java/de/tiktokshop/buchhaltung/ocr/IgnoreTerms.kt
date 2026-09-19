package de.tiktokshop.buchhaltung.ocr

/**
 * Zeilen, die niemals als Händler/Quelle einer Transaktion interpretiert werden dürfen -
 * Monatsnamen (Diagramm-/Kalenderachsen), App-eigene Überschriften und Tabellenkopf-Reste
 * aus "Budget & Verlauf"-Screenshots. Ohne diese Liste hätte z. B. "Juli" als Zeilen-Label
 * einer Balkengrafik fälschlich als Händlername gegolten.
 */
object IgnoreTerms {

    private val MONTHS = setOf(
        "januar", "februar", "märz", "april", "mai", "juni",
        "juli", "august", "september", "oktober", "november", "dezember",
    )

    private val HEADERS = setOf(
        "budget & verlauf", "budget und verlauf", "sheet1", "fields explanation",
    )

    private val ALL = MONTHS + HEADERS

    /** true, wenn diese Zeile niemals als Händler-/Quellenname verwendet werden darf. */
    fun isIgnoredMerchantLine(line: String): Boolean {
        val normalized = line.trim().lowercase().trimEnd('.', ':')
        if (normalized.isBlank()) return true
        return ALL.contains(normalized)
    }
}
