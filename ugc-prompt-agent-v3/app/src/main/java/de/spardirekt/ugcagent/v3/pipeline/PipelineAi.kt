package de.spardirekt.ugcagent.v3.pipeline

import de.spardirekt.ugcagent.v3.ai.PromptContext
import org.json.JSONObject

interface PipelineAi {
    fun consistencyCheck(): JSONObject
    fun analyseProduct(): JSONObject
    fun fingerprint(): JSONObject
    fun readiness(fingerprint: JSONObject): JSONObject
    fun recommendFirstFrame(): JSONObject
    fun firstFrameQuality(imageIndex: Int): JSONObject
    fun generateScene(analysis: JSONObject, fingerprint: JSONObject, previous: JSONObject?): JSONObject
    fun actionRisk(fingerprint: JSONObject, scene: JSONObject): JSONObject
    fun generatePrompt(ctx: PromptContext): String
    fun checkCompliance(prompt: String, analysis: JSONObject?, caption: String, hashtags: List<String>): JSONObject
    fun generateCaption(ctx: PromptContext): JSONObject
}
