package de.spardirekt.ugcagent.v3.ai

import org.json.JSONObject

interface AiProvider {
    val id: String

    fun testConnection(apiKey: String): JSONObject
    fun consistencyCheck(apiKey: String, images: List<ApiImage>): JSONObject
    fun analyseProduct(apiKey: String, images: List<ApiImage>): JSONObject
    fun productIdentityFingerprint(apiKey: String, images: List<ApiImage>): JSONObject
    fun actionIdentityRiskCheck(apiKey: String, fingerprint: JSONObject, scene: JSONObject, images: List<ApiImage>): JSONObject
    fun productIdentityReadiness(apiKey: String, fingerprint: JSONObject, images: List<ApiImage>): JSONObject
    fun recommendFirstFrame(apiKey: String, images: List<ApiImage>): JSONObject
    fun firstFrameQuality(apiKey: String, image: ApiImage): JSONObject
    fun generateScene(apiKey: String, analysis: JSONObject, images: List<ApiImage>, previous: JSONObject?, fingerprint: JSONObject?): JSONObject
    fun generateVideoPrompt(apiKey: String, images: List<ApiImage>, ctx: PromptContext): String
    fun improvePrompt(apiKey: String, ctx: PromptContext): String
    fun regenerateSpeech(apiKey: String, ctx: PromptContext): String
    fun generateCaption(apiKey: String, ctx: PromptContext): JSONObject
    fun checkCompliance(apiKey: String, ctx: PromptContext): JSONObject
}
