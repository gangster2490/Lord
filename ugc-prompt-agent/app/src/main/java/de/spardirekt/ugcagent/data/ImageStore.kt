package de.spardirekt.ugcagent.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

data class StoredImage(
    val id: String,
    val file: File,
    val thumbDataUrl: String,
    val hash: Long,
)

class ImageStore(private val context: Context) {

    private val dir: File = File(context.filesDir, "images").apply { mkdirs() }
    private val images = LinkedHashMap<String, StoredImage>()

    fun list(): List<StoredImage> = images.values.toList()

    fun get(id: String): StoredImage? = images[id]

    fun clear() {
        images.values.forEach { it.file.delete() }
        images.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    fun importAll(uris: List<Uri>): List<StoredImage> {
        clear()
        uris.take(MAX_IMAGES).forEach { uri ->
            runCatching { importOne(uri) }.getOrNull()?.let { images[it.id] = it }
        }
        return list()
    }

    private fun importOne(uri: Uri): StoredImage {
        val id = UUID.randomUUID().toString()
        val compressed = ImageCompressor.compress(context, uri)
        val file = File(dir, "$id.jpg")
        file.writeBytes(compressed.bytes)
        val thumb = ImageCompressor.thumbDataUrl(compressed.bitmap)
        val hash = SimilarityChecker.averageHash(compressed.bitmap)
        compressed.bitmap.recycle()
        return StoredImage(id = id, file = file, thumbDataUrl = thumb, hash = hash)
    }

    companion object {
        const val MIN_IMAGES = 15
        const val MAX_IMAGES = 20
    }
}

data class CompressedImage(
    val bytes: ByteArray,
    val bitmap: Bitmap,
)

object ImageCompressor {
    const val MAX_EDGE = 1568
    const val JPEG_QUALITY = 85
    private const val MAX_BYTES = 420_000
    private const val THUMB_EDGE = 160

    fun compress(context: Context, uri: Uri): CompressedImage {
        val original = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalArgumentException("image_unreadable")

        var bitmap = scale(original, MAX_EDGE)
        if (bitmap !== original) original.recycle()

        var quality = JPEG_QUALITY
        var bytes = encode(bitmap, quality)
        if (bytes.size > MAX_BYTES) {
            quality = 75
            bytes = encode(bitmap, quality)
        }
        if (bytes.size > MAX_BYTES) {
            val smaller = scale(bitmap, 1280)
            if (smaller !== bitmap) {
                bitmap.recycle()
                bitmap = smaller
            }
            bytes = encode(bitmap, 70)
        }
        return CompressedImage(bytes = bytes, bitmap = bitmap)
    }

    fun thumbDataUrl(src: Bitmap): String {
        val thumb = scale(src, THUMB_EDGE)
        val bytes = encode(thumb, 70)
        if (thumb !== src) thumb.recycle()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    fun fileToBase64(file: File): String =
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun scale(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxEdge) return src
        val factor = maxEdge.toFloat() / longest
        val w = (src.width * factor).toInt().coerceAtLeast(1)
        val h = (src.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
