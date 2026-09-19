package de.tiktokshop.buchhaltung.data.repository

import android.content.Context
import android.net.Uri
import de.tiktokshop.buchhaltung.data.db.AppDatabase
import de.tiktokshop.buchhaltung.data.importer.ExcelImportPreview
import de.tiktokshop.buchhaltung.data.importer.ImportException
import de.tiktokshop.buchhaltung.data.importer.ImportFileStorage
import de.tiktokshop.buchhaltung.data.importer.TikTokExcelParser
import de.tiktokshop.buchhaltung.data.model.ActivityPhase
import de.tiktokshop.buchhaltung.data.model.ImportBatch
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.IncomeStatus
import de.tiktokshop.buchhaltung.data.model.SourceDocument
import de.tiktokshop.buchhaltung.data.model.SourceDocumentType
import de.tiktokshop.buchhaltung.data.xlsx.XlsxParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

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
}
