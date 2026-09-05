package de.spardirekt.ugcagent.v3.image

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

object FirstFrameHeuristics {
    const val AUTO_APPLY_CONFIDENCE = 0.75
    const val PAUSE_CONFIDENCE = 0.55

    fun check(width: Int, height: Int, compressedBytes: Long): JSONObject {
        val warnings = JSONArray()
        var usable = true
        val longest = max(width, height)
        val shortest = min(width, height)
        if (longest < 400 || shortest < 240) {
            usable = false
            warnings.put("Product area may be too small for a First Frame reference.")
        }
        if (compressedBytes < 8_000L) {
            warnings.put("Image file is very small; it may be too blurry for a reference.")
        }
        val confidence = when {
            !usable -> 0.35
            warnings.length() > 0 -> 0.62
            longest >= 800 -> 0.86
            else -> 0.74
        }
        return JSONObject()
            .put("usable", usable)
            .put("confidence", confidence)
            .put("warnings", warnings)
            .put("source", "local")
    }

    fun merge(local: JSONObject, ai: JSONObject): JSONObject {
        val warnings = JSONArray()
        fun addAll(from: JSONObject) {
            val arr = from.optJSONArray("warnings") ?: JSONArray()
            for (i in 0 until arr.length()) warnings.put(arr.optString(i))
        }
        addAll(local)
        addAll(ai)
        val usable = local.optBoolean("usable", true) && ai.optBoolean("usable", true)
        val confidence = min(local.optDouble("confidence", 0.5), ai.optDouble("confidence", 0.5))
        return JSONObject()
            .put("usable", usable)
            .put("confidence", confidence)
            .put("warnings", warnings)
            .put("source", "local+ai")
    }

    data class RankedImage(
        val id: String,
        val index: Int,
        val width: Int,
        val height: Int,
        val compressedBytes: Long,
        val score: Double,
    )

    fun score(width: Int, height: Int, compressedBytes: Long): Double {
        val area = width.toDouble() * height
        val minSide = min(width, height).toDouble()
        val bytesScore = (compressedBytes / 50_000.0).coerceAtMost(8.0)
        return area / 1_000_000.0 + minSide / 100.0 + bytesScore - screenshotPenalty(width, height, compressedBytes)
    }

    fun looksLikeScreenshot(width: Int, height: Int, compressedBytes: Long): Boolean {
        val longest = max(width, height).toDouble()
        val shortest = min(width, height).toDouble().coerceAtLeast(1.0)
        val aspect = longest / shortest
        return aspect >= 1.9 && compressedBytes < 90_000L
    }

    fun screenshotPenalty(width: Int, height: Int, compressedBytes: Long): Double {
        var penalty = 0.0
        if (looksLikeScreenshot(width, height, compressedBytes)) penalty += 12.0
        val longest = max(width, height).toDouble()
        val shortest = min(width, height).toDouble().coerceAtLeast(1.0)
        if (longest / shortest >= 1.85) penalty += 3.0
        if (compressedBytes < 25_000L) penalty += 4.0
        return penalty
    }

    fun rank(images: List<RankedImage>): List<RankedImage> = images.sortedByDescending { it.score }

    fun recommendLocal(images: List<RankedImage>): RankedImage? = rank(images).firstOrNull()

    fun mergeRecommendation(localId: String?, aiIndex: Int, images: List<RankedImage>): RankedImage? {
        val fromAi = images.firstOrNull { it.index == aiIndex }
        return fromAi ?: images.firstOrNull { it.id == localId } ?: recommendLocal(images)
    }

    fun recommendationConfidence(quality: JSONObject, ai: JSONObject?): Double {
        val local = quality.optDouble("confidence", 0.0)
        val remote = ai?.optDouble("confidence", local) ?: local
        return min(local, if (remote > 0) remote else local)
    }

    fun shouldAutoApply(confidence: Double, quality: JSONObject): Boolean =
        quality.optBoolean("usable", false) && confidence >= AUTO_APPLY_CONFIDENCE

    fun shouldPauseForLowConfidence(confidence: Double, quality: JSONObject): Boolean =
        !quality.optBoolean("usable", false)

    fun rejectAsFirstFrame(ai: JSONObject?): Boolean {
        if (ai == null) return false
        if (ai.optBoolean("marketplace_ui_over_product", false)) return true
        if (!ai.optBoolean("identity_components_visible", true)) return true
        val reasons = ai.optJSONArray("reasons")?.toString()?.lowercase() ?: ""
        return reasons.contains("text-only") ||
            reasons.contains("description page") ||
            reasons.contains("safety page") ||
            reasons.contains("infographic") && !reasons.contains("product")
    }
}
