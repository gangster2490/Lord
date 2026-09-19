package de.tiktokshop.buchhaltung.data.importer

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Speichert importierte Quelldateien (aktuell: TikTok-Excel-Reports) unveränderlich im
 * privaten App-Speicher - wird nie gelöscht, auch nicht wenn daraus erzeugte Buchungen
 * gelöscht werden (§9: Audit-Trail).
 */
class ImportFileStorage(private val context: Context) {

    private val importsDir: File
        get() = File(context.filesDir, "imports").apply { mkdirs() }

    fun persistBytes(bytes: ByteArray, originalFilename: String): String {
        val destination = File(importsDir, "${UUID.randomUUID()}_${sanitizeFilename(originalFilename)}")
        destination.writeBytes(bytes)
        return destination.absolutePath
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sanitizeFilename(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
