package de.tiktokshop.buchhaltung.export

import android.content.Context
import android.net.Uri
import de.tiktokshop.buchhaltung.data.model.ExpenseEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceEntry
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatus
import de.tiktokshop.buchhaltung.data.model.FrozenBalanceStatusHistory
import de.tiktokshop.buchhaltung.data.model.IncomeEntry
import de.tiktokshop.buchhaltung.data.model.StatusHistory
import de.tiktokshop.buchhaltung.data.repository.LedgerRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class BackupEnvelope(
    val schemaVersion: Int = 1,
    val createdAtEpochMillis: Long,
    val incomes: List<BackupIncome>,
    val expenses: List<BackupExpense>,
    val statusHistory: List<BackupStatusHistory>,
    val frozenBalances: List<BackupFrozenBalance> = emptyList(),
    val frozenBalanceHistory: List<BackupFrozenBalanceStatusHistory> = emptyList(),
)

@Serializable
private data class BackupIncome(
    val id: String,
    val date: String,
    val platform: String,
    val incomeType: String,
    val amountCents: Long,
    val currency: String,
    val status: String,
    val payoutDate: String?,
    val paidOutAmountCents: Long?,
    val note: String?,
    val receiptFileNames: List<String>,
    val ocrRawText: String?,
    val ocrConfidence: Float?,
    val confirmed: Boolean,
    val imageHash: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val externalTransactionId: String? = null,
    val externalEarningType: String? = null,
    val payer: String? = null,
    val payerCountry: String? = null,
    val platformExpenseCents: Long? = null,
    val activityPhase: String = "REGULAR_BUSINESS",
    val taxRelevant: Boolean = true,
    val sourceDocumentId: String? = null,
)

@Serializable
private data class BackupExpense(
    val id: String,
    val date: String,
    val merchant: String?,
    val category: String,
    val grossAmountCents: Long,
    val currency: String,
    val vatAmountCents: Long?,
    val businessUsePercent: Int,
    val businessAmountCents: Long,
    val paymentMethod: String?,
    val note: String?,
    val receiptFileNames: List<String>,
    val ocrRawText: String?,
    val ocrConfidence: Float?,
    val confirmed: Boolean,
    val imageHash: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val activityPhase: String = "REGULAR_BUSINESS",
    val taxRelevant: Boolean = true,
    val sourceDocumentId: String? = null,
)

@Serializable
private data class BackupStatusHistory(
    val id: String,
    val incomeEntryId: String,
    val oldStatus: String?,
    val newStatus: String,
    val changedAtEpochMillis: Long,
    val note: String?,
    val receiptFileName: String?,
)

