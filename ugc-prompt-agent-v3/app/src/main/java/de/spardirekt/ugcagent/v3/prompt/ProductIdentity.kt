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

    const val COOKWARE_PAN_LOCK =
        "Keep the pan body, rounded deep sidewall, side handle assembly, short metal tang, wooden handle grip, metallic/gold-colored collar, hanging ring, wooden lid, raised lid handle and visible fastener heads exactly as referenced.\n" +
            "Do not add extra handles, extra lids, extra rings or a second collar.\n" +
            "Do not replace the wooden grip, lid or hanging ring with generic cookware parts."

    fun cookwarePanFingerprint(): JSONObject = JSONObject()
        .put("overall_geometry", "deep round pan body with a wooden side handle, metallic collar, hanging ring and a wooden lid")
        .put(
            "identity_critical_components",
            JSONArray()
                .put("pan body")
                .put("rounded deep sidewall")
                .put("side handle assembly")
                .put("short metal tang")
                .put("wooden handle grip")
                .put("metallic/gold-colored collar")
                .put("hanging ring")
                .put("wooden lid")
                .put("raised lid handle")
                .put("visible fastener heads"),
        )
        .put("component_count_constraints", JSONArray().put("one pan body, one lid, one side handle assembly"))
        .put("component_layout", JSONArray().put("handle assembly attached at the sidewall with collar, tang and hanging ring in their original relative positions"))
        .put("attachment_points", JSONArray().put("visible fastener heads on the handle assembly"))
        .put("moving_or_removable_parts", JSONArray().put("wooden lid").put("raised lid handle"))
        .put(
            "must_not_change",
            JSONArray()
                .put("pan body")
                .put("rounded deep sidewall")
                .put("wooden handle grip")
                .put("metallic/gold-colored collar")
                .put("hanging ring")
                .put("wooden lid")
                .put("raised lid handle")
                .put("visible fastener heads"),
        )
        .put("confidence", 0.88)

    val MICROWAVE_COVER_FEATURES = listOf(
        "Transparent dome body",
        "Green circular base ring",
        "Curved green handle",
        "Circular upper vent",
        "Two separate rectangular transparent upper modules",
        "Green caps on the rectangular modules",
        "Original relative positions of the vent and rectangular modules",
        "Visible central dome seam/rib",
        "Visible perimeter attachment layout",
        "Never replace the two rectangular modules or the circular vent with one cylindrical reservoir",
    )

    val COOKWARE_PAN_FEATURES = listOf(
        "pan body",
        "rounded deep sidewall",
        "side handle assembly",
        "short metal tang",
        "wooden handle grip",
        "metallic/gold-colored collar",
        "hanging ring",
        "wooden lid",
        "raised lid handle",
        "visible fastener heads",
    )

    val IDENTITY_POLICY_LINES = listOf(
        "Do not merge, split, omit, relocate, simplify or invent components. Do not redesign, reinterpret, replace, duplicate or morph it.",
        "Do not generate a similar product. Do not generate a generic product from the same category. A functionally similar but visually different product is a failed generation.",
    )

    private val GENERIC_IDENTITY_PADS = listOf(
        "Exact referenced silhouette from the photos",
        "Visible component count from the photos",
        "Original attachment layout",
        "Distinctive color and finish from the First Frame",
        "Identity-critical parts stay in their photo positions",
    )

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

    fun finalIdentityConstraints(fingerprint: JSONObject?): List<String> =
        compactIdentityFeatures(fingerprint)

    fun compactIdentityFeatures(fingerprint: JSONObject?, extra: List<String> = emptyList()): List<String> {
        val features = mutableListOf<String>()
        fun add(raw: String) {
            if (features.size >= 10) return
            val item = compactFeature(raw) ?: return
            if (features.any { similarFeature(it, item) }) return
            features += item
        }
        if (looksLikeMicrowaveCover(fingerprint)) {
            MICROWAVE_COVER_FEATURES.forEach(::add)
            extra.forEach(::add)
            return padIdentityFeatures(features)
        }
        if (looksLikeCookwarePan(fingerprint)) {
            COOKWARE_PAN_FEATURES.forEach(::add)
            extra.forEach(::add)
            return padIdentityFeatures(features)
        }
        addVisibleItems(fingerprint, "identity_critical_components", features)
        addVisibleItems(fingerprint, "must_not_change", features)
        addVisibleItems(fingerprint, "attachment_points", features, skipUnconfirmed = true)
        val geometry = fingerprint?.optString("overall_geometry").orEmpty().trim()
        if (geometry.length in 12..140) add(geometry)
        addVisibleItems(fingerprint, "component_layout", features)
        extra.forEach(::add)
        return padIdentityFeatures(features)
    }

    fun identityPolicyLines(): List<String> = IDENTITY_POLICY_LINES

    fun finalIdentityLockBlock(fingerprint: JSONObject?): String {
        val features = compactIdentityFeatures(fingerprint)
        return buildString {
            appendLine("PRODUCT IDENTITY LOCK:")
            features.forEachIndexed { index, line -> appendLine("${index + 1}. $line") }
            IDENTITY_POLICY_LINES.forEach { appendLine(it) }
        }.trim()
    }

    fun compactFeature(raw: String): String? {
        var text = raw.trim().replace(Regex("^\\d+\\.\\s*"), "").trim()
        if (text.isBlank()) return null
        if (isPolicyLine(text) || isInternalLeak(text) || looksLikeDimension(text) || looksLikeSellerClaim(text)) return null
        if (text.length < 8 || text.length > 140) return null
        return text.trimEnd('.', ';')
    }

    fun isPolicyLine(text: String): Boolean {
        val lower = text.lowercase().replace(Regex("^\\d+\\.\\s*"), "")
        return lower.startsWith("do not ") ||
            lower.startsWith("keep exactly the same") ||
            lower.startsWith("preserve the exact number") ||
            lower.startsWith("preserve exact component count") ||
            lower.contains("functionally similar but visually different") ||
            lower.contains("functionally equivalent but visually different") ||
            lower.contains("generic product from the same category")
    }

    private fun padIdentityFeatures(features: List<String>): List<String> {
        val out = features.take(10).toMutableList()
        GENERIC_IDENTITY_PADS.forEach { pad ->
            if (out.size >= 5) return@forEach
            if (out.none { similarFeature(it, pad) }) out += pad
        }
        return out.take(10)
    }

    private fun similarFeature(a: String, b: String): Boolean {
        val left = featureKey(a)
        val right = featureKey(b)
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true
        val seed = minOf(18, minOf(left.length, right.length))
        if (seed < 10) return false
        return left.contains(right) || right.contains(left)
    }

    private fun featureKey(text: String): String =
        text.lowercase().replace(Regex("^\\d+\\.\\s*"), "").replace(Regex("[^a-z0-9а-яё]+"), " ").trim()

    private fun looksLikeSellerClaim(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("listing") ||
            lower.contains("seller") ||
            lower.contains("bestseller") ||
            lower.contains("best seller") ||
            lower.contains("guaranteed") ||
            lower.contains("warranty") ||
            lower.contains("shop rank") ||
            lower.contains("review count")
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

    fun looksLikeCookwarePan(fingerprint: JSONObject?, analysis: JSONObject? = null): Boolean {
        if (looksLikeMicrowaveCover(fingerprint)) return false
        val blob = listOf(
            fingerprint?.toString().orEmpty(),
            analysis?.toString().orEmpty(),
            analysis?.optString("product_category").orEmpty(),
            analysis?.optString("observed_use_case").orEmpty(),
        ).joinToString(" ").lowercase()
        if (blob.isBlank()) return false
        return blob.contains("skillet") ||
            blob.contains("saucepan") ||
            blob.contains("frying pan") ||
            blob.contains("cookware") ||
            blob.contains("сковород") ||
            blob.contains("кастрюл") ||
            Regex("\\bpan\\b").containsMatchIn(blob) ||
            (
                blob.contains("wooden") &&
                    blob.contains("lid") &&
                    blob.contains("handle") &&
                    (blob.contains("collar") || blob.contains("hanging ring") || blob.contains("tang"))
                )
    }

    fun structuralLockBlock(fingerprint: JSONObject?): String = finalIdentityLockBlock(fingerprint)

    private fun addVisibleItems(fingerprint: JSONObject?, key: String, out: MutableList<String>, skipUnconfirmed: Boolean = false) {
        val arr = fingerprint?.optJSONArray(key) ?: return
        for (i in 0 until arr.length()) {
            if (out.size >= 10) return
            val item = arr.optString(i).trim()
            if (item.isBlank() || isInternalLeak(item)) continue
            if (skipUnconfirmed && (item.contains("unconfirmed", true) || item.contains("hidden", true) || item.contains("uncertain", true))) continue
            val compact = compactFeature(item) ?: continue
            if (out.any { similarFeature(it, compact) }) continue
            out.add(compact)
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

    private fun looksLikeDimension(text: String): Boolean = PromptComposer.looksLikeDimension(text)

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
