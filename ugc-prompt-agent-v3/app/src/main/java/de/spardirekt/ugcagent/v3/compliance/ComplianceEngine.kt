package de.spardirekt.ugcagent.v3.compliance

import de.spardirekt.ugcagent.v3.ai.JsonExtractor
import org.json.JSONArray
import org.json.JSONObject

object ComplianceEngine {
    fun review(
        prompt: String,
        speech: String,
        caption: String,
        hashtags: List<String>,
        analysis: JSONObject?,
        semantic: JSONObject?,
        commercialCaption: Boolean = true,
    ): JSONObject {
        val combined = listOf(prompt, speech, caption, hashtags.joinToString(" ")).joinToString("\n")
        val warnings = JSONArray()
        val blocked = JSONArray()
        val claims = JSONArray()
        val supported = JSONArray()
        val unsupported = JSONArray()

        match(combined, TikTokShopPolicyConfig.absoluteClaimPatterns).forEach {
            claims.put(it)
            warnings.put("Absolute claim without independent evidence: $it")
            unsupported.put(it)
        }
        match(combined, TikTokShopPolicyConfig.medicalPatterns).forEach {
            claims.put(it)
            blocked.put("Medical claim: $it")
            unsupported.put(it)
        }
        match(combined, TikTokShopPolicyConfig.shippingPatterns).forEach {
            claims.put(it)
            warnings.put("Shipping claim without evidence: $it")
            unsupported.put(it)
        }
        match(combined, TikTokShopPolicyConfig.scarcityPatterns).forEach {
            claims.put(it)
            warnings.put("Urgency/scarcity language: $it")
            unsupported.put(it)
        }
        match(combined, TikTokShopPolicyConfig.comparativePatterns).forEach {
            claims.put(it)
            warnings.put("Comparative claim without evidence: $it")
            unsupported.put(it)
        }
        match(combined, TikTokShopPolicyConfig.offPlatformPatterns).forEach {
            blocked.put("Off-platform redirection: $it")
        }

        val lower = combined.lowercase()
        if (TikTokShopPolicyConfig.restrictedCategoryKeywords.any { lower.contains(it) }) {
            warnings.put("Dieses Produkt kann unter eine eingeschränkte oder verbotene TikTok-Shop-Kategorie fallen.")
        }

        val evidenceText = evidenceBlob(analysis)
        JsonExtractor.stringList(analysis ?: JSONObject(), "text_claims").forEach { claim ->
            if (claim.isNotBlank()) {
                supported.put("source text claim: $claim")
            }
        }

        TikTokShopPolicyConfig.certificationWords.forEach { word ->
            if (lower.contains(word) && !evidenceText.contains(word)) {
                warnings.put("Certification-like wording without source evidence: $word")
                unsupported.put(word)
            }
        }

        if (MarketplaceFilter.containsMarketplaceUi(combined)) {
            warnings.put("Marketplace UI data must not appear in generated content.")
            unsupported.put("marketplace UI")
        }

        if (commercialCaption && caption.isNotBlank() && !TikTokShopPolicyConfig.disclosurePattern.containsMatchIn(caption)) {
            warnings.put("Commercial disclosure missing: add Werbung or Anzeige.")
        }

        semantic?.optJSONArray("warnings")?.let { arr ->
            for (i in 0 until arr.length()) warnings.put(arr.optString(i))
        }
        semantic?.optJSONArray("blocked_reasons")?.let { arr ->
            for (i in 0 until arr.length()) blocked.put(arr.optString(i))
        }
        semantic?.optJSONArray("unsupported_claims")?.let { arr ->
            for (i in 0 until arr.length()) unsupported.put(arr.optString(i))
        }
        semantic?.optJSONArray("evidence_supported_claims")?.let { arr ->
            for (i in 0 until arr.length()) supported.put(arr.optString(i))
        }

        val status = when {
            blocked.length() > 0 -> "BLOCK"
            warnings.length() > 0 -> "WARNING"
            else -> "PASS"
        }

        return JSONObject()
            .put("status", status)
            .put("warnings", warnings)
            .put("blocked_reasons", blocked)
            .put("claims_detected", claims)
            .put("evidence_supported_claims", supported)
            .put("unsupported_claims", unsupported)
            .put("policy_version", TikTokShopPolicyConfig.VERSION)
            .put("policy_updated", TikTokShopPolicyConfig.LAST_UPDATED)
    }

    fun addWerbung(caption: String): String {
        if (TikTokShopPolicyConfig.disclosurePattern.containsMatchIn(caption)) return caption
        return if (caption.isBlank()) "Werbung" else caption.trimEnd() + "\nWerbung"
    }

    private fun match(text: String, patterns: List<Pair<String, String>>): List<String> {
        return patterns.mapNotNull { (pattern, label) ->
            if (Regex(pattern, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(text)) label else null
        }
    }

    private fun evidenceBlob(analysis: JSONObject?): String {
        if (analysis == null) return ""
        val parts = mutableListOf<String>()
        listOf("text_claims", "dimensions", "usage_instructions", "observed_use_case", "visual_features_relevant_to_use").forEach { key ->
            parts += JsonExtractor.stringList(analysis, key)
            if (analysis.has(key) && analysis.opt(key) is String) parts += analysis.optString(key)
        }
        return parts.joinToString(" ").lowercase()
    }
}

object MarketplaceFilter {
    private val patterns = listOf(
        Regex("earn\\s*€?\\s*\\d", RegexOption.IGNORE_CASE),
        Regex("\\d+\\s*%\\s*commission", RegexOption.IGNORE_CASE),
        Regex("#\\d+\\s*ranking", RegexOption.IGNORE_CASE),
        Regex("best seller badge", RegexOption.IGNORE_CASE),
        Regex("affiliate commission", RegexOption.IGNORE_CASE),
        Regex("free sample ui", RegexOption.IGNORE_CASE),
        Regex("seller rank", RegexOption.IGNORE_CASE),
    )

    fun containsMarketplaceUi(text: String): Boolean = patterns.any { it.containsMatchIn(text) }

    fun ignoreInstructions(): String = buildString {
        appendLine("Ignore marketplace UI completely. It is not product information.")
        TikTokShopPolicyConfig.marketplaceIgnore.forEach { appendLine("- $it") }
        appendLine("Examples that are NOT product features: Earn €1.44 per sale, 8% commission, #3 ranking.")
    }
}
