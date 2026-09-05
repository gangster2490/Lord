package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONArray
import org.json.JSONObject

object ActionIdentity {
    const val DEFAULT_SAFE_ACTION =
        "the product remains stationary while the person interacts around it in one continuous micro-moment"

    const val MICROWAVE_SAFE_ACTION =
        "cover already placed over food; one hand lightly grips the existing green handle"

    private val highRisk = listOf(
        Regex("""pour\s+(water|liquid)""", RegexOption.IGNORE_CASE),
        Regex("""fill\s+(the\s+)?(top|upper)?\s*(compartment|reservoir|tank|opening)""", RegexOption.IGNORE_CASE),
        Regex("""water into the top""", RegexOption.IGNORE_CASE),
        Regex("""hidden (compartment|mechanism|geometry|opening)""", RegexOption.IGNORE_CASE),
        Regex("""disassemble|unscrew|look inside""", RegexOption.IGNORE_CASE),
        Regex("""invent(ed)? (a )?(hinge|reservoir|compartment|mechanism)""", RegexOption.IGNORE_CASE),
        Regex("""open(ing)? (a )?(hidden|unseen)""", RegexOption.IGNORE_CASE),
        Regex("""unfold|detach the base|internal water path""", RegexOption.IGNORE_CASE),
    )

    private val mediumRisk = listOf(
        Regex("""move(s|ing)? (a |the )?(lid|module|component|vent)""", RegexOption.IGNORE_CASE),
        Regex("""fold(ing)?|remove(s|ing)? (the )?(lid|cap)""", RegexOption.IGNORE_CASE),
        Regex("""adjust(s|ing)? (the )?(vent|control)""", RegexOption.IGNORE_CASE),
    )

    fun localCheck(action: String, fingerprint: JSONObject?): JSONObject {
        val text = action.trim()
        val reasons = JSONArray()
        val hidden = JSONArray()
        val moving = JSONArray()
        var risk = "LOW"
        if (highRisk.any { it.containsMatchIn(text) }) {
            risk = "HIGH"
            reasons.put("Action requires hidden or ambiguous geometry that is not clearly confirmed by the references.")
            hidden.put("fill opening / internal path / unseen mechanism")
        } else if (mediumRisk.any { it.containsMatchIn(text) }) {
            risk = "MEDIUM"
            reasons.put("Action moves a component; only safe if that component is clearly documented in multiple references.")
            moving.put(text)
        }
        val uncertain = fingerprint?.optJSONArray("uncertain_hidden_geometry")
        if (uncertain != null && uncertain.length() > 0 && highRisk.any { it.containsMatchIn(text) }) {
            risk = "HIGH"
            reasons.put("References leave required geometry uncertain; do not reconstruct it.")
        }
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint) && text.contains("pour", ignoreCase = true)) {
            risk = "HIGH"
            reasons.put("Microwave-cover fill geometry is visually ambiguous; pouring water reconstructs the top structure.")
        }
        return JSONObject()
            .put("risk", risk)
            .put("risk_reasons", reasons)
            .put("geometry_that_must_move", moving)
            .put("hidden_geometry_required", hidden)
            .put("recommended_safe_action", recommendedSafeAction(fingerprint))
            .put("source", "local")
            .put("original_action", text)
    }

    fun merge(local: JSONObject, ai: JSONObject): JSONObject {
        val risk = ProductIdentity.higherRisk(local.optString("risk"), ai.optString("risk"))
        val safer = sequenceOf(ai.optString("recommended_safe_action"), local.optString("recommended_safe_action"))
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !isUnsafeAction(it) }
            ?: recommendedSafeAction(null)
        val merged = JSONObject()
            .put("risk", risk)
            .put("risk_reasons", mergeArrays(local.optJSONArray("risk_reasons"), ai.optJSONArray("risk_reasons")))
            .put("geometry_that_must_move", mergeArrays(local.optJSONArray("geometry_that_must_move"), ai.optJSONArray("geometry_that_must_move")))
            .put("hidden_geometry_required", mergeArrays(local.optJSONArray("hidden_geometry_required"), ai.optJSONArray("hidden_geometry_required")))
            .put("recommended_safe_action", safer)
            .put("source", "local+ai")
            .put("original_action", local.optString("original_action").ifBlank { ai.optString("original_action") })
        if (isUnsafeAction(safer) || (risk == "HIGH" && safer.equals(merged.optString("original_action"), true))) {
            merged.put("recommended_safe_action", recommendedSafeAction(null))
        }
        return merged
    }

    fun applyIfHighRisk(scene: JSONObject, risk: JSONObject): JSONObject {
        val original = scene.optString("main_action")
        if (risk.optString("risk") != "HIGH") {
            return scene
        }
        val safer = risk.optString("recommended_safe_action").ifBlank { DEFAULT_SAFE_ACTION }
        if (safer.equals(original, ignoreCase = true) || isUnsafeAction(safer)) {
            scene.put("main_action", DEFAULT_SAFE_ACTION)
        } else {
            scene.put("main_action", safer)
        }
        scene.put("rejected_high_risk_action", original)
        scene.put("action_identity_override", true)
        scene.put("action_risk", risk.optString("risk"))
        return scene
    }

    fun isUnsafeAction(action: String): Boolean = highRisk.any { it.containsMatchIn(action) }

    fun recommendedSafeAction(fingerprint: JSONObject?): String {
        return if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) MICROWAVE_SAFE_ACTION else DEFAULT_SAFE_ACTION
    }

    fun selectedActionIsUnsafe(scene: JSONObject): Boolean = isUnsafeAction(scene.optString("main_action"))

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
