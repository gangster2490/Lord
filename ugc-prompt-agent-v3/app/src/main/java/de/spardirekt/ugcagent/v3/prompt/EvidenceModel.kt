package de.spardirekt.ugcagent.v3.prompt

import de.spardirekt.ugcagent.v3.ai.JsonExtractor
import org.json.JSONArray
import org.json.JSONObject

object EvidenceModel {
    private val banned = listOf(
        Regex("bpa[- ]?free", RegexOption.IGNORE_CASE),
        Regex("anti[- ]?scratch", RegexOption.IGNORE_CASE),
        Regex("\\b(best|perfect|always|never|guaranteed|100%|revolutionary|game[- ]changing)\\b", RegexOption.IGNORE_CASE),
        Regex("moisture retention", RegexOption.IGNORE_CASE),
        Regex("heating performance", RegexOption.IGNORE_CASE),
        Regex("maximum (time|minutes|temperature)", RegexOption.IGNORE_CASE),
        Regex("\\d+\\s*°\\s*c", RegexOption.IGNORE_CASE),
        Regex("\\d+\\s*min(ute)?s?", RegexOption.IGNORE_CASE),
        Regex("dishwasher safe", RegexOption.IGNORE_CASE),
        Regex("non[- ]stick", RegexOption.IGNORE_CASE),
    )

    fun classify(analysis: JSONObject?): JSONObject {
        val source = analysis ?: JSONObject()
        val visual = JsonExtractor.stringList(source, "visual_features_relevant_to_use").filter { !isBanned(it) }
        val claims = JsonExtractor.stringList(source, "text_claims")
        val verifiedText = claims.filter { !isBanned(it) && !looksUncertain(it) }
        val uncertain = mutableListOf<String>()
        claims.filter { isBanned(it) || looksUncertain(it) }.forEach { uncertain += it }
        val warning = source.optString("ambiguity_warning")
        if (warning.isNotBlank()) uncertain += warning
        val dimensions = JsonExtractor.stringList(source, "dimensions")
        if (dimensions.map { it.trim().lowercase() }.distinct().size > 1) {
            uncertain += "conflicting dimensions omitted"
        }
        return JSONObject()
            .put("visually_confirmed", JSONArray(visual))
            .put("seller_text_claims", JSONArray(verifiedText))
            .put("conflicting_or_uncertain", JSONArray(uncertain.distinct()))
    }

    fun omitUnverified(text: String): String {
        var next = text
        banned.forEach { next = it.replace(next, "") }
        return next.replace(Regex(" +"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun isBanned(text: String): Boolean = banned.any { it.containsMatchIn(text) }

    fun looksUncertain(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("unverified") ||
            lower.contains("seller claim") ||
            lower.contains("may be") ||
            lower.contains("possibly")
    }

    fun hashtagCountOk(tags: List<String>): Boolean = tags.size in 4..6
}
