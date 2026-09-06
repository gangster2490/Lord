package de.spardirekt.clipforge.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import de.spardirekt.clipforge.data.model.EncodedImage
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
            ?: throw IllegalArgumentException("Не удалось прочитать изображение.")
        if (bytes.size > MAX_BYTES) {
            throw IllegalArgumentException("Файл слишком большой — максимум 10 МБ.")
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Нужно фото товара (JPG, PNG, WEBP).")

        val scaled = scaleDown(bitmap)
        val out = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== bitmap) scaled.recycle()
        if (!ok) throw IllegalArgumentException("Не удалось сжать изображение.")

        val encoded = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        return EncodedImage(base64 = encoded, mime = "image/jpeg")
    }

    fun maxEdgeAfterScale(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toFloat() / longest
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val (w, h) = maxEdgeAfterScale(bitmap.width, bitmap.height)
        if (w == bitmap.width && h == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun guessMime(uri: Uri): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext?.lowercase())
    }
}
