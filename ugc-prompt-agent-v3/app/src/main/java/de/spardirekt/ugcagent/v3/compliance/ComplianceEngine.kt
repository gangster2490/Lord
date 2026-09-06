package de.spardirekt.ugcagent.v3.compliance

import de.spardirekt.ugcagent.v3.ai.JsonExtractor
import de.spardirekt.ugcagent.v3.prompt.CaptionEngine
import de.spardirekt.ugcagent.v3.prompt.EvidenceModel
import org.json.JSONArray
import org.json.JSONObject

object ComplianceEngine {
    data class FixedOutputs(
        val prompt: String,
        val caption: String,
        val hashtags: List<String>,
        val review: JSONObject,
    )

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
        val notes = JSONArray()
        val blocked = JSONArray()
        val claims = JSONArray()
        val supported = JSONArray()
        val unsupported = JSONArray()

        match(combined, TikTokShopPolicyConfig.absoluteClaimPatterns).forEach {
            claims.put(it)
            unsupported.put(it)
            warnings.put("Absolute claim without independent evidence: $it")
        }
        match(combined, TikTokShopPolicyConfig.medicalPatterns).forEach {
            claims.put(it)
            blocked.put("Medical claim: $it")
            unsupported.put(it)
        }
        match(combined, TikTokShopPolicyConfig.shippingPatterns).forEach {
            claims.put(it)
            unsupported.put(it)
            warnings.put("Shipping claim without evidence: $it")
        }
        match(combined, TikTokShopPolicyConfig.scarcityPatterns).forEach {
            claims.put(it)
            unsupported.put(it)
            warnings.put("Urgency/scarcity language: $it")
        }
        match(combined, TikTokShopPolicyConfig.comparativePatterns).forEach {
            claims.put(it)
            unsupported.put(it)
            warnings.put("Comparative claim without evidence: $it")
        }
        match(combined, TikTokShopPolicyConfig.offPlatformPatterns).forEach {
            blocked.put("Off-platform redirection: $it")
        }

        val lower = combined.lowercase()
        if (TikTokShopPolicyConfig.restrictedCategoryKeywords.any { lower.contains(it) }) {
            notes.put("Dieses Produkt kann unter eine eingeschränkte oder verbotene TikTok-Shop-Kategorie fallen.")
        }

        EvidenceModel.visuallyConfirmed(null, analysis).forEach { supported.put(it) }
        EvidenceModel.verifiedReliable(null, analysis).forEach { supported.put("verified: $it") }

        TikTokShopPolicyConfig.certificationWords.forEach { word ->
            if (lower.contains(word) && !evidenceBlob(analysis).contains(word)) {
                unsupported.put(word)
                warnings.put("Certification-like wording without source evidence: $word")
            }
        }

        if (MarketplaceFilter.containsMarketplaceUi(combined)) {
            unsupported.put("marketplace UI")
            warnings.put("Marketplace UI data must not appear in generated content.")
        }

        if (commercialCaption && caption.isNotBlank() && !TikTokShopPolicyConfig.disclosurePattern.containsMatchIn(caption)) {
            notes.put("Commercial disclosure missing: add Werbung or Anzeige.")
        }

