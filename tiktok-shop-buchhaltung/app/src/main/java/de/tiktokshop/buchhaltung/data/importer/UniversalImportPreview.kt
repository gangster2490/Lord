package de.tiktokshop.buchhaltung.data.importer

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.SourceDocument
import de.tiktokshop.buchhaltung.data.xlsx.XlsxRow
import java.time.LocalDate

/**
 * Voranalyse einer über "Datei / Beleg importieren" (§1) gewählten Excel/CSV-Datei, BEVOR
 * irgendetwas gespeichert wird. Zeigt dem Nutzer immer den erkannten Typ (Einnahme/Ausgabe je
 * Zeile) statt ihn vorher festlegen zu lassen (§3) - siehe "Import prüfen"-Screen (§5).
 */
data class UniversalImportPreview(
    val filename: String,
    val fileHash: String,
    val fileBytes: ByteArray,
    val existingSourceDocument: SourceDocument?,
    val sheets: List<SheetAnalysis>,
    val newRows: List<ImportCandidateRow>,
    val duplicateRows: List<ImportCandidateRow>,
    val parseErrors: List<ImportRowError>,
    val sheetsData: Map<String, List<XlsxRow>> = emptyMap(),
) {
    val newIncomeCount: Int get() = newRows.count { it.type == EntryType.INCOME }
    val newExpenseCount: Int get() = newRows.count { it.type == EntryType.EXPENSE }
    val duplicateCount: Int get() = duplicateRows.size
    val totalNewIncomeCents: Cents get() = newRows.filter { it.type == EntryType.INCOME }.sumOf { it.amountCents }
    val totalNewExpenseCents: Cents get() = newRows.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountCents }

    /** Kein einziges Sheet konnte automatisch zugeordnet werden - Nutzer muss Spalten zuordnen (§4). */
    val needsColumnMapping: Boolean get() = newRows.isEmpty() && duplicateRows.isEmpty() && sheets.any { !it.isConfident }

    val periodRange: Pair<LocalDate, LocalDate>? get() {
        val dates = (newRows + duplicateRows).map { it.date }
        return if (dates.isEmpty()) null else dates.min() to dates.max()
    }
}
