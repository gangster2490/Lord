package de.spardirekt.ugcagent.v3.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

object ImageProcessor {
    const val MAX_EDGE = 1568
    const val JPEG_QUALITY = 82
    const val MIN_EDGE_WARN = 1280

    data class Prepared(
        val file: File,
        val width: Int,
        val height: Int,
        val originalBytes: Long,
        val compressedBytes: Long,
        val mime: String = "image/jpeg",
    )

    fun prepare(source: File, destinationDir: File, originalBytes: Long = source.length()): Prepared {
        destinationDir.mkdirs()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val orientation = readOrientation(source)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, opts)
            ?: throw IllegalArgumentException("image_decode_error")
        val rotated = applyOrientation(decoded, orientation)
        val scaled = scaleToMax(rotated, MAX_EDGE)
        if (scaled !== rotated && rotated !== decoded) rotated.recycle()
        if (rotated !== decoded) decoded.recycle()
        val out = File(destinationDir, UUID.randomUUID().toString() + ".jpg")
        val bytes = ByteArrayOutputStream()
        var quality = JPEG_QUALITY
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
        while (bytes.size() > 1_200_000 && quality > 70) {
            bytes.reset()
            quality -= 5
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
        }
        out.writeBytes(bytes.toByteArray())
        val w = scaled.width
        val h = scaled.height
        if (scaled !== decoded) scaled.recycle()
        return Prepared(out, w, h, originalBytes, out.length())
    }

    fun thumbnailJpeg(source: File, maxEdge: Int = 256, quality: Int = 60): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, opts) ?: return ByteArray(0)
        val scaled = scaleToMax(decoded, maxEdge)
        val bytes = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return bytes.toByteArray()
    }

    fun maybeCompressMore(prepared: Prepared, destinationDir: File): Prepared {
        if (prepared.compressedBytes < 700_000) return prepared
        val bitmap = BitmapFactory.decodeFile(prepared.file.absolutePath) ?: return prepared
        val smaller = scaleToMax(bitmap, 1280)
        val out = File(destinationDir, UUID.randomUUID().toString() + ".jpg")
        val bytes = ByteArrayOutputStream()
        smaller.compress(Bitmap.CompressFormat.JPEG, 75, bytes)
        out.writeBytes(bytes.toByteArray())
        val w = smaller.width
        val h = smaller.height
        if (smaller !== bitmap) smaller.recycle()
        bitmap.recycle()
        return Prepared(out, w, h, prepared.originalBytes, out.length())
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (max(w, h) / sample > maxEdge * 2) sample *= 2
        return sample
    }

    private fun scaleToMax(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun readOrientation(file: File): Int {
        return try {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
