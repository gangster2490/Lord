package de.spardirekt.ugcagent.v3.image

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

object FirstFrameHeuristics {
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
}
