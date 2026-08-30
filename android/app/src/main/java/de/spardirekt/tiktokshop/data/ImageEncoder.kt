package de.spardirekt.tiktokshop.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageEncoder {
    const val MAX_BYTES = 10 * 1024 * 1024

    fun encode(resolver: ContentResolver, uri: Uri): EncodedImage {
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val name = queryDisplayName(resolver, uri) ?: "image.jpg"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > MAX_BYTES) {
                    error("Datei zu groß – max. 10 MB.")
                }
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: error("Bild konnte nicht gelesen werden.")

        if (bytes.isEmpty()) error("Leere Bilddatei.")
        return EncodedImage(
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            mime = mime,
            displayName = name,
            uriString = uri.toString(),
        )
    }

    fun toDataUrl(image: EncodedImage): String = "data:${image.mime};base64,${image.base64}"

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }
}
