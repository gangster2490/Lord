package de.spardirekt.ugcagent.data

import android.graphics.Bitmap
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class SimilarityResult(
    val firstFrameId: String,
    val comparedCount: Int,
    val outlierIds: List<String>,
    val warning: Boolean,
    val maxDistance: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("firstFrameId", firstFrameId)
        .put("comparedCount", comparedCount)
        .put("outlierIds", JSONArray(outlierIds))
        .put("warning", warning)
        .put("maxDistance", maxDistance)
}

object SimilarityChecker {
    const val HASH_SIZE = 8
    const val OUTLIER_DISTANCE = 18

    fun averageHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, HASH_SIZE, HASH_SIZE, true)
        val pixels = IntArray(HASH_SIZE * HASH_SIZE)
        scaled.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)
        if (scaled !== bitmap) scaled.recycle()
        return averageHashFromPixels(pixels)
    }

    fun averageHashFromPixels(pixels: IntArray): Long {
        if (pixels.isEmpty()) return 0L
        val gray = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
        }
        val mean = gray.average()
        var bits = 0L
        gray.forEachIndexed { index, value ->
            if (value >= mean) {
                bits = bits or (1L shl index)
            }
        }
        return bits
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun compare(firstFrame: StoredImage, others: List<StoredImage>): SimilarityResult {
        val rest = others.filter { it.id != firstFrame.id }
        val outliers = rest.filter { hamming(firstFrame.hash, it.hash) > OUTLIER_DISTANCE }
        val maxDistance = rest.maxOfOrNull { hamming(firstFrame.hash, it.hash) } ?: 0
        val warning = rest.isNotEmpty() && outliers.size * 3 >= rest.size
        return SimilarityResult(
            firstFrameId = firstFrame.id,
            comparedCount = rest.size,
            outlierIds = outliers.map { it.id },
            warning = warning,
            maxDistance = maxDistance,
        )
    }
}
