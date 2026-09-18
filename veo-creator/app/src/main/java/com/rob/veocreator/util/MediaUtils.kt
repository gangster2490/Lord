package com.rob.veocreator.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.rob.veocreator.data.api.GeminiVeoClient
import com.rob.veocreator.data.api.InlineImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Only touched when an image is genuinely too large to send as-is - kept generous so normal
 *  product photos are transmitted at full original resolution/bytes. */
private const val MAX_DIMENSION_PX = 2048
private const val MAX_BYTES = 8 * 1024 * 1024

data class ImageLoadResult(
    val inline: InlineImage,
    val originalWidth: Int,
    val originalHeight: Int,
    val transmittedWidth: Int,
    val transmittedHeight: Int,
    val originalBytes: Int,
    val transmittedBytes: Int,
    val resizeApplied: Boolean,
    val compressionApplied: Boolean
)

object MediaUtils {

    /** Reads an image Uri into memory without recompressing it, ready to embed in a Veo request. */
    fun loadInlineImage(context: Context, uri: Uri): InlineImage = loadInlineImageWithDiagnostics(context, uri).inline

    /**
     * Reads the original file bytes untouched whenever possible - only resizes/recompresses when
     * the image is larger than [MAX_DIMENSION_PX] on its longest side or [MAX_BYTES], to avoid
     * bloating the request. When it does have to shrink, it keeps the source format family
     * (PNG stays PNG, WEBP stays WEBP, JPEG stays JPEG) at high quality rather than forcing
     * everything through low-quality JPEG.
     */
    fun loadInlineImageWithDiagnostics(context: Context, uri: Uri): ImageLoadResult {
        val mimeType = context.contentResolver.getType(uri) ?: guessMimeFromUri(uri) ?: "image/jpeg"
        val originalBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read image")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, bounds)
        val originalWidth = bounds.outWidth
        val originalHeight = bounds.outHeight

        val needsResize = maxOf(originalWidth, originalHeight) > MAX_DIMENSION_PX
        val needsShrink = needsResize || originalBytes.size > MAX_BYTES

        if (!needsShrink) {
            return ImageLoadResult(
                inline = InlineImage(GeminiVeoClient.toBase64(originalBytes), mimeType),
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                transmittedWidth = originalWidth,
                transmittedHeight = originalHeight,
                originalBytes = originalBytes.size,
                transmittedBytes = originalBytes.size,
                resizeApplied = false,
                compressionApplied = false
            )
        }

        val sampleSize = calculateInSampleSize(originalWidth, originalHeight, MAX_DIMENSION_PX)
        val decoded = BitmapFactory.decodeByteArray(
            originalBytes, 0, originalBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: throw IllegalStateException("Could not decode image")

        val scale = MAX_DIMENSION_PX.toFloat() / maxOf(decoded.width, decoded.height)
        val finalBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
        } else decoded

        val format = when {
            mimeType.contains("png") -> Bitmap.CompressFormat.PNG
            mimeType.contains("webp") ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        val output = ByteArrayOutputStream()
        finalBitmap.compress(format, 92, output)
        val transmittedBytes = output.toByteArray()

        return ImageLoadResult(
            inline = InlineImage(GeminiVeoClient.toBase64(transmittedBytes), mimeType),
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            transmittedWidth = finalBitmap.width,
            transmittedHeight = finalBitmap.height,
            originalBytes = originalBytes.size,
            transmittedBytes = transmittedBytes.size,
            resizeApplied = needsResize,
            compressionApplied = true
        )
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetMax: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w, h) / 2 >= targetMax) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun guessMimeFromUri(uri: Uri): String? {
        val path = uri.path ?: return null
        return when {
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".webp", true) -> "image/webp"
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            else -> null
        }
    }

    /** Copies a downloaded mp4 into the app's private cache so it can be played/shared. */
    fun cacheDir(context: Context): File =
        File(context.cacheDir, "videos").apply { mkdirs() }

    fun newVideoCacheFile(context: Context): File {
        val name = "veo_${System.currentTimeMillis()}.mp4"
        return File(cacheDir(context), name)
    }

    /** Saves a generated video into Movies/VeoCreator via MediaStore (scoped storage). Returns the public Uri. */
    fun saveVideoToMovies(context: Context, source: File): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "VeoCreator_$timestamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VeoCreator")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create MediaStore entry")

        resolver.openOutputStream(itemUri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("Could not open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }

        return itemUri
    }
}
