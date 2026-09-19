package de.tiktokshop.buchhaltung.data.importer

import de.tiktokshop.buchhaltung.data.model.Cents
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.xlsx.XlsxRow
import java.time.LocalDate

/**
 * Eine aus einer importierten Tabelle (Excel/CSV) erkannte Buchung, bevor sie als
 * [de.tiktokshop.buchhaltung.data.model.IncomeEntry] oder
 * [de.tiktokshop.buchhaltung.data.model.ExpenseEntry] gespeichert wird. Vereinheitlicht die
 * TikTok-Earnings- und die generische Ausgaben-Tabellenform (§1-6).
 */
data class ImportCandidateRow(
    val type: EntryType,
    val date: LocalDate,
    val amountCents: Cents,
    val currency: String,
    val merchant: String?,
    val category: String?,
    val externalTransactionId: String? = null,
    val externalEarningType: String? = null,
    val payer: String? = null,
    val payerCountry: String? = null,
    val platformExpenseCents: Cents? = null,
    val sourceSheet: String,
    val sourceRowNumber: Int,
    /**
     * true, wenn [type] nur aus dem Vorzeichen einer generischen "Betrag"-Spalte geraten wurde
     * (kein expliziter Income-/Expense-/Category-Hinweis in der Datei) - die UI zeigt dem
     * Nutzer diese Zeilen mit einer Einnahme/Ausgabe-Umschaltmöglichkeit (§3: "immer den
     * erkannten Typ anzeigen, Nutzer kann überschreiben").
     */
    val isAmbiguousType: Boolean = false,
    /** Aus einer "Geschäftlich (%)"-Spalte, falls vorhanden - sonst 100% (voll geschäftlich). */
    val businessPercent: Int? = null,
    val status: String? = null,
    val source: String? = null,
)

data class ImportRowError(val sourceSheet: String, val rowNumber: Int, val message: String)

/**
 * Ergebnis der Kopfzeilen-Analyse eines einzelnen Sheets/einer CSV-Tabelle. [isConfident] ist
 * nur dann true, wenn Datum UND mindestens ein Betragsfeld eindeutig erkannt wurden - sonst
 * zeigt die UI den "Spalten zuordnen"-Screen (§4) statt automatisch (evtl. falsch) zu
 * extrahieren.
 */
data class SheetAnalysis(
    val sheetName: String,
    val headerRowIndex: Int,
    val headers: List<String>,
    val columnMapping: Map<ColumnRole, Int>,
    val isConfident: Boolean,
)

data class UniversalImportResult(
    val filename: String,
    val sheets: List<SheetAnalysis>,
    val rows: List<ImportCandidateRow>,
    val errors: List<ImportRowError>,
    /** Rohzeilen je Sheet - ermöglicht dem "Spalten zuordnen"-Screen (§4), nach einer manuellen
     *  Zuordnung ohne erneutes Datei-Parsing neu zu extrahieren. */
    val sheetsData: Map<String, List<XlsxRow>>,
) {
    /** true, wenn nichts automatisch extrahiert werden konnte, aber mind. ein Sheet Tabellendaten hat. */
    val needsColumnMapping: Boolean get() = rows.isEmpty() && sheets.any { !it.isConfident }
}
