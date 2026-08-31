package de.spardirekt.tiktokshop.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import de.spardirekt.tiktokshop.data.model.EncodedImage
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageEncoder {
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_EDGE = 1600

    fun encode(context: Context, uri: Uri): EncodedImage {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: guessMime(uri)
            ?: "image/jpeg"

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Bild konnte nicht gelesen werden.")
        if (bytes.size > MAX_BYTES) {
            throw IllegalArgumentException("Datei zu groß – max. 10 MB.")
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Nur Bilddateien erlaubt (JPG, PNG, WEBP).")

        val scaled = scaleDown(bitmap)
        val out = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== bitmap) scaled.recycle()
        if (!ok) throw IllegalArgumentException("Bild konnte nicht komprimiert werden.")

        val encoded = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        return EncodedImage(base64 = encoded, mime = "image/jpeg")
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val scale = MAX_EDGE.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun guessMime(uri: Uri): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext?.lowercase())
    }
}
