package de.spardirekt.ugcagent.v3.pipeline

import org.json.JSONArray
import org.json.JSONObject

object ProductConsistency {
    const val HARD_CONFLICT_THRESHOLD = 0.85

    private val evidenceVariation = Regex(
        """\b(viewpoint|viewpoints|camera angle|angles?|packaging|package|boxed|instruction cards?|infographic|close-?ups?|usage|demonstrat(?:e|ion)|background|lighting|crop(?:ping)?|lifestyle|screenshot|size card|feature (?:card|description)|marketplace)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val hardGeometry = Regex(
        """\b(incompatible geometry|physically different|different product(?:s)?|different model|different shape|two products|another product|geometry conflict|conflicting geometry)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun shouldPauseForDifferentProducts(result: JSONObject): Boolean {
        if (result.optBoolean("same_product", true)) return false
        if (result.optDouble("confidence", 0.0) < HARD_CONFLICT_THRESHOLD) return false
        if (looksLikeEvidenceVariation(result)) return false
        if (result.optBoolean("hard_geometry_conflict", false)) return true
        return looksLikeIncompatibleGeometry(result.optString("reason"))
    }

    fun looksLikeEvidenceVariation(result: JSONObject): Boolean {
        val reason = result.optString("reason")
        if (evidenceVariation.containsMatchIn(reason)) return true
        val types = stringList(result.optJSONArray("ignored_variation_types"))
        return types.any { evidenceVariation.containsMatchIn(it) }
    }

    fun looksLikeIncompatibleGeometry(reason: String): Boolean = hardGeometry.containsMatchIn(reason)

    fun dominantIndices(result: JSONObject, imageCount: Int): List<Int> {
        val all = (0 until imageCount.coerceAtLeast(0)).toList()
        val stated = intList(result.optJSONArray("dominant_product_indices")).filter { it in all }
        if (stated.isNotEmpty()) return stated.distinct()
        val conflicting = intList(result.optJSONArray("conflicting_image_indices")).filter { it in all }.toSet()
        if (conflicting.isNotEmpty() && conflicting.size < imageCount) {
            val kept = all.filter { it !in conflicting }
            if (kept.isNotEmpty()) return kept
        }
        return all
    }

    fun autoSelectWarning(result: JSONObject): String {
        val reason = result.optString("reason").ifBlank { "mixed reference views" }
        return "Dominant product identity selected automatically ($reason). Viewpoint, packaging, instruction, infographic, close-up, usage and background differences are not different products."
    }

    private fun intList(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            if (arr.isNull(idx)) null else arr.optInt(idx, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        }
    }

    private fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }
}
