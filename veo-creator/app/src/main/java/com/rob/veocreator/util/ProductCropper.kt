package com.rob.veocreator.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val ANALYSIS_MAX_DIMENSION = 640
private const val BACKGROUND_MATCH_THRESHOLD = 28
private const val EDGE_BACKGROUND_FRACTION = 0.92f
private const val MARGIN_FRACTION = 0.075f
private const val MIN_CONTENT_FRACTION = 0.05f
private const val MAX_CONTENT_FRACTION = 0.97f

/**
 * Deterministic, offline auto-crop: strips uniform borders around the product - large empty
 * margins, black letterbox bars, screenshot chrome, plain white/solid backgrounds - by sampling
 * the border color and scanning inward from each edge for the first row/column that isn't
 * overwhelmingly that color. This directly targets the reported failure mode (product occupying
 * only a small area of a screenshot-like upload) without any ML/network dependency. It won't
 * distinguish a real busy background from the product, which is what the manual "Edit Crop"
 * override in the UI is for.
 */
object ProductCropper {

    /** Returns a normalized (0f..1f) crop rect in the original image's coordinate space, or
     *  null if no safe crop was found (near-uniform image, or the product already fills the frame). */
    fun suggestCrop(context: Context, uri: Uri): RectF? {
        val bitmap = decodeDownsampled(context, uri, ANALYSIS_MAX_DIMENSION) ?: return null
        try {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 8 || h < 8) return null

            val bg = sampleBorderColor(bitmap)

            var left = 0
            while (left < w / 2 && isColumnBackground(bitmap, left, bg)) left++
            var right = w - 1
            while (right > w / 2 && isColumnBackground(bitmap, right, bg)) right--
            var top = 0
            while (top < h / 2 && isRowBackground(bitmap, top, bg)) top++
            var bottom = h - 1
            while (bottom > h / 2 && isRowBackground(bitmap, bottom, bg)) bottom--

            if (right <= left || bottom <= top) return null

            val contentFraction = ((right - left).toFloat() * (bottom - top)) / (w.toFloat() * h)
            if (contentFraction < MIN_CONTENT_FRACTION || contentFraction > MAX_CONTENT_FRACTION) return null

            val marginX = (right - left) * MARGIN_FRACTION
            val marginY = (bottom - top) * MARGIN_FRACTION
            val cropLeft = max(0f, left - marginX)
            val cropTop = max(0f, top - marginY)
            val cropRight = min(w.toFloat(), right + marginX)
            val cropBottom = min(h.toFloat(), bottom + marginY)

            return RectF(cropLeft / w, cropTop / h, cropRight / w, cropBottom / h)
        } finally {
            bitmap.recycle()
        }
    }

    private fun sampleBorderColor(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val stripPx = max(1, (min(w, h) * 0.02f).toInt())
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        fun accumulate(x: Int, y: Int) {
            val c = bitmap.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            count++
        }
        var x = 0
        while (x < w) {
            for (y in 0 until stripPx) accumulate(x, y)
            for (y in h - stripPx until h) accumulate(x, y)
            x += max(1, w / 50)
        }
        var y = 0
        while (y < h) {
            for (px in 0 until stripPx) accumulate(px, y)
            for (px in w - stripPx until w) accumulate(px, y)
            y += max(1, h / 50)
        }
        if (count == 0L) return intArrayOf(255, 255, 255)
        return intArrayOf((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun isBackgroundPixel(color: Int, bg: IntArray): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return abs(r - bg[0]) <= BACKGROUND_MATCH_THRESHOLD &&
            abs(g - bg[1]) <= BACKGROUND_MATCH_THRESHOLD &&
            abs(b - bg[2]) <= BACKGROUND_MATCH_THRESHOLD
    }

    private fun isColumnBackground(bitmap: Bitmap, x: Int, bg: IntArray): Boolean {
        val h = bitmap.height
        var bgCount = 0
        var total = 0
        var y = 0
        val step = max(1, h / 100)
        while (y < h) {
            if (isBackgroundPixel(bitmap.getPixel(x, y), bg)) bgCount++
            total++
            y += step
        }
        return total > 0 && bgCount.toFloat() / total >= EDGE_BACKGROUND_FRACTION
    }

    private fun isRowBackground(bitmap: Bitmap, y: Int, bg: IntArray): Boolean {
        val w = bitmap.width
        var bgCount = 0
        var total = 0
        var x = 0
        val step = max(1, w / 100)
        while (x < w) {
            if (isBackgroundPixel(bitmap.getPixel(x, y), bg)) bgCount++
            total++
            x += step
        }
        return total > 0 && bgCount.toFloat() / total >= EDGE_BACKGROUND_FRACTION
    }

    private fun decodeDownsampled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
