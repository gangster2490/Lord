package de.tiktokshop.buchhaltung.data.receipts

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/**
 * Kopiert importierte/aufgenommene Belege unveränderlich in den privaten App-Speicher.
 * Originale werden nie überschrieben (PRODUCT_REQUIREMENTS.md Qualitätskriterium).
 */
class ReceiptStorage(private val context: Context) {

    private val receiptsDir: File
        get() = File(context.filesDir, "receipts").apply { mkdirs() }

    private val captureDir: File
        get() = File(context.cacheDir, "camera_captures").apply { mkdirs() }

    /** Erstellt eine Ziel-URI für eine neue Kamera-Aufnahme (vor dem eigentlichen Import). */
    fun createCameraCaptureUri(): Uri {
        val file = File(captureDir, "capture_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Kopiert ein Bild (Kamera- oder Galerie-URI) dauerhaft in den App-Speicher und gibt den Pfad zurück. */
    fun persistReceipt(sourceUri: Uri, date: LocalDate): String {
        val extension = context.contentResolver.getType(sourceUri)?.substringAfterLast('/') ?: "jpg"
        val fileName = "beleg_${date}_${UUID.randomUUID()}.$extension"
        val destination = File(receiptsDir, fileName)
        context.contentResolver.openInputStream(sourceUri).use { input ->
            destination.outputStream().use { output ->
                requireNotNull(input) { "Konnte Beleg-URI nicht öffnen: $sourceUri" }.copyTo(output)
            }
        }
        return destination.absolutePath
    }

    fun computeSha256(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun fileForPath(path: String): File = File(path)
}
