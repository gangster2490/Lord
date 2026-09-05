package de.spardirekt.ugcagent.v3.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StoredImage(
    val id: String,
    val originalPath: String,
    val compressedPath: String,
    val width: Int,
    val height: Int,
    val originalBytes: Long,
    val compressedBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("originalPath", originalPath)
        .put("compressedPath", compressedPath)
        .put("width", width)
        .put("height", height)
        .put("originalBytes", originalBytes)
        .put("compressedBytes", compressedBytes)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(obj: JSONObject) = StoredImage(
            id = obj.getString("id"),
            originalPath = obj.getString("originalPath"),
            compressedPath = obj.getString("compressedPath"),
            width = obj.optInt("width"),
            height = obj.optInt("height"),
            originalBytes = obj.optLong("originalBytes"),
            compressedBytes = obj.optLong("compressedBytes"),
            createdAt = obj.optLong("createdAt"),
        )
    }
}

class ProjectRecord(
    val id: String = UUID.randomUUID().toString(),
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var images: MutableList<StoredImage> = mutableListOf(),
    var firstFrameId: String? = null,
    var firstFrameQuality: JSONObject? = null,
    var consistency: JSONObject? = null,
    var analysis: JSONObject? = null,
    var scene: JSONObject? = null,
    var speechLanguage: String = "DEUTSCH",
    var captionLanguage: String = "DEUTSCH",
    var targetGenerator: String = "VEO",
    var strictProductLock: Boolean = true,
    var finalPrompt: String? = null,
    var improvedPrompt: String? = null,
    var caption: String? = null,
    var hashtags: MutableList<String> = mutableListOf(),
    var compliance: JSONObject? = null,
    var provider: String = "OPENAI",
    var model: String? = null,
    var consistencyOverride: Boolean = false,
    var identityFingerprint: JSONObject? = null,
    var actionRisk: JSONObject? = null,
    var identityReadiness: JSONObject? = null,
    var firstFrameRecommendation: JSONObject? = null,
    var recommendedFirstFrameId: String? = null,
    var pipelineStage: String = "IDLE",
    var resumeStage: String? = null,
    var pausedReason: String? = null,
    var pipelineError: String? = null,
    var warnings: MutableList<String> = mutableListOf(),
    var completedStages: MutableList<String> = mutableListOf(),
    var repairApplied: Boolean = false,
    var finalIdentityLock: String? = null,
    var firstFrameAutoApplied: Boolean = false,
    var firstFrameUserChosen: Boolean = false,
) {
    fun touch() {
        updatedAt = System.currentTimeMillis()
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("images", JSONArray(images.map { it.toJson() }))
        .put("firstFrameId", firstFrameId)
        .put("firstFrameQuality", firstFrameQuality)
        .put("consistency", consistency)
        .put("analysis", analysis)
        .put("scene", scene)
        .put("speechLanguage", speechLanguage)
        .put("captionLanguage", captionLanguage)
        .put("targetGenerator", targetGenerator)
        .put("strictProductLock", strictProductLock)
        .put("finalPrompt", finalPrompt)
        .put("improvedPrompt", improvedPrompt)
        .put("caption", caption)
        .put("hashtags", JSONArray(hashtags))
        .put("compliance", compliance)
        .put("provider", provider)
        .put("model", model)
        .put("consistencyOverride", consistencyOverride)
        .put("identityFingerprint", identityFingerprint)
        .put("actionRisk", actionRisk)
        .put("identityReadiness", identityReadiness)
        .put("firstFrameRecommendation", firstFrameRecommendation)
        .put("recommendedFirstFrameId", recommendedFirstFrameId)
        .put("pipelineStage", pipelineStage)
        .put("resumeStage", resumeStage)
        .put("pausedReason", pausedReason)
        .put("pipelineError", pipelineError)
        .put("warnings", JSONArray(warnings))
        .put("completedStages", JSONArray(completedStages))
        .put("repairApplied", repairApplied)
        .put("finalIdentityLock", finalIdentityLock)
        .put("firstFrameAutoApplied", firstFrameAutoApplied)
        .put("firstFrameUserChosen", firstFrameUserChosen)

    companion object {
        fun fromJson(obj: JSONObject): ProjectRecord {
            val imagesArr = obj.optJSONArray("images") ?: JSONArray()
            val tags = obj.optJSONArray("hashtags") ?: JSONArray()
            return ProjectRecord(
                id = obj.getString("id"),
                createdAt = obj.optLong("createdAt"),
                updatedAt = obj.optLong("updatedAt"),
                images = MutableList(imagesArr.length()) { StoredImage.fromJson(imagesArr.getJSONObject(it)) },
                firstFrameId = obj.optString("firstFrameId").ifBlank { null },
                firstFrameQuality = obj.optJSONObject("firstFrameQuality"),
                consistency = obj.optJSONObject("consistency"),
                analysis = obj.optJSONObject("analysis"),
                scene = obj.optJSONObject("scene"),
                speechLanguage = obj.optString("speechLanguage", "DEUTSCH"),
                captionLanguage = obj.optString("captionLanguage", "DEUTSCH"),
                targetGenerator = obj.optString("targetGenerator", "VEO"),
                strictProductLock = obj.optBoolean("strictProductLock", true),
                finalPrompt = obj.optString("finalPrompt").ifBlank { null },
                improvedPrompt = obj.optString("improvedPrompt").ifBlank { null },
                caption = obj.optString("caption").ifBlank { null },
                hashtags = MutableList(tags.length()) { tags.optString(it) },
                compliance = obj.optJSONObject("compliance"),
                provider = obj.optString("provider", "OPENAI"),
                model = obj.optString("model").ifBlank { null },
                consistencyOverride = obj.optBoolean("consistencyOverride", false),
                identityFingerprint = obj.optJSONObject("identityFingerprint"),
                actionRisk = obj.optJSONObject("actionRisk"),
                identityReadiness = obj.optJSONObject("identityReadiness"),
                firstFrameRecommendation = obj.optJSONObject("firstFrameRecommendation"),
                recommendedFirstFrameId = obj.optString("recommendedFirstFrameId").ifBlank { null },
                pipelineStage = obj.optString("pipelineStage", "IDLE").ifBlank { "IDLE" },
                resumeStage = obj.optString("resumeStage").ifBlank { null },
                pausedReason = obj.optString("pausedReason").ifBlank { null },
                pipelineError = obj.optString("pipelineError").ifBlank { null },
                warnings = stringList(obj.optJSONArray("warnings")),
                completedStages = stringList(obj.optJSONArray("completedStages")),
                repairApplied = obj.optBoolean("repairApplied", false),
                finalIdentityLock = obj.optString("finalIdentityLock").ifBlank { null },
                firstFrameAutoApplied = obj.optBoolean("firstFrameAutoApplied", false),
                firstFrameUserChosen = obj.optBoolean("firstFrameUserChosen", false),
            )
        }

        private fun stringList(arr: JSONArray?): MutableList<String> {
            if (arr == null) return mutableListOf()
            return MutableList(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }.toMutableList()
        }
    }
}
