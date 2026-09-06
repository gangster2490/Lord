package de.spardirekt.ugcclean.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageEncoder {
    fun encodeAll(context: Context, uris: List<String>, maxSide: Int = 1280, quality: Int = 85): List<String> {
        return uris.mapNotNull { encode(context, it, maxSide, quality) }
    }

    fun encode(context: Context, uriString: String, maxSide: Int = 1280, quality: Int = 85): String? {
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return null
                val scaled = scale(original, maxSide)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                if (scaled !== original) original.recycle()
                scaled.recycle()
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                "data:image/jpeg;base64,$b64"
            }
        }.getOrNull()
    }

    private fun scale(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        val nw = (src.width * scale).toInt().coerceAtLeast(1)
        val nh = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
