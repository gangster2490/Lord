package de.tiktokshop.buchhaltung.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Protokolliert einen einzelnen Excel-Import ("Import-Verlauf", §8/§29). Ein Import kann aus
 * mehreren gleichzeitig gewählten Dateien bestehen - pro Datei entsteht ein eigener
 * ImportBatch, damit "X neu / X bereits vorhanden" je Datei nachvollziehbar bleibt.
 */
@Entity(tableName = "import_batches")
data class ImportBatch(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceDocumentId: String,
    val filename: String,
    /** Aus der Excel-Metadatenzeile "Date period" abgeleitet, z. B. "2026-01"; null wenn nicht bestimmbar. */
    val month: String?,
    val importDate: Instant = Instant.now(),
    val rowCount: Int,
    val newTransactionCount: Int,
    val duplicateCount: Int,
    val totalIncomeCents: Cents,
    val sourceHash: String,
    /** Summe neuer Ausgaben aus diesem Import (§20) - 0 bei reinen TikTok-Earnings-Importen. */
    val totalExpenseCents: Cents = 0L,
)
