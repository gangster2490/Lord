package de.spardirekt.ugcagent.v3.prompt

import de.spardirekt.ugcagent.v3.ai.JsonExtractor
import org.json.JSONArray
import org.json.JSONObject

object EvidenceModel {
    val performanceClaimPatterns: List<Regex> = listOf(
        Regex("bpa[- ]?free", RegexOption.IGNORE_CASE),
        Regex("без\\s*bpa", RegexOption.IGNORE_CASE),
        Regex("anti[- ]?scratch", RegexOption.IGNORE_CASE),
        Regex("антицарап", RegexOption.IGNORE_CASE),
        Regex("kratzfest", RegexOption.IGNORE_CASE),
        Regex("moisture retention", RegexOption.IGNORE_CASE),
        Regex("preserves moisture", RegexOption.IGNORE_CASE),
        Regex("сохраня(ет|ют)?\\s+влаг", RegexOption.IGNORE_CASE),
        Regex("feuchtigkeit bewahr", RegexOption.IGNORE_CASE),
        Regex("bewahrt feuchtigkeit", RegexOption.IGNORE_CASE),
        Regex("makes food softer", RegexOption.IGNORE_CASE),
        Regex("еда[а-я]*\\s+мягче", RegexOption.IGNORE_CASE),
        Regex("macht das essen weicher", RegexOption.IGNORE_CASE),
        Regex("improves taste", RegexOption.IGNORE_CASE),
        Regex("улучша(ет|ют)?\\s+вкус", RegexOption.IGNORE_CASE),
        Regex("schmeckt besser", RegexOption.IGNORE_CASE),
        Regex("creates steam", RegexOption.IGNORE_CASE),
        Regex("образуе[т]?\\s+пар", RegexOption.IGNORE_CASE),
        Regex("erzeugt dampf", RegexOption.IGNORE_CASE),
        Regex("easier to use", RegexOption.IGNORE_CASE),
        Regex("легче\\s+(использовать|готовить)", RegexOption.IGNORE_CASE),
        Regex("einfacher zu (verwenden|benutzen)", RegexOption.IGNORE_CASE),
        Regex("heating performance", RegexOption.IGNORE_CASE),
        Regex("гарантированн(ый|ое)\\s+нагрев", RegexOption.IGNORE_CASE),
        Regex("heizleistung", RegexOption.IGNORE_CASE),
        Regex("maximum (time|minutes|temperature)", RegexOption.IGNORE_CASE),
        Regex("\\d+\\s*°\\s*c", RegexOption.IGNORE_CASE),
        Regex("dishwasher safe", RegexOption.IGNORE_CASE),
        Regex("spülmaschinengeeignet", RegexOption.IGNORE_CASE),
        Regex("non[- ]stick", RegexOption.IGNORE_CASE),
    )

    val marketingClaimPatterns: List<Regex> = listOf(
        Regex("\\b(best|perfect|always|never|guaranteed|100%|revolutionary|game[- ]changing)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(beste[rsn]?|perfekt|garantiert)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(лучший|идеальн|гарантир|всегда|никогда)\\b", RegexOption.IGNORE_CASE),
    )

    private val splashBenefit = listOf(
        Regex("уменьш\\w*\\s+количество\\s+брызг", RegexOption.IGNORE_CASE),
        Regex("reduce[s]?\\s+(splashes?|spray)", RegexOption.IGNORE_CASE),
        Regex("spritzer\\s+(zu\\s+)?reduzier", RegexOption.IGNORE_CASE),
        Regex("weniger\\s+spritzer", RegexOption.IGNORE_CASE),
        Regex("меньше\\s+пачкать", RegexOption.IGNORE_CASE),
    )

    fun classify(analysis: JSONObject?): JSONObject {
        val source = analysis ?: JSONObject()
        val visual = JsonExtractor.stringList(source, "visual_features_relevant_to_use").filter { !isBanned(it) }
        val verified = JsonExtractor.stringList(source, "verified_claims") +
            JsonExtractor.stringList(source, "reliable_claims")
        val claims = JsonExtractor.stringList(source, "text_claims")
        val uncertain = mutableListOf<String>()
        claims.filter { isBanned(it) || looksUncertain(it) || looksLikePerformanceClaim(it) }.forEach { uncertain += it }
        val sellerOnly = claims.filter { it !in uncertain }
        val warning = source.optString("ambiguity_warning")
        if (warning.isNotBlank()) uncertain += warning
        val dimensions = JsonExtractor.stringList(source, "dimensions")
        if (dimensions.map { it.trim().lowercase() }.distinct().size > 1) {
            uncertain += "conflicting dimensions omitted"
        }
        return JSONObject()
            .put("visually_confirmed", JSONArray(visual.distinct()))
            .put("verified_reliable", JSONArray(verified.filter { !isBanned(it) }.distinct()))
            .put("seller_text_claims", JSONArray(sellerOnly.distinct()))
            .put("conflicting_or_uncertain", JSONArray(uncertain.distinct()))
    }

