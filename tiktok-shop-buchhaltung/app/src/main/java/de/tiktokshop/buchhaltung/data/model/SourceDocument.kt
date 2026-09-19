package de.tiktokshop.buchhaltung.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Jede importierte Excel-Datei oder jeder Beleg bekommt ein SourceDocument. Das Original wird
 * unveränderlich nach `localBackupPath` kopiert und NIE gelöscht (auch nicht beim Löschen der
 * daraus erzeugten Buchungen) - dient als Audit-Trail (§9, §26).
 */
@Entity(tableName = "source_documents")
data class SourceDocument(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val filename: String,
    val mimeType: String,
    val importDate: Instant = Instant.now(),
    /** content://-URI zum Zeitpunkt des Imports - nach App-Neustart oft nicht mehr gültig, nur informativ. */
    val originalUri: String? = null,
    /** Pfad der unveränderlichen Kopie im privaten App-Speicher (siehe ReceiptStorage/ImportFileStorage). */
    val localBackupPath: String,
    /** SHA-256 der Datei - Grundlage der Datei-Dublettenprüfung (§8). */
    val hash: String,
    val sourceType: SourceDocumentType,
    val notes: String? = null,
)
