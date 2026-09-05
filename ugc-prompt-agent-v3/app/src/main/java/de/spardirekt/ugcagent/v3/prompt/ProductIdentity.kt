package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONArray
import org.json.JSONObject

object ProductIdentity {
    const val READINESS_HIGH_MESSAGE_RU =
        "Недостаточно визуальной информации для безопасной динамичной сцены. Рекомендуется более простое действие или дополнительные фотографии."

    const val READINESS_HIGH_MESSAGE_DE =
        "Es liegen nicht genug visuelle Informationen für eine sichere dynamische Szene vor. Empfohlen wird eine einfachere Aktion oder zusätzliche Fotos."

    const val MICROWAVE_COVER_LOCK =
        "Keep the transparent low dome, green circular perimeter base ring and curved green handle exactly as referenced.\n" +
            "Preserve the separate circular upper vent and exactly two separate square-capped rectangular transparent upper modules with their green caps in their original relative positions.\n" +
            "Preserve the visible ribs/seams including the central clear dome seam/rib and the visible perimeter attachment details.\n" +
            "Do not replace, merge, remove or reinterpret any of these identity-critical components.\n" +
            "Never replace the two rectangular upper modules or the circular vent with one cylindrical reservoir.\n" +
            "Do not remove the circular vent, relocate the handle, merge the modules, or change their relative positions."

    const val MICROWAVE_VENT_STATIC =
        "If the scene does not require the circular upper component to move, keep it completely static.\n" +
            "If that circular upper component moves, its shape, diameter, thickness and attachment point must remain identical; the axis of movement must stay consistent; it must not rise, stretch, expand, collapse or turn into another mechanism."

    fun microwaveCoverFingerprint(): JSONObject = JSONObject()
        .put("overall_geometry", "transparent dome body with a green circular base ring and a curved green carry handle")
        .put(
            "identity_critical_components",
            JSONArray()
                .put("one transparent dome body")
                .put("one green circular base ring")
                .put("one curved green carry handle")
                .put("one separate circular upper vent/control")
                .put("two separate transparent rectangular upper modules with green caps")
                .put("one central visible clear dome seam/rib")
                .put("visible base clips / groove / attachment layout"),
        )
        .put(
            "component_count_constraints",
            JSONArray()
                .put("two separate rectangular upper modules must remain two separate modules")
                .put("circular upper vent must remain separate from the rectangular modules"),
        )
        .put(
            "component_layout",
            JSONArray().put("circular vent and both rectangular modules stay in their original relative positions on the upper dome"),
        )
        .put("attachment_points", JSONArray().put("visible base clips / groove / attachment layout"))
        .put("moving_or_removable_parts", JSONArray().put("curved green carry handle").put("circular upper vent if evidenced as adjustable"))
        .put(
            "must_not_change",
            JSONArray()
                .put("transparent dome")
                .put("green circular base ring")
                .put("curved green handle")
                .put("separate circular upper vent")
                .put("two separate rectangular transparent upper modules with green caps")
                .put("central clear dome seam/rib")
                .put("visible base attachment layout"),
        )
        .put(
            "uncertain_hidden_geometry",
            JSONArray().put("exact fill opening and internal water path are not clearly confirmed across references"),
        )
        .put("confidence", 0.86)

    fun finalIdentityConstraints(fingerprint: JSONObject?): List<String> {
        val out = mutableListOf<String>()
        if (looksLikeMicrowaveCover(fingerprint)) {
            out.add("transparent low dome with green circular perimeter base ring and curved green handle")
            out.add("separate circular upper vent remains present and separate")
            out.add("exactly two separate square-capped rectangular transparent upper modules with green caps")
            out.add("original relative positions of the circular upper vent and both rectangular modules")
            out.add("visible ribs/seams including the central clear dome seam/rib")
            out.add("visible perimeter attachment layout")
            out.add("never replace the two rectangular modules or the circular vent with one cylindrical reservoir")
            out.add("do not remove the circular vent, relocate the handle, merge modules, or change relative positions")
        }
        val geometry = fingerprint?.optString("overall_geometry").orEmpty().trim()
        if (geometry.isNotBlank()) out.add(geometry)
        addVisibleItems(fingerprint, "identity_critical_components", out)
        addVisibleItems(fingerprint, "component_count_constraints", out)
        addVisibleItems(fingerprint, "component_layout", out)
        addVisibleItems(fingerprint, "attachment_points", out, skipUnconfirmed = true)
        addVisibleItems(fingerprint, "must_not_change", out)
        return out.map { it.trim() }.filter { it.isNotBlank() && !isInternalLeak(it) }.distinctBy { it.lowercase() }.take(10)
    }

    fun finalIdentityLockBlock(fingerprint: JSONObject?): String {
        val constraints = finalIdentityConstraints(fingerprint).let { items ->
            if (items.size >= 5) items else (items + listOf(
                "Keep exactly the same single physical product",
                "Preserve exact component count, geometry and relative positions",
                "Do not merge, split, remove, relocate, simplify or invent components",
                "Do not generate a similar or generic category-equivalent product",
                "Do not invent hidden structure",
            )).distinctBy { it.lowercase() }.take(10)
        }
        return buildString {
            appendLine("FINAL IDENTITY LOCK:")
            constraints.forEachIndexed { index, line -> appendLine("${index + 1}. $line") }
        }.trim()
    }

