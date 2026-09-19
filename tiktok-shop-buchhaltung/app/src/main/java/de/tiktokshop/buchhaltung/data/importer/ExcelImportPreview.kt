package de.tiktokshop.buchhaltung.data.importer

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.SourceDocument
import java.time.LocalDate

/** Fehler beim Lesen/Parsen einer Import-Datei - wird dem Nutzer verständlich angezeigt (§33). */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Ergebnis der Voranalyse einer Excel-Datei VOR dem eigentlichen Speichern (§8: "X neue
 * Transaktionen / X bereits vorhanden / Gesamteinnahmen / Zeitraum"). Enthält die Rohbytes,
 * damit [ImportRepository.commit] die Datei nicht erneut vom (ggf. flüchtigen) `content://`-URI
 * lesen muss.
 */
data class ExcelImportPreview(
    val filename: String,
    val fileHash: String,
    val fileBytes: ByteArray,
    /** Falls eine Datei mit identischem Hash bereits früher importiert wurde. */
    val existingSourceDocument: SourceDocument?,
    val period: String?,
    val newRows: List<TikTokEarningsRow>,
    val duplicateRows: List<TikTokEarningsRow>,
    val parseErrors: List<TikTokEarningsRowError>,
) {
    val newTransactionCount: Int get() = newRows.size
    val duplicateCount: Int get() = duplicateRows.size
    val totalNewIncomeCents: Long get() = newRows.sumOf { it.grossAmountCents }
    val periodRange: Pair<LocalDate, LocalDate>? get() {
        val dates = (newRows + duplicateRows).map { it.transactionDate }
        return if (dates.isEmpty()) null else dates.min() to dates.max()
    }
}