@Serializable
private data class BackupFrozenBalance(
    val id: String,
    val date: String,
    val amountCents: Long,
    val currency: String,
    val platform: String,
    val status: String,
    val note: String?,
    val receiptFileNames: List<String>,
    val sourceDocumentId: String?,
    val periodReference: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
private data class BackupFrozenBalanceStatusHistory(
    val id: String,
    val frozenBalanceEntryId: String,
    val oldStatus: String?,
    val newStatus: String,
    val changedAtEpochMillis: Long,
    val note: String?,
)

class BackupRestoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Backup/Restore als ZIP (JSON-Manifest + Belegkopien), siehe TC08. */
class BackupManager(private val context: Context, private val repository: LedgerRepository) {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val backupDir: File
        get() = File(context.externalCacheDir ?: context.cacheDir, "backups").apply { mkdirs() }

    suspend fun createBackup(): File {
        val incomes = repository.getAllIncomes()
        val expenses = repository.getAllExpenses()
        val history = repository.getAllStatusHistory()
        val frozenBalances = repository.getAllFrozenBalances()
        val frozenBalanceHistory = repository.getAllFrozenBalanceHistory()

        val envelope = BackupEnvelope(
            createdAtEpochMillis = Instant.now().toEpochMilli(),
            incomes = incomes.map {
                BackupIncome(
                    id = it.id,
                    date = it.date.toString(),
                    platform = it.platform,
                    incomeType = it.incomeType,
                    amountCents = it.amountCents,
                    currency = it.currency,
                    status = it.status.name,
                    payoutDate = it.payoutDate?.toString(),
                    paidOutAmountCents = it.paidOutAmountCents,
                    note = it.note,
                    receiptFileNames = it.receiptUris.map { path -> File(path).name },
                    ocrRawText = it.ocrRawText,
                    ocrConfidence = it.ocrConfidence,
                    confirmed = it.confirmed,
                    imageHash = it.imageHash,
                    createdAtEpochMillis = it.createdAt.toEpochMilli(),
                    updatedAtEpochMillis = it.updatedAt.toEpochMilli(),
                    externalTransactionId = it.externalTransactionId,
                    externalEarningType = it.externalEarningType,
                    payer = it.payer,
                    payerCountry = it.payerCountry,
                    platformExpenseCents = it.platformExpenseCents,
                    activityPhase = it.activityPhase.name,
                    taxRelevant = it.taxRelevant,
                    sourceDocumentId = it.sourceDocumentId,
                )
            },
            expenses = expenses.map {
                BackupExpense(
                    id = it.id,
                    date = it.date.toString(),
                    merchant = it.merchant,
                    category = it.category.name,
                    grossAmountCents = it.grossAmountCents,
                    currency = it.currency,
                    vatAmountCents = it.vatAmountCents,
                    businessUsePercent = it.businessUsePercent,
                    businessAmountCents = it.businessAmountCents,
                    paymentMethod = it.paymentMethod,
                    note = it.note,
                    receiptFileNames = it.receiptUris.map { path -> File(path).name },
                    ocrRawText = it.ocrRawText,
                    ocrConfidence = it.ocrConfidence,
                    confirmed = it.confirmed,
                    imageHash = it.imageHash,
                    createdAtEpochMillis = it.createdAt.toEpochMilli(),
                    updatedAtEpochMillis = it.updatedAt.toEpochMilli(),
                    activityPhase = it.activityPhase.name,
                    taxRelevant = it.taxRelevant,
                    sourceDocumentId = it.sourceDocumentId,
                )
            },
            statusHistory = history.map {
                BackupStatusHistory(
                    id = it.id,
                    incomeEntryId = it.incomeEntryId,
                    oldStatus = it.oldStatus?.name,
                    newStatus = it.newStatus.name,
                    changedAtEpochMillis = it.changedAt.toEpochMilli(),
                    note = it.note,
                    receiptFileName = it.receiptUri?.let { path -> File(path).name },
                )
            },
            frozenBalances = frozenBalances.map {
                BackupFrozenBalance(
                    id = it.id,
                    date = it.date.toString(),
                    amountCents = it.amountCents,
                    currency = it.currency,
                    platform = it.platform,
                    status = it.status.name,
                    note = it.note,
                    receiptFileNames = it.receiptUris.map { path -> File(path).name },
                    sourceDocumentId = it.sourceDocumentId,
                    periodReference = it.periodReference,
                    createdAtEpochMillis = it.createdAt.toEpochMilli(),
                    updatedAtEpochMillis = it.updatedAt.toEpochMilli(),
                )
            },
            frozenBalanceHistory = frozenBalanceHistory.map {
                BackupFrozenBalanceStatusHistory(
                    id = it.id,
                    frozenBalanceEntryId = it.frozenBalanceEntryId,
                    oldStatus = it.oldStatus?.name,
                    newStatus = it.newStatus.name,
                    changedAtEpochMillis = it.changedAt.toEpochMilli(),
                    note = it.note,
                )
            },
        )

        val allReceiptPaths = (
            incomes.flatMap { it.receiptUris } +
                expenses.flatMap { it.receiptUris } +
                frozenBalances.flatMap { it.receiptUris }
            ).distinct()
        val backupFile = File(backupDir, "backup_${Instant.now().toEpochMilli()}.zip")
        ZipOutputStream(backupFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            allReceiptPaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("belege/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return backupFile
    }

    /**
     * Liest ein Backup-ZIP und liefert eine Vorschau (Anzahl Einnahmen/Ausgaben) vor
     * dem eigentlichen Restore (UX_FLOW.md: "Restore mit Vorschau").
     */
    data class RestorePreview(val incomeCount: Int, val expenseCount: Int, val createdAt: Instant)

    suspend fun preview(backupUri: Uri): RestorePreview {
        val envelope = readEnvelope(backupUri)
        return RestorePreview(
            incomeCount = envelope.incomes.size,
            expenseCount = envelope.expenses.size,
            createdAt = Instant.ofEpochMilli(envelope.createdAtEpochMillis),
        )
    }

    /** Stellt Buchungen und Belege wieder her. Vorhandene lokale Daten werden nicht gelöscht, nur ergänzt/ersetzt (per ID). */
    suspend fun restore(backupUri: Uri) {
        val (envelope, receiptBytesByName) = readEnvelopeWithReceipts(backupUri)
        val receiptsDir = File(context.filesDir, "receipts").apply { mkdirs() }

        fun restoreReceipt(fileName: String): String {
            val target = File(receiptsDir, fileName)
            receiptBytesByName[fileName]?.let { target.writeBytes(it) }
            return target.absolutePath
        }

        envelope.incomes.forEach { backup ->
            repository.restoreIncome(
                IncomeEntry(
                    id = backup.id,
                    date = LocalDate.parse(backup.date),
                    platform = backup.platform,
                    incomeType = backup.incomeType,
                    amountCents = backup.amountCents,
                    currency = backup.currency,
                    status = de.tiktokshop.buchhaltung.data.model.IncomeStatus.valueOf(backup.status),
                    payoutDate = backup.payoutDate?.let(LocalDate::parse),
                    paidOutAmountCents = backup.paidOutAmountCents,
                    note = backup.note,
                    receiptUris = backup.receiptFileNames.map(::restoreReceipt),
                    ocrRawText = backup.ocrRawText,
                    ocrConfidence = backup.ocrConfidence,
                    confirmed = backup.confirmed,
                    imageHash = backup.imageHash,
                    createdAt = Instant.ofEpochMilli(backup.createdAtEpochMillis),
                    updatedAt = Instant.ofEpochMilli(backup.updatedAtEpochMillis),
                    externalTransactionId = backup.externalTransactionId,
                    externalEarningType = backup.externalEarningType,
                    payer = backup.payer,
                    payerCountry = backup.payerCountry,
                    platformExpenseCents = backup.platformExpenseCents,
                    activityPhase = de.tiktokshop.buchhaltung.data.model.ActivityPhase.valueOf(backup.activityPhase),
                    taxRelevant = backup.taxRelevant,
                    sourceDocumentId = backup.sourceDocumentId,
                ),
            )
        }

        envelope.expenses.forEach { backup ->
            repository.restoreExpense(
                ExpenseEntry(
                    id = backup.id,
                    date = LocalDate.parse(backup.date),
                    merchant = backup.merchant,
                    category = de.tiktokshop.buchhaltung.data.model.ExpenseCategory.valueOf(backup.category),
                    grossAmountCents = backup.grossAmountCents,
                    currency = backup.currency,
                    vatAmountCents = backup.vatAmountCents,
                    businessUsePercent = backup.businessUsePercent,
                    businessAmountCents = backup.businessAmountCents,
                    paymentMethod = backup.paymentMethod,
                    note = backup.note,
                    receiptUris = backup.receiptFileNames.map(::restoreReceipt),
                    ocrRawText = backup.ocrRawText,
                    ocrConfidence = backup.ocrConfidence,
                    confirmed = backup.confirmed,
                    imageHash = backup.imageHash,
                    createdAt = Instant.ofEpochMilli(backup.createdAtEpochMillis),
                    updatedAt = Instant.ofEpochMilli(backup.updatedAtEpochMillis),
                    activityPhase = de.tiktokshop.buchhaltung.data.model.ActivityPhase.valueOf(backup.activityPhase),
                    taxRelevant = backup.taxRelevant,
                    sourceDocumentId = backup.sourceDocumentId,
                ),
            )
        }

        envelope.statusHistory.forEach { backup ->
            repository.restoreStatusHistory(
                StatusHistory(
                    id = backup.id,
                    incomeEntryId = backup.incomeEntryId,
                    oldStatus = backup.oldStatus?.let { de.tiktokshop.buchhaltung.data.model.IncomeStatus.valueOf(it) },
                    newStatus = de.tiktokshop.buchhaltung.data.model.IncomeStatus.valueOf(backup.newStatus),
                    changedAt = Instant.ofEpochMilli(backup.changedAtEpochMillis),
                    note = backup.note,
                    receiptUri = backup.receiptFileName?.let { fileName -> File(receiptsDir, fileName).absolutePath },
                ),
            )
        }

        envelope.frozenBalances.forEach { backup ->
            repository.restoreFrozenBalance(
                FrozenBalanceEntry(
                    id = backup.id,
                    date = LocalDate.parse(backup.date),
                    amountCents = backup.amountCents,
                    currency = backup.currency,
                    platform = backup.platform,
                    status = FrozenBalanceStatus.valueOf(backup.status),
                    note = backup.note,
                    receiptUris = backup.receiptFileNames.map(::restoreReceipt),
                    sourceDocumentId = backup.sourceDocumentId,
                    periodReference = backup.periodReference,
                    createdAt = Instant.ofEpochMilli(backup.createdAtEpochMillis),
                    updatedAt = Instant.ofEpochMilli(backup.updatedAtEpochMillis),
                ),
            )
        }

        envelope.frozenBalanceHistory.forEach { backup ->
            repository.restoreFrozenBalanceHistory(
                FrozenBalanceStatusHistory(
                    id = backup.id,
                    frozenBalanceEntryId = backup.frozenBalanceEntryId,
                    oldStatus = backup.oldStatus?.let { FrozenBalanceStatus.valueOf(it) },
                    newStatus = FrozenBalanceStatus.valueOf(backup.newStatus),
                    changedAt = Instant.ofEpochMilli(backup.changedAtEpochMillis),
                    note = backup.note,
                ),
            )
        }
    }

    private fun readEnvelope(backupUri: Uri): BackupEnvelope = readEnvelopeWithReceipts(backupUri).first

    private fun readEnvelopeWithReceipts(backupUri: Uri): Pair<BackupEnvelope, Map<String, ByteArray>> {
        var envelope: BackupEnvelope? = null
        val receipts = mutableMapOf<String, ByteArray>()

        val stream = context.contentResolver.openInputStream(backupUri)
            ?: throw BackupRestoreException("Backup-Datei konnte nicht geöffnet werden.")

        stream.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val bytes = zip.readBytes()
                    when {
                        entry.name == "manifest.json" -> {
                            envelope = try {
                                json.decodeFromString(BackupEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
                            } catch (e: SerializationException) {
                                throw BackupRestoreException("Backup-Manifest ist beschädigt oder ungültig.", e)
                            }
                        }
                        entry.name.startsWith("belege/") -> {
                            receipts[File(entry.name).name] = bytes
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        return (envelope ?: throw BackupRestoreException("Backup enthält kein manifest.json.")) to receipts
    }
}