        semantic?.optJSONArray("warnings")?.let { arr ->
            for (i in 0 until arr.length()) {
                val text = arr.optString(i)
                if (text.isNotBlank()) notes.put("semantic: $text")
            }
        }
        semantic?.optJSONArray("blocked_reasons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val text = arr.optString(i)
                if (text.isNotBlank() && isHardBlockReason(text, combined)) blocked.put(text)
                else if (text.isNotBlank()) notes.put("semantic block ignored: $text")
            }
        }
        semantic?.optJSONArray("unsupported_claims")?.let { arr ->
            for (i in 0 until arr.length()) {
                val text = arr.optString(i)
                if (text.isNotBlank()) notes.put("semantic unsupported: $text")
            }
        }

        val status = when {
            blocked.length() > 0 -> "BLOCK"
            else -> "PASS"
        }

        return JSONObject()
            .put("status", status)
            .put("warnings", warnings)
            .put("notes", notes)
            .put("blocked_reasons", blocked)
            .put("claims_detected", claims)
            .put("evidence_supported_claims", supported)
            .put("unsupported_claims", unsupported)
            .put("policy_version", TikTokShopPolicyConfig.VERSION)
            .put("policy_updated", TikTokShopPolicyConfig.LAST_UPDATED)
    }

    fun enforceAndFix(
        prompt: String,
        caption: String,
        hashtags: List<String>,
        analysis: JSONObject?,
        evidence: JSONObject?,
        fingerprint: JSONObject?,
        language: String,
        commercialCaption: Boolean = true,
    ): FixedOutputs {
        var nextPrompt = EvidenceModel.sanitizePromptBody(MarketplaceFilter.stripFromText(prompt))
        var nextCaption = CaptionEngine.finalize(
            raw = caption,
            analysis = analysis,
            evidence = evidence,
            fingerprint = fingerprint,
            language = language,
            appendDisclosure = commercialCaption,
        )
        var nextTags = EvidenceModel.sanitizeHashtags(hashtags)
        var review = review(nextPrompt, nextPrompt, nextCaption, nextTags, analysis, null, commercialCaption)
        if (review.optJSONArray("unsupported_claims")?.length() ?: 0 > 0) {
            nextPrompt = EvidenceModel.sanitizePromptBody(stripUnsupportedPhrases(nextPrompt, review))
            nextCaption = CaptionEngine.finalize(
                raw = nextCaption,
                analysis = analysis,
                evidence = evidence,
                fingerprint = fingerprint,
                language = language,
                appendDisclosure = commercialCaption,
            )
            nextTags = EvidenceModel.sanitizeHashtags(nextTags)
            review = review(nextPrompt, nextPrompt, nextCaption, nextTags, analysis, null, commercialCaption)
        }
        if (review.optString("status") != "BLOCK") {
            val leftover = review.optJSONArray("warnings") ?: JSONArray()
            val notes = review.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until leftover.length()) notes.put(leftover.optString(i))
            review.put("warnings", JSONArray())
            review.put("notes", notes)
            review.put("status", "PASS")
        }
        return FixedOutputs(nextPrompt, nextCaption, nextTags, review)
    }

    fun addWerbung(caption: String): String = addDisclosure(caption, "DEUTSCH")

    fun addDisclosure(caption: String, language: String): String {
        if (TikTokShopPolicyConfig.disclosurePattern.containsMatchIn(caption)) return caption
        val label = if (language.equals("РУССКИЙ", true)) "Anzeige" else "Werbung"
        return if (caption.isBlank()) label else caption.trimEnd() + "\n$label"
    }

    private fun isHardBlockReason(reason: String, combined: String): Boolean {
        val lower = reason.lowercase()
        return (lower.contains("medical") || lower.contains("heilt") || lower.contains("off-platform") || lower.contains("url")) &&
            (TikTokShopPolicyConfig.medicalPatterns.any { Regex(it.first, RegexOption.IGNORE_CASE).containsMatchIn(combined) } ||
                TikTokShopPolicyConfig.offPlatformPatterns.any { Regex(it.first, RegexOption.IGNORE_CASE).containsMatchIn(combined) })
    }

    private fun stripUnsupportedPhrases(text: String, review: JSONObject): String {
        var next = text
        val unsupported = review.optJSONArray("unsupported_claims") ?: return next
        for (i in 0 until unsupported.length()) {
            val phrase = unsupported.optString(i).trim()
            if (phrase.length >= 3) next = next.replace(phrase, "", ignoreCase = true)
        }
        return next.replace(Regex(" +"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun match(text: String, patterns: List<Pair<String, String>>): List<String> {
        return patterns.mapNotNull { (pattern, label) ->
            if (Regex(pattern, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(text)) label else null
        }
    }

    private fun evidenceBlob(analysis: JSONObject?): String {
        if (analysis == null) return ""
        val parts = mutableListOf<String>()
        listOf("visual_features_relevant_to_use", "verified_claims", "reliable_claims", "observed_use_case").forEach { key ->
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

    fun stripFromText(text: String): String {
        var next = text
        patterns.forEach { next = it.replace(next, "") }
        return next.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun ignoreInstructions(): String = buildString {
        appendLine("Ignore marketplace UI completely. It is not product information.")
        TikTokShopPolicyConfig.marketplaceIgnore.forEach { appendLine("- $it") }
        appendLine("Examples that are NOT product features: Earn €1.44 per sale, 8% commission, #3 ranking.")
    }
}