    fun hasFinishConflict(fingerprint: JSONObject?): Boolean {
        val blob = fingerprint?.toString()?.lowercase() ?: return false
        if (blob.contains("finish_conflict") || blob.contains("conflicting finish") || blob.contains("color/finish")) return true
        return blob.contains("finish") && (blob.contains("conflict") || blob.contains("disagree") || blob.contains("differ"))
    }

    fun looksLikeMicrowaveCover(fingerprint: JSONObject?): Boolean {
        if (fingerprint == null) return false
        val blob = fingerprint.toString().lowercase()
        return blob.contains("dome") &&
            blob.contains("rectangular") &&
            blob.contains("vent") &&
            blob.contains("handle") &&
            blob.contains("green")
    }

    fun structuralLockBlock(fingerprint: JSONObject?): String = finalIdentityLockBlock(fingerprint)

    private fun addVisibleItems(fingerprint: JSONObject?, key: String, out: MutableList<String>, skipUnconfirmed: Boolean = false) {
        val arr = fingerprint?.optJSONArray(key) ?: return
        for (i in 0 until arr.length()) {
            val item = arr.optString(i).trim()
            if (item.isBlank() || isInternalLeak(item)) continue
            if (skipUnconfirmed && (item.contains("unconfirmed", true) || item.contains("hidden", true) || item.contains("uncertain", true))) continue
            out.add(item)
        }
    }

    private fun isInternalLeak(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("uncertain_hidden") ||
            lower.contains("ambiguity_warning") ||
            lower.contains("hidden mechanism") ||
            lower.contains("unconfirmed attachment") ||
            lower.contains("exact dimension")
    }

    fun localReadiness(fingerprint: JSONObject?): JSONObject {
        val uncertain = fingerprint?.optJSONArray("uncertain_hidden_geometry") ?: JSONArray()
        val confidence = fingerprint?.optDouble("confidence", 0.0) ?: 0.0
        val ambiguous = (0 until uncertain.length()).map { uncertain.optString(it) }.filter { it.isNotBlank() }
        val risk = when {
            fingerprint == null || confidence < 0.45 || ambiguous.size >= 3 -> "HIGH"
            confidence < 0.7 || ambiguous.isNotEmpty() -> "MEDIUM"
            else -> "LOW"
        }
        val missing = JSONArray()
        if (confidence < 0.7) missing.put("additional clean product views")
        return JSONObject()
            .put("score", confidence)
            .put("missing_views", missing)
            .put("ambiguous_components", JSONArray(ambiguous))
            .put("generation_risk", risk)
            .put("source", "local")
    }

    fun mergeReadiness(local: JSONObject, ai: JSONObject): JSONObject {
        val risk = higherRisk(local.optString("generation_risk"), ai.optString("generation_risk"))
        val missing = mergeArrays(local.optJSONArray("missing_views"), ai.optJSONArray("missing_views"))
        val ambiguous = mergeArrays(local.optJSONArray("ambiguous_components"), ai.optJSONArray("ambiguous_components"))
        val score = minOf(local.optDouble("score", 0.0), ai.optDouble("score", local.optDouble("score", 0.0)))
        return JSONObject()
            .put("score", score)
            .put("missing_views", missing)
            .put("ambiguous_components", ambiguous)
            .put("generation_risk", risk)
            .put("source", "local+ai")
            .put("warning_ru", if (risk == "HIGH") READINESS_HIGH_MESSAGE_RU else "")
            .put("warning_de", if (risk == "HIGH") READINESS_HIGH_MESSAGE_DE else "")
    }

    fun warningFor(readiness: JSONObject?, lang: String): String {
        if (readiness?.optString("generation_risk") != "HIGH") return ""
        return if (lang.equals("ru", true)) READINESS_HIGH_MESSAGE_RU else READINESS_HIGH_MESSAGE_DE
    }

    fun higherRisk(a: String, b: String): String {
        fun rank(value: String) = when (value.uppercase()) {
            "HIGH" -> 2
            "MEDIUM" -> 1
            else -> 0
        }
        return if (rank(a) >= rank(b)) a.ifBlank { "LOW" }.uppercase() else b.uppercase()
    }

    private fun joinList(obj: JSONObject?, key: String): String? {
        val arr = obj?.optJSONArray(key) ?: return null
        val items = (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotBlank() }
        return items.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun mergeArrays(a: JSONArray?, b: JSONArray?): JSONArray {
        val out = JSONArray()
        val seen = mutableSetOf<String>()
        listOf(a, b).forEach { arr ->
            if (arr == null) return@forEach
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotBlank() && seen.add(value.lowercase())) out.put(value)
            }
        }
        return out
    }
}