    fun omitUnverified(text: String): String {
        var next = text
        (performanceClaimPatterns + marketingClaimPatterns).forEach { next = it.replace(next, "") }
        return next.replace(Regex(" +"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun containsUnverifiedClaim(text: String): Boolean =
        performanceClaimPatterns.any { it.containsMatchIn(text) } ||
            marketingClaimPatterns.any { it.containsMatchIn(text) }

    fun containsPerformanceClaim(text: String): Boolean =
        performanceClaimPatterns.any { it.containsMatchIn(text) }

    fun containsUnverifiedSplashBenefit(text: String): Boolean =
        splashBenefit.any { it.containsMatchIn(text) }

    fun isBanned(text: String): Boolean = containsUnverifiedClaim(text)

    fun looksLikePerformanceClaim(text: String): Boolean {
        val lower = text.lowercase()
        return containsPerformanceClaim(text) ||
            containsUnverifiedSplashBenefit(text) ||
            lower.contains("preserves") ||
            lower.contains("improves") ||
            lower.contains("guarantees")
    }

    fun looksUncertain(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("unverified") ||
            lower.contains("seller claim") ||
            lower.contains("may be") ||
            lower.contains("possibly") ||
            lower.contains("not visually")
    }

    fun visuallyConfirmed(evidence: JSONObject?, analysis: JSONObject?): List<String> {
        val fromEvidence = JsonExtractor.stringList(evidence ?: JSONObject(), "visually_confirmed")
        val fromAnalysis = JsonExtractor.stringList(analysis ?: JSONObject(), "visual_features_relevant_to_use")
        return (fromEvidence + fromAnalysis).map { it.trim() }.filter { it.isNotBlank() && !isBanned(it) }.distinct()
    }

    fun verifiedReliable(evidence: JSONObject?, analysis: JSONObject?): List<String> {
        val fromEvidence = JsonExtractor.stringList(evidence ?: JSONObject(), "verified_reliable")
        val fromAnalysis = JsonExtractor.stringList(analysis ?: JSONObject(), "verified_claims") +
            JsonExtractor.stringList(analysis ?: JSONObject(), "reliable_claims")
        return (fromEvidence + fromAnalysis).map { it.trim() }.filter { it.isNotBlank() && !isBanned(it) }.distinct()
    }

    fun sellerOrUncertain(evidence: JSONObject?, analysis: JSONObject?): List<String> {
        val source = evidence ?: classify(analysis)
        return (
            JsonExtractor.stringList(source, "seller_text_claims") +
                JsonExtractor.stringList(source, "conflicting_or_uncertain") +
                JsonExtractor.stringList(analysis ?: JSONObject(), "text_claims")
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    fun captionContainsSellerClaim(text: String, evidence: JSONObject?, analysis: JSONObject?): Boolean {
        val body = text.lowercase()
        return sellerOrUncertain(evidence, analysis).any { claim ->
            val needle = claim.trim().lowercase()
            needle.length >= 8 && body.contains(needle)
        }
    }

    fun splashVisuallyConfirmed(evidence: JSONObject?, analysis: JSONObject?): Boolean {
        val visual = visuallyConfirmed(evidence, analysis).joinToString(" ").lowercase()
        val uncertain = sellerOrUncertain(evidence, analysis).joinToString(" ").lowercase()
        val splashWords = listOf("splash", "брызг", "spritz")
        if (splashWords.any { uncertain.contains(it) } && splashWords.none { visual.contains(it) }) return false
        return splashWords.any { visual.contains(it) }
    }

    fun sanitizePromptBody(prompt: String): String {
        val speech = Regex(
            "(?is)(?:^|\\n)SPEECH:\\s*.*?(?=\\n(?:FORMAT|REFERENCE|FINAL IDENTITY LOCK|MOVING COMPONENT LOCK|SETTING|CAMERA|SAFE ACTION|ACTION|HUMAN BEHAVIOUR|LIGHTING|ANTI-MORPH|DURATION|Target generator)\\b|$)",
        )
        val speechBlocks = speech.findAll(prompt).map { it.value }.toList()
        var body = speech.replace(prompt, "\n")
        body = body.lineSequence().joinToString("\n") { line ->
            if (isInstructionLine(line)) line
            else if (containsPerformanceClaim(line) || containsUnverifiedSplashBenefit(line)) ""
            else line
        }
        val keptSpeech = speechBlocks.joinToString("\n\n") { block ->
            block.lineSequence().joinToString("\n") { line ->
                if (containsPerformanceClaim(line)) "" else line
            }
        }
        return (body.trim() + if (keptSpeech.isBlank()) "" else "\n\n" + keptSpeech.trim())
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun sanitizeHashtags(tags: List<String>): List<String> =
        tags.map { it.trim() }.filter { it.isNotBlank() && !isBanned(it) && !containsUnverifiedSplashBenefit(it) }

    fun hashtagCountOk(tags: List<String>): Boolean = tags.size in 4..6

    private fun isInstructionLine(line: String): Boolean {
        val lower = line.trim().lowercase()
        return lower.startsWith("do not") ||
            lower.startsWith("never ") ||
            lower.startsWith("keep ") ||
            lower.startsWith("preserve ") ||
            lower.contains("identity lock") ||
            lower.contains("anti-morph")
    }
}
