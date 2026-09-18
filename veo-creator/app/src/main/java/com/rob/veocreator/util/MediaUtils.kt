package com.rob.veocreator.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
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

/** Below this on the shorter side, a crop is too small to reliably preserve product detail. */
const val MIN_USEFUL_DIMENSION_PX = 480

data class ImageLoadResult(
    val inline: InlineImage,
    val originalWidth: Int,
    val originalHeight: Int,
    /** Dimensions of the crop region in the original image's pixel space, before any further
     *  downscale-if-still-too-large. Equal to original width/height when no crop was applied. */
    val cropWidth: Int,
    val cropHeight: Int,
    val transmittedWidth: Int,
    val transmittedHeight: Int,
    val originalBytes: Int,
    val transmittedBytes: Int,
    val resizeApplied: Boolean,
    val compressionApplied: Boolean
) {
    /** What fraction of the original frame the transmitted crop covers. */
    val productCoveragePercent: Int
        get() = if (originalWidth == 0 || originalHeight == 0) 100 else
            (((cropWidth.toLong() * cropHeight) * 100) / (originalWidth.toLong() * originalHeight)).toInt()

    val lowResolutionWarning: Boolean
        get() = minOf(transmittedWidth, transmittedHeight) < MIN_USEFUL_DIMENSION_PX
}

object MediaUtils {

    /** Reads an image Uri into memory without recompressing it, ready to embed in a Veo request. */
    fun loadInlineImage(context: Context, uri: Uri): InlineImage = loadInlineImageWithDiagnostics(context, uri).inline

    /**
     * Reads the original file bytes untouched whenever possible - only crops (per [cropRect], a
     * normalized 0f..1f rect) or resizes/recompresses when the image is larger than
     * [MAX_DIMENSION_PX] on its longest side or [MAX_BYTES], to avoid bloating the request. When
     * it does have to re-encode, it keeps the source format family (PNG stays PNG, WEBP stays
     * WEBP, JPEG stays JPEG) at high quality rather than forcing everything through low-quality
     * JPEG, and never upsamples or artificially sharpens.
     */
    fun loadInlineImageWithDiagnostics(context: Context, uri: Uri, cropRect: RectF? = null): ImageLoadResult {
        val mimeType = context.contentResolver.getType(uri) ?: guessMimeFromUri(uri) ?: "image/jpeg"
        val originalBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read image")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, bounds)
        val originalWidth = bounds.outWidth
        val originalHeight = bounds.outHeight

        val hasMeaningfulCrop = cropRect != null &&
            (cropRect.left > 0.005f || cropRect.top > 0.005f || cropRect.right < 0.995f || cropRect.bottom < 0.995f)

        val needsResize = maxOf(originalWidth, originalHeight) > MAX_DIMENSION_PX
        val needsShrink = needsResize || originalBytes.size > MAX_BYTES

        if (!hasMeaningfulCrop && !needsShrink) {
            return ImageLoadResult(
                inline = InlineImage(GeminiVeoClient.toBase64(originalBytes), mimeType),
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                cropWidth = originalWidth,
                cropHeight = originalHeight,
                transmittedWidth = originalWidth,
                transmittedHeight = originalHeight,
                originalBytes = originalBytes.size,
                transmittedBytes = originalBytes.size,
                resizeApplied = false,
                compressionApplied = false
            )
        }

        val cropPx = if (hasMeaningfulCrop) {
            Rect(
                (cropRect!!.left * originalWidth).roundToInt().coerceIn(0, originalWidth - 1),
                (cropRect.top * originalHeight).roundToInt().coerceIn(0, originalHeight - 1),
                (cropRect.right * originalWidth).roundToInt().coerceIn(1, originalWidth),
                (cropRect.bottom * originalHeight).roundToInt().coerceIn(1, originalHeight)
            )
        } else Rect(0, 0, originalWidth, originalHeight)
        val cropWidth = cropPx.width()
        val cropHeight = cropPx.height()

        // Decode at just enough resolution to crop precisely without loading a huge full bitmap
        // into memory for large source photos.
        val decodeTarget = maxOf(MAX_DIMENSION_PX, maxOf(cropWidth, cropHeight))
        val sampleSize = calculateInSampleSize(originalWidth, originalHeight, decodeTarget)
        val fullBitmap = BitmapFactory.decodeByteArray(
            originalBytes, 0, originalBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: throw IllegalStateException("Could not decode image")

        val scaleFactor = fullBitmap.width.toFloat() / originalWidth
        val scaledCrop = Rect(
            (cropPx.left * scaleFactor).roundToInt().coerceIn(0, fullBitmap.width - 1),
            (cropPx.top * scaleFactor).roundToInt().coerceIn(0, fullBitmap.height - 1),
            (cropPx.right * scaleFactor).roundToInt().coerceIn(1, fullBitmap.width),
            (cropPx.bottom * scaleFactor).roundToInt().coerceIn(1, fullBitmap.height)
        )
        val croppedBitmap = Bitmap.createBitmap(
            fullBitmap, scaledCrop.left, scaledCrop.top, scaledCrop.width(), scaledCrop.height()
        )
        if (croppedBitmap !== fullBitmap) fullBitmap.recycle()

        val needsFurtherResize = maxOf(croppedBitmap.width, croppedBitmap.height) > MAX_DIMENSION_PX
        val finalBitmap = if (needsFurtherResize) {
            val scale = MAX_DIMENSION_PX.toFloat() / maxOf(croppedBitmap.width, croppedBitmap.height)
            Bitmap.createScaledBitmap(
                croppedBitmap, (croppedBitmap.width * scale).roundToInt(), (croppedBitmap.height * scale).roundToInt(), true
            ).also { if (it !== croppedBitmap) croppedBitmap.recycle() }
        } else croppedBitmap

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
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            transmittedWidth = finalBitmap.width,
            transmittedHeight = finalBitmap.height,
            originalBytes = originalBytes.size,
            transmittedBytes = transmittedBytes.size,
            resizeApplied = needsFurtherResize,
            compressionApplied = true
        )
    }

    /** Renders just the cropped region at a small preview size - used for the "Image sent to
     *  Veo" thumbnail so it stays fast even for large source photos. */
    fun renderCroppedPreview(context: Context, uri: Uri, cropRect: RectF?, maxDim: Int = 512): Bitmap? {
        val bitmap = decodeDownsampledBitmap(context, uri, maxDim) ?: return null
        if (cropRect == null) return bitmap
        val left = (cropRect.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (cropRect.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (cropRect.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (cropRect.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    /** Decodes the full (uncropped) image downsampled for on-screen display, e.g. in the crop editor. */
    fun decodeDownsampledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Original pixel dimensions without decoding the full bitmap - (0, 0) if unreadable. */
    fun decodeBounds(context: Context, uri: Uri): Pair<Int, Int> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return 0 to 0
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth to bounds.outHeight
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
