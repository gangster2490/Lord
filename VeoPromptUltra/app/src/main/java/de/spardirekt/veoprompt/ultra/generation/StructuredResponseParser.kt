package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.diagnostics.DebugLog
import de.spardirekt.veoprompt.ultra.model.CreativeDirection
import de.spardirekt.veoprompt.ultra.model.ImageAnalysisBlock
import de.spardirekt.veoprompt.ultra.model.ImageClassification
import de.spardirekt.veoprompt.ultra.model.SafetyAudit
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Strongly typed decode of the FINAL_PROMPT JSON.
 * Arrays stay arrays — never cast to JsonPrimitive.
 */
object StructuredResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun parse(raw: String): StructuredResponse {
        val attempts = listOf(JsonExtractor.extract(raw), JsonExtractor.repair(raw)).distinct()
        for (payload in attempts) {
            val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
            val parsed = fromObject(obj)
            if (parsed.veoPrompt.isNotBlank() || parsed.title.isNotBlank() || parsed.hashtags.isNotEmpty()) {
                return parsed
            }
        }
        DebugLog.d("StructuredResponse parse produced empty package")
        return StructuredResponse()
    }

    fun fromObject(obj: JsonObject): StructuredResponse {
        val veo = readString(obj, "veoPrompt").ifBlank { readString(obj, "mainPrompt") }
        return StructuredResponse(
            imageAnalysis = readImageAnalysis(obj["imageAnalysis"]),
            creativeDirection = readCreative(obj["creativeDirection"]),
            veoPrompt = veo,
            voiceover = readString(obj, "voiceover"),
            title = readString(obj, "title"),
            hashtags = readStringArray(obj["hashtags"]),
            safetyAudit = readSafety(obj["safetyAudit"])
        )
    }

    private fun readString(obj: JsonObject, key: String): String {
        val el = obj[key] ?: return ""
        return when (el) {
            is JsonPrimitive -> el.contentOrNull.orEmpty().trim()
            is JsonArray -> el.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject -> item.values.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
                    else -> null
                }
            }.joinToString("\n").trim()
            else -> ""
        }
    }

    private fun readStringArray(el: JsonElement?): List<String> {
        if (el == null) return emptyList()
        return when (el) {
            is JsonArray -> el.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    else -> null
                }
            }
            is JsonPrimitive -> el.contentOrNull.orEmpty()
                .split(Regex("[,\\s]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun readImageAnalysis(el: JsonElement?): ImageAnalysisBlock {
        val obj = el as? JsonObject ?: return ImageAnalysisBlock()
        return ImageAnalysisBlock(
            productCategory = readString(obj, "productCategory"),
            productIdentity = readString(obj, "productIdentity"),
            visualSignature = readStringArray(obj["visualSignature"]),
            verifiedFeatures = readStringArray(obj["verifiedFeatures"]),
            uncertainFacts = readStringArray(obj["uncertainFacts"]),
            imageTypes = readClassifications(obj["imageTypes"] ?: obj["classifications"])
        )
    }

    private fun readClassifications(el: JsonElement?): List<ImageClassification> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            ImageClassification(
                imageId = (o["imageId"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                category = (o["category"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                notes = (o["notes"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            )
        }
    }

    private fun readCreative(el: JsonElement?): CreativeDirection {
        val obj = el as? JsonObject ?: return CreativeDirection()
        return CreativeDirection(
            selectedMode = readString(obj, "selectedMode").ifBlank { readString(obj, "strategy") },
            heroFeature = readString(obj, "heroFeature"),
            reason = readString(obj, "reason").ifBlank { readString(obj, "rationale") },
            setting = readString(obj, "setting").ifBlank { "premium studio" },
            hookIdea = readString(obj, "hookIdea"),
            useHands = (obj["useHands"] as? JsonPrimitive)?.contentOrNull.toBoolean(),
            usePeople = (obj["usePeople"] as? JsonPrimitive)?.contentOrNull.toBoolean()
        )
    }

    private fun readSafety(el: JsonElement?): SafetyAudit {
        val obj = el as? JsonObject ?: return SafetyAudit()
        val items = when (val raw = obj["items"]) {
            is JsonArray -> readStringArray(raw)
            is JsonPrimitive -> listOfNotNull(raw.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
            else -> emptyList()
        }
        return SafetyAudit(
            riskLevel = readString(obj, "riskLevel").ifBlank { "LOW" },
            items = items,
            policyVersion = readString(obj, "policyVersion")
        )
    }
}
