package de.spardirekt.ugcagent.v3.ai

import org.json.JSONArray
import org.json.JSONObject

object JsonExtractor {
    fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
        return (fenced ?: trimmed).trim()
    }

    fun extractObject(raw: String): JSONObject {
        val candidate = stripFences(raw)
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IllegalArgumentException("invalid_json")
        }
        return JSONObject(candidate.substring(start, end + 1))
    }

    fun requireKeys(obj: JSONObject, keys: List<String>): JSONObject {
        keys.forEach { key ->
            if (!obj.has(key)) throw IllegalArgumentException("missing_key:$key")
        }
        return obj
    }

    fun analysisSchemaKeys(): List<String> = listOf(
        "product_category",
        "observed_use_case",
        "observed_context",
        "visual_features_relevant_to_use",
        "text_claims",
        "dimensions",
        "usage_instructions",
        "inferred_use_case",
        "possible_target_audience",
        "possible_pain_point",
        "possible_actions",
        "confidence",
        "ambiguity_warning",
    )

    fun consistencySchemaKeys(): List<String> = listOf(
        "same_product",
        "confidence",
        "conflicting_image_indices",
        "reason",
    )

    fun firstFrameSchemaKeys(): List<String> = listOf(
        "usable",
        "confidence",
        "warnings",
    )

    fun fingerprintSchemaKeys(): List<String> = listOf(
        "overall_geometry",
        "identity_critical_components",
        "component_count_constraints",
        "component_layout",
        "attachment_points",
        "moving_or_removable_parts",
        "must_not_change",
        "uncertain_hidden_geometry",
        "confidence",
    )

    fun actionRiskSchemaKeys(): List<String> = listOf(
        "risk",
        "risk_reasons",
        "geometry_that_must_move",
        "identity_critical_moving_components",
        "hidden_geometry_required",
        "motion_geometry_risk",
        "recommended_safe_action",
    )

    fun readinessSchemaKeys(): List<String> = listOf(
        "score",
        "missing_views",
        "ambiguous_components",
        "generation_risk",
    )

    fun firstFrameRecommendSchemaKeys(): List<String> = listOf(
        "recommended_image_index",
        "reasons",
        "identity_components_visible",
        "marketplace_ui_over_product",
    )

    fun complianceSchemaKeys(): List<String> = listOf(
        "status",
        "warnings",
        "blocked_reasons",
        "claims_detected",
        "evidence_supported_claims",
        "unsupported_claims",
    )

    fun stringList(obj: JSONObject, key: String): List<String> {
        if (!obj.has(key) || obj.isNull(key)) return emptyList()
        val value = obj.get(key)
        return when (value) {
            is JSONArray -> (0 until value.length()).map { value.optString(it) }.filter { it.isNotBlank() }
            else -> listOf(value.toString()).filter { it.isNotBlank() }
        }
    }

    fun withDefaults(obj: JSONObject, keys: List<String>): JSONObject {
        keys.forEach { key ->
            if (!obj.has(key) || obj.isNull(key)) {
                when (key) {
                    "visual_features_relevant_to_use",
                    "text_claims",
                    "dimensions",
                    "usage_instructions",
                    "possible_actions",
                    "conflicting_image_indices",
                    "warnings",
                    "blocked_reasons",
                    "claims_detected",
                    "evidence_supported_claims",
                    "unsupported_claims",
                    "identity_critical_components",
                    "component_count_constraints",
                    "component_layout",
                    "attachment_points",
                    "moving_or_removable_parts",
                    "must_not_change",
                    "uncertain_hidden_geometry",
                    "risk_reasons",
                    "geometry_that_must_move",
                    "identity_critical_moving_components",
                    "hidden_geometry_required",
                    "missing_views",
                    "ambiguous_components",
                    "reasons",
                    -> obj.put(key, JSONArray())
                    "same_product", "usable", "identity_components_visible" -> obj.put(key, true)
                    "marketplace_ui_over_product" -> obj.put(key, false)
                    "confidence", "score" -> obj.put(key, 0.0)
                    "recommended_image_index" -> obj.put(key, 0)
                    "risk", "generation_risk", "motion_geometry_risk" -> obj.put(key, "LOW")
                    "status" -> obj.put(key, "WARNING")
                    else -> obj.put(key, "")
                }
            }
        }
        return obj
    }
}
