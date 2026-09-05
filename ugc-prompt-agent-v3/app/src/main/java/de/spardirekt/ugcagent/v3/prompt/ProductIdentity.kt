package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONArray
import org.json.JSONObject

object ProductIdentity {
    const val READINESS_HIGH_MESSAGE_RU =
        "Недостаточно визуальной информации для безопасной динамичной сцены. Рекомендуется более простое действие или дополнительные фотографии."

    const val READINESS_HIGH_MESSAGE_DE =
        "Es liegen nicht genug visuelle Informationen für eine sichere dynamische Szene vor. Empfohlen wird eine einfachere Aktion oder zusätzliche Fotos."

    const val MICROWAVE_COVER_LOCK =
        "Keep the transparent dome, green circular base ring and curved green handle exactly as referenced.\n" +
            "Preserve the separate circular upper vent and both separate rectangular transparent upper modules with their green caps in their original relative positions.\n" +
            "Preserve the central clear dome seam/rib and visible base attachment layout.\n" +
            "Do not replace, merge, remove or reinterpret any of these identity-critical components.\n" +
            "Never replace the two rectangular upper modules or the circular vent with one cylindrical reservoir."

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

    fun looksLikeMicrowaveCover(fingerprint: JSONObject?): Boolean {
        if (fingerprint == null) return false
        val blob = fingerprint.toString().lowercase()
        return blob.contains("dome") &&
            blob.contains("rectangular") &&
            blob.contains("vent") &&
            blob.contains("handle") &&
            blob.contains("green")
    }

    fun structuralLockBlock(fingerprint: JSONObject?): String {
        val lines = mutableListOf("STRUCTURAL IDENTITY LOCK:")
        val geometry = fingerprint?.optString("overall_geometry").orEmpty().trim()
        if (geometry.isNotBlank()) lines.add(geometry)
        joinList(fingerprint, "identity_critical_components")?.let { lines.add("Identity-critical components: $it") }
        joinList(fingerprint, "component_count_constraints")?.let { lines.add("Component count: $it") }
        joinList(fingerprint, "component_layout")?.let { lines.add("Layout: $it") }
        joinList(fingerprint, "attachment_points")?.let { lines.add("Attachment layout: $it") }
        joinList(fingerprint, "must_not_change")?.let { lines.add("Must not change: $it") }
        joinList(fingerprint, "uncertain_hidden_geometry")?.let {
            lines.add("Uncertain/hidden geometry — do not invent: $it")
        }
        lines.add("Keep exactly the same single physical product throughout the entire video.")
        if (looksLikeMicrowaveCover(fingerprint)) {
            lines.add(MICROWAVE_COVER_LOCK)
        }
        return lines.joinToString("\n")
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
