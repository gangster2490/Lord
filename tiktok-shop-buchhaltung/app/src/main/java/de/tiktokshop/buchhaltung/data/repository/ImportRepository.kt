package de.tiktokshop.buchhaltung.data.repository

import android.content.Context
import android.net.Uri
import de.tiktokshop.buchhaltung.data.db.AppDatabase
import de.tiktokshop.buchhaltung.data.importer.ColumnRole
import de.tiktokshop.buchhaltung.data.importer.ExcelImportPreview
import de.tiktokshop.buchhaltung.data.importer.ImportCandidateRow
import de.tiktokshop.buchhaltung.data.importer.ImportException
import de.tiktokshop.buchhaltung.data.importer.ImportFileStorage
import de.tiktokshop.buchhaltung.data.importer.TikTokExcelParser
import de.tiktokshop.buchhaltung.data.importer.UniversalImportPreview
import de.tiktokshop.buchhaltung.data.importer.UniversalSpreadsheetImporter
import de.tiktokshop.buchhaltung.data.model.ActivityPhase
import de.tiktokshop.buchhaltung.data.model.EntryType
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.ImportBatch
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.model.SourceDocument
import de.tiktokshop.buchhaltung.data.model.SourceDocumentType
import de.tiktokshop.buchhaltung.data.xlsx.XlsxParseException
import de.tiktokshop.buchhaltung.ocr.CategorySuggester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val CSV_MIME_TYPE = "text/csv"

/**
 * Orchestriert den TikTok-Excel-Import (§7-9): liest + hasht die Datei, prüft Transaktions-
 * Dubletten per Transaction ID GEGEN DIE DATENBANK, bevor irgendetwas gespeichert wird
 * ([preview]), und persistiert erst nach expliziter Nutzerbestätigung ([commit]).
 */
class ImportRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val importFileStorage: ImportFileStorage,
) {
    private val sourceDocumentDao = database.sourceDocumentDao()
    private val importBatchDao = database.importBatchDao()
    private val incomeDao = database.incomeDao()
    private val expenseDao = database.expenseDao()

    fun observeImportHistory(): Flow<List<ImportBatch>> = importBatchDao.observeAll()

    suspend fun preview(uri: Uri, filename: String): ExcelImportPreview = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ImportException("Datei „$filename“ konnte nicht geöffnet werden.")
        } catch (e: IOException) {
            throw ImportException("Datei „$filename“ konnte nicht gelesen werden: ${e.message}", e)
        }
        if (bytes.isEmpty()) throw ImportException("Datei „$filename“ ist leer.")

        val hash = importFileStorage.sha256(bytes)
        val existingDocument = sourceDocumentDao.findByHash(hash)

        val parseResult = try {
            TikTokExcelParser.parse(bytes.inputStream())
        } catch (e: XlsxParseException) {
            throw ImportException("„$filename“ konnte nicht als TikTok-Excel gelesen werden: ${e.message}", e)
        }

        // Dublettenschlüssel ist (Transaction ID + Earning-Type), NICHT die Transaction ID
        // allein - dieselbe ID kann in einem echten Report mehrfach mit unterschiedlichem
        // Earning-Type auftreten (z. B. "Seller bonus" + "Standard commission" für denselben
        // Verkauf) und sind dann zwei eigenständige, echte Einnahmen (siehe IncomeEntry-Kommentar).
        val existingKeys = incomeDao
            .findExistingExternalKeys(parseResult.rows.map { it.externalTransactionId }.distinct())
            .map { it.externalTransactionId to it.externalEarningType }
            .toSet()
        val (duplicateRows, newRows) = parseResult.rows.partition {
            (it.externalTransactionId to it.earningType) in existingKeys
        }

        ExcelImportPreview(
            filename = filename,
            fileHash = hash,
            fileBytes = bytes,
            existingSourceDocument = existingDocument,
            period = parseResult.period,
            newRows = newRows,
            duplicateRows = duplicateRows,
            parseErrors = parseResult.errors,
        )
    }

    /** Speichert die neuen Zeilen aus [preview] als [IncomeEntry]s und protokolliert den Import. */
    suspend fun commit(preview: ExcelImportPreview): ImportBatch = withContext(Dispatchers.IO) {
        val sourceDocument = preview.existingSourceDocument ?: SourceDocument(
            filename = preview.filename,
            mimeType = XLSX_MIME_TYPE,
            localBackupPath = importFileStorage.persistBytes(preview.fileBytes, preview.filename),
            hash = preview.fileHash,
            sourceType = SourceDocumentType.TIKTOK_EXCEL,
        )
        if (preview.existingSourceDocument == null) sourceDocumentDao.upsert(sourceDocument)

        preview.newRows.forEach { row ->
            incomeDao.upsert(
                IncomeEntry(
                    date = row.transactionDate,
                    platform = "TikTok Shop",
                    incomeType = row.incomeType,
                    amountCents = row.grossAmountCents,
                    currency = row.currency,
                    // Excel-Import zeigt nur "Earned" laut Report, keinen Auszahlungsstatus -
                    // status bleibt ACCRUED, bis der Nutzer ihn manuell/per Screenshot aktualisiert
                    // (TAX_LOGIC_DE.md: angezeigte Provision != Auszahlung).
                    status = IncomeStatus.ACCRUED,
                    confirmed = true,
                    externalTransactionId = row.externalTransactionId,
                    externalEarningType = row.earningType,
                    payer = row.payer,
                    payerCountry = row.payerCountry,
                    platformExpenseCents = row.platformExpenseCents,
                    activityPhase = ActivityPhase.forDate(row.transactionDate),
                    sourceDocumentId = sourceDocument.id,
                ),
            )
        }

        val batch = ImportBatch(
            sourceDocumentId = sourceDocument.id,
            filename = preview.filename,
            month = preview.period,
            rowCount = preview.newRows.size + preview.duplicateRows.size,
            newTransactionCount = preview.newTransactionCount,
            duplicateCount = preview.duplicateCount,
            totalIncomeCents = preview.totalNewIncomeCents,
            sourceHash = preview.fileHash,
        )
        importBatchDao.insert(batch)
        batch
    }

    /**
     * Voranalyse für den universellen Import (§1-6): akzeptiert .xlsx UND .csv, erkennt
     * Einnahme/Ausgabe automatisch. Dublettenprüfung (§6):
     * - Zeilen MIT Transaction ID (TikTok-Reports): wie bisher über den DB-Unique-Index
     *   (externalTransactionId + externalEarningType, siehe [IncomeEntry]-Kommentar).
     * - Zeilen OHNE Transaction ID (generische Ausgaben-Importe): stabiler Schlüssel aus
     *   Datum + Händler + Betrag + Kategorie gegen bereits gespeicherte Ausgaben, damit ein
     *   wiederholter Import derselben Datei keine Dubletten erzeugt, ohne echte unterschiedliche
     *   Ausgaben (anderes Datum/anderer Betrag) fälschlich zusammenzufassen.
     */
    suspend fun previewUniversal(uri: Uri, filename: String): UniversalImportPreview = withContext(Dispatchers.IO) {
        val bytes = readBytesOrThrow(uri, filename)
        val hash = importFileStorage.sha256(bytes)
        val existingDocument = sourceDocumentDao.findByHash(hash)

        val analysis = UniversalSpreadsheetImporter.analyze(bytes, filename)
        val (duplicateRows, newRows) = partitionDuplicates(analysis.rows)

        UniversalImportPreview(
            filename = filename,
            fileHash = hash,
            fileBytes = bytes,
            existingSourceDocument = existingDocument,
            sheets = analysis.sheets,
            newRows = newRows,
            duplicateRows = duplicateRows,
            parseErrors = analysis.errors,
            sheetsData = analysis.sheetsData,
        )
    }

    /**
     * Wendet eine vom Nutzer im "Spalten zuordnen"-Screen (§4) gewählte Spaltenzuordnung auf
     * EIN Sheet der bereits geladenen Datei an - ohne die Datei erneut zu lesen (siehe
     * [UniversalImportPreview.sheetsData]). Zeilen anderer Sheets bleiben unverändert.
     */
    suspend fun applyColumnMapping(
        preview: UniversalImportPreview,
        sheetName: String,
        mapping: Map<ColumnRole, Int>,
    ): UniversalImportPreview = withContext(Dispatchers.IO) {
        val sheetAnalysis = preview.sheets.first { it.sheetName == sheetName }
        val rawRows = preview.sheetsData.getValue(sheetName)
        val (extractedRows, extractErrors) = UniversalSpreadsheetImporter.extractRows(
            sheetName, rawRows, sheetAnalysis.headerRowIndex, mapping,
        )
        val (newDuplicates, newNew) = partitionDuplicates(extractedRows)

        preview.copy(
            sheets = preview.sheets.map {
                if (it.sheetName == sheetName) it.copy(columnMapping = mapping, isConfident = true) else it
            },
            newRows = preview.newRows.filterNot { it.sourceSheet == sheetName } + newNew,
            duplicateRows = preview.duplicateRows.filterNot { it.sourceSheet == sheetName } + newDuplicates,
            parseErrors = preview.parseErrors.filterNot { it.sourceSheet == sheetName } + extractErrors,
        )
    }

    /**
     * Überschreibt den erkannten Typ ALLER Zeilen, deren Typ nur aus dem Vorzeichen einer
     * generischen "Betrag"-Spalte geraten wurde ([ImportCandidateRow.isAmbiguousType]) - für
     * den Fall, dass der Nutzer ein ganz normales Excel/CSV ohne Income/Expense-Split
     * importiert und die automatische Vorzeichen-Vermutung nicht passt (§3: "Nutzer kann
     * überschreiben"). Eindeutige Zeilen (TikTok-Reports, explizite Income-/Expense-Spalten)
     * bleiben unverändert. Die Dublettenprüfung wird für die betroffenen Zeilen neu berechnet,
     * da sie vom Typ abhängt.
     */
    suspend fun setAmbiguousRowsType(preview: UniversalImportPreview, newType: EntryType): UniversalImportPreview =
        withContext(Dispatchers.IO) {
            fun flip(rows: List<ImportCandidateRow>) = rows.map { if (it.isAmbiguousType) it.copy(type = newType) else it }
            val allRows = flip(preview.newRows) + flip(preview.duplicateRows)
            val (duplicateRows, newRows) = partitionDuplicates(allRows)
            preview.copy(newRows = newRows, duplicateRows = duplicateRows)
        }

    /**
     * Dublettenprüfung (§6): Zeilen MIT Transaction ID über den bestehenden DB-Unique-Index
     * (externalTransactionId + externalEarningType); Zeilen OHNE Transaction ID (generische
     * Ausgaben) über Datum+Händler+Betrag+Kategorie gegen bereits gespeicherte Ausgaben.
     */
    private suspend fun partitionDuplicates(
        rows: List<ImportCandidateRow>,
    ): Pair<List<ImportCandidateRow>, List<ImportCandidateRow>> {
        val existingIncomeKeys = incomeDao
            .findExistingExternalKeys(rows.mapNotNull { it.externalTransactionId }.distinct())
            .map { it.externalTransactionId to it.externalEarningType }
            .toSet()
        val existingExpenseKeys = expenseDao.getAll()
            .map { expenseDedupKey(it.date, it.merchant, it.grossAmountCents, it.category) }
            .toSet()

        return rows.partition { row ->
            when {
                row.externalTransactionId != null ->
                    (row.externalTransactionId to row.externalEarningType) in existingIncomeKeys
                row.type == EntryType.EXPENSE ->
                    expenseDedupKey(row.date, row.merchant, row.amountCents, resolveExpenseCategory(row)) in existingExpenseKeys
                else -> false // generische Einnahme ohne Transaction ID: keine Dublettenprüfung möglich
            }
        }
    }

    /** Speichert [UniversalImportPreview.newRows] als Income-/ExpenseEntry, protokolliert den Import (§1-6). */
    suspend fun commitUniversal(preview: UniversalImportPreview): ImportBatch = withContext(Dispatchers.IO) {
        val sourceDocument = preview.existingSourceDocument ?: SourceDocument(
            filename = preview.filename,
            mimeType = if (preview.filename.endsWith(".csv", ignoreCase = true)) CSV_MIME_TYPE else XLSX_MIME_TYPE,
            localBackupPath = importFileStorage.persistBytes(preview.fileBytes, preview.filename),
            hash = preview.fileHash,
            sourceType = if (preview.newIncomeCount > 0 && preview.newExpenseCount == 0) {
                SourceDocumentType.TIKTOK_EXCEL
            } else {
                SourceDocumentType.OTHER
            },
        )
        if (preview.existingSourceDocument == null) sourceDocumentDao.upsert(sourceDocument)

        preview.newRows.forEach { row ->
            when (row.type) {
                EntryType.INCOME -> incomeDao.upsert(
                    IncomeEntry(
                        date = row.date,
                        platform = "TikTok Shop",
                        incomeType = row.category ?: "Sonstige Einnahme",
                        amountCents = row.amountCents,
                        currency = row.currency,
                        status = IncomeStatus.ACCRUED,
                        confirmed = true,
                        externalTransactionId = row.externalTransactionId,
                        externalEarningType = row.externalEarningType,
                        payer = row.payer,
                        payerCountry = row.payerCountry,
                        platformExpenseCents = row.platformExpenseCents,
                        activityPhase = ActivityPhase.forDate(row.date),
                        sourceDocumentId = sourceDocument.id,
                    ),
                )
                EntryType.EXPENSE -> {
                    val businessUsePercent = row.businessPercent?.coerceIn(0, 100) ?: 100
                    expenseDao.upsert(
                        ExpenseEntry(
                            date = row.date,
                            merchant = row.merchant,
                            category = resolveExpenseCategory(row),
                            grossAmountCents = row.amountCents,
                            currency = row.currency,
                            businessUsePercent = businessUsePercent,
                            businessAmountCents = ExpenseEntry.computeBusinessAmountCents(row.amountCents, businessUsePercent),
                            confirmed = true,
                            note = buildExpenseImportNote(preview.filename, row),
                            activityPhase = ActivityPhase.forDate(row.date),
                            sourceDocumentId = sourceDocument.id,
                        ),
                    )
                }
            }
        }

        val batch = ImportBatch(
            sourceDocumentId = sourceDocument.id,
            filename = preview.filename,
            month = preview.periodRange?.let { (from, to) -> if (from == to) "$from" else "$from – $to" },
            rowCount = preview.newRows.size + preview.duplicateRows.size,
            newTransactionCount = preview.newRows.size,
            duplicateCount = preview.duplicateCount,
            totalIncomeCents = preview.totalNewIncomeCents,
            totalExpenseCents = preview.totalNewExpenseCents,
            sourceHash = preview.fileHash,
        )
        importBatchDao.insert(batch)
        batch
    }

    private suspend fun readBytesOrThrow(uri: Uri, filename: String): ByteArray {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ImportException("Datei „$filename“ konnte nicht geöffnet werden.")
        } catch (e: IOException) {
            throw ImportException("Datei „$filename“ konnte nicht gelesen werden: ${e.message}", e)
        }
        if (bytes.isEmpty()) throw ImportException("Datei „$filename“ ist leer.")
        return bytes
    }

    private fun resolveExpenseCategory(row: ImportCandidateRow): ExpenseCategory =
        CategorySuggester.suggest(row.merchant) ?: CategorySuggester.suggest(row.category) ?: ExpenseCategory.OTHER

    /** Fasst Herkunft + optionale "Status"/"Quelle"-Spalten aus der Importdatei zusammen (§12). */
    private fun buildExpenseImportNote(filename: String, row: ImportCandidateRow): String {
        val base = "Import: $filename (Zeile ${row.sourceRowNumber}, ${row.sourceSheet})"
        val extras = listOfNotNull(
            row.status?.let { "Status: $it" },
            row.source?.let { "Quelle: $it" },
        )
        return if (extras.isEmpty()) base else "$base - ${extras.joinToString(", ")}"
    }

    private fun expenseDedupKey(date: LocalDate, merchant: String?, amountCents: Long, category: ExpenseCategory): String =
        "$date|${merchant?.trim()?.lowercase()}|$amountCents|${category.name}"
}
