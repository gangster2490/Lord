package de.spardirekt.ugcagent.v3.pipeline

import de.spardirekt.ugcagent.v3.image.FirstFrameHeuristics
import org.json.JSONArray
import org.json.JSONObject

data class PipelineImage(
    val id: String,
    val index: Int,
    val width: Int,
    val height: Int,
    val compressedBytes: Long,
)

class PipelineSession {
    var stage: PipelineStage = PipelineStage.IDLE
    var resumeStage: PipelineStage? = null
    var pausedReason: String? = null
    var errorMessage: String? = null
    var warnings: MutableList<String> = mutableListOf()
    var completed: MutableSet<PipelineStage> = linkedSetOf()
    var repairApplied: Boolean = false
    var firstFrameAutoApplied: Boolean = false
    var firstFrameUserChosen: Boolean = false
    var firstFrameId: String? = null
    var recommendedFirstFrameId: String? = null
    var consistencyOverride: Boolean = false
    var speechLanguage: String = "DEUTSCH"
    var captionLanguage: String = "DEUTSCH"
    var targetGenerator: String = "VEO"
    var strictProductLock: Boolean = true
    var hasApiKey: Boolean = true
    var images: List<PipelineImage> = emptyList()
    var consistency: JSONObject? = null
    var analysis: JSONObject? = null
    var identityFingerprint: JSONObject? = null
    var identityReadiness: JSONObject? = null
    var firstFrameQuality: JSONObject? = null
    var firstFrameRecommendation: JSONObject? = null
    var actionRisk: JSONObject? = null
    var scene: JSONObject? = null
    var finalIdentityLock: String? = null
    var finalPrompt: String? = null
    var caption: String? = null
    var hashtags: MutableList<String> = mutableListOf()
    var compliance: JSONObject? = null
    var details: String? = null
    var autoRetried: Boolean = false
    var forceStaticAction: Boolean = false
    var dominantImageIndices: List<Int> = emptyList()

    fun isDominantIndex(index: Int): Boolean =
        dominantImageIndices.isEmpty() || index in dominantImageIndices

    fun rankedImages(): List<FirstFrameHeuristics.RankedImage> = images.map { image ->
        val base = FirstFrameHeuristics.score(image.width, image.height, image.compressedBytes)
        val score = if (isDominantIndex(image.index)) base else base - 20.0
        FirstFrameHeuristics.RankedImage(
            id = image.id,
            index = image.index,
            width = image.width,
            height = image.height,
            compressedBytes = image.compressedBytes,
            score = score,
        )
    }

    fun completedNames(): JSONArray = JSONArray(completed.map { it.name })

    companion object {
        fun parseCompleted(arr: JSONArray?): MutableSet<PipelineStage> {
            val out = linkedSetOf<PipelineStage>()
            if (arr == null) return out
            for (i in 0 until arr.length()) {
                out.add(PipelineStage.fromName(arr.optString(i)))
            }
            return out
        }
    }
}
