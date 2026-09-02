package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.AppMode
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.GenerationStage
import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.ProjectImage
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import de.spardirekt.veoprompt.ultra.network.ChatClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationPipelineTest {

    private val fakeJpeg = listOf("data:image/jpeg;base64,ZmFrZQ==")
    private val encoder = ImageDataUrlEncoder { images -> if (images.isEmpty()) emptyList() else fakeJpeg }

    private fun input() = GenerationPipeline.PipelineInput(
        projectId = "p1",
        images = listOf(ProjectImage(id = "img_1", uri = "file:///tmp/a.jpg")),
        optionalWish = "",
        voiceLanguage = VoiceLanguage.DE,
        mode = AppMode.Simple,
        creativeMode = CreativeMode.AUTO,
        tiktokShopMode = true,
        apiKey = "sk-test",
        model = "gpt-4o"
    )

    @Test
    fun emptyImagesFailWithoutModel() = runBlocking {
        val client = ScriptedChatClient()
        val pipeline = GenerationPipeline(client, encoder)
        val result = pipeline.run(input().copy(images = emptyList())) {}
        assertTrue(result.isFailure)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun successfulPathStoresFullUnmodifiedPrompt() = runBlocking {
        val client = ScriptedChatClient()
        val pipeline = GenerationPipeline(client, encoder)
        val stages = mutableListOf<GenerationStage>()
        val result = pipeline.run(input()) { stages += it.stage }
        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
        val bundle = result.getOrThrow()
        assertTrue(bundle.veoPrompt.contains("PRODUCT LOCK"))
        assertFalse(bundle.veoPrompt.contains("\nTITLE\n"))
        assertEquals(5, bundle.hashtags.size)
        assertTrue(stages.contains(GenerationStage.PHOTO_ANALYSIS))
        assertTrue(stages.contains(GenerationStage.VISUAL_LOCK))
        assertTrue(stages.contains(GenerationStage.FINAL_VALIDATION))
        assertTrue(stages.contains(GenerationStage.DONE))
        assertFalse(bundle.veoPrompt.endsWith("..."))
        assertTrue(bundle.veoPrompt.contains("deep rounded bowl"))
        assertFalse(bundle.veoPrompt.contains("SAFETY AUDIT"))
        assertTrue(bundle.safetyAudit.policyVersion.isNotBlank())
    }

    @Test
    fun apiFailureIsNotSuccess() = runBlocking {
        val client = ScriptedChatClient(failAt = "PHOTO_ANALYSIS")
        val pipeline = GenerationPipeline(client, encoder)
        val result = pipeline.run(input()) {}
        assertTrue(result.isFailure)
    }

    @Test
    fun arraysStayArraysInStructuredJson() {
        val json = buildJsonObject {
            put("hashtags", buildJsonArray {
                add(JsonPrimitive("#a"))
                add(JsonPrimitive("#b"))
                add(JsonPrimitive("#c"))
                add(JsonPrimitive("#d"))
                add(JsonPrimitive("#TikTokShop"))
            })
            put("veoPrompt", JsonPrimitive(Fixtures.validVeoPrompt()))
            put("voiceover", JsonPrimitive("Kurz und klar über den Deckel."))
            put("title", JsonPrimitive("Pfanne"))
        }
        val parsed = StructuredResponseParser.fromObject(json)
        assertEquals(5, parsed.hashtags.size)
        assertTrue(json["hashtags"] is JsonArray)
    }
}

class ScriptedChatClient(
    private val failAt: String? = null
) : ChatClient {
    val calls = mutableListOf<String>()

    override suspend fun chat(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        timeoutSeconds: Long,
        jsonMode: Boolean,
        temperature: Double,
        maxTokens: Int,
        maxAttempts: Int
    ): Result<String> {
        val stage = when {
            systemPrompt.contains("PHOTO_ANALYSIS") -> "PHOTO_ANALYSIS"
            systemPrompt.contains("PRODUCT_MODEL") -> "PRODUCT_MODEL"
            systemPrompt.contains("CREATIVE_DIRECTOR") -> "CREATIVE_DIRECTOR"
            systemPrompt.contains("TARGETED_REPAIR") -> "TARGETED_REPAIR"
            else -> "FINAL_PROMPT"
        }
        calls += stage
        if (stage == failAt) {
            return Result.failure(de.spardirekt.veoprompt.ultra.diagnostics.AppError.RateLimited("429"))
        }
        return Result.success(
            when (stage) {
                "PHOTO_ANALYSIS" -> analysisJson()
                "PRODUCT_MODEL" -> productJson()
                "CREATIVE_DIRECTOR" -> creativeJson()
                else -> finalJson()
            }
        )
    }

    private fun analysisJson() = """
{"productCategory":"cookware","productIdentity":"Deep black pan with wooden lid","visualSignature":["deep rounded bowl","high sides","wooden handle","ferrule","rivets","hanging ring","wooden lid"],"verifiedFeatures":["wooden lid"],"uncertainFacts":[],"imageTypes":[{"imageId":"img_1","category":"PRODUCT_PHOTO","notes":""}],"marketplaceDetected":false,"summary":"pan"}
""".trim()

    private fun productJson() = """
{"productCategory":"cookware","productIdentity":"Deep black pan with wooden lid","visualSignature":["deep rounded bowl","high sides","wooden handle","ferrule","rivets","hanging ring","wooden lid"],"confirmedParts":["bowl","handle","lid"],"confirmedColors":["black","wood"],"confirmedMaterials":[],"confirmedStates":["lid on"],"confirmedFunctions":["cooking"],"confirmedAccessories":["lid"],"confirmedMarkings":[],"descriptionEvidence":[],"uncertainFacts":[],"unsafeAssumptions":[],"highRiskHallucinations":[],"possibleUseCases":["stovetop cooking"],"imageClassifications":[{"imageId":"img_1","category":"PRODUCT_PHOTO"}],"hasMarketplaceScreenshots":false}
""".trim()

    private fun creativeJson() = """
{"selectedMode":"SHOWCASE","heroFeature":"wooden lid fit","reason":"closed product, keep simple","setting":"kitchen","hookIdea":"lid detail","useHands":false,"usePeople":false}
""".trim()

    private fun finalJson(): String {
        val prompt = Fixtures.validVeoPrompt(
            ProductModel(
                visualSignature = listOf(
                    "deep rounded bowl", "high sides", "wooden handle", "ferrule", "rivets", "hanging ring", "wooden lid"
                )
            )
        ).replace("\n", "\\n")
        return """
{"imageAnalysis":{"productCategory":"cookware","productIdentity":"Deep black pan with wooden lid","visualSignature":["deep rounded bowl"],"verifiedFeatures":["lid"],"uncertainFacts":[],"imageTypes":[]},"creativeDirection":{"selectedMode":"SHOWCASE","heroFeature":"lid","reason":"simple"},"veoPrompt":"$prompt","voiceover":"Tiefer Topf, fester Holzdeckel, einfach kochen.","title":"Tiefe Pfanne mit Holzdeckel","hashtags":["#Pfanne","#Kochen","#Holzdeckel","#Kitchen","#TikTokShop"],"safetyAudit":{"riskLevel":"LOW","items":[]}}
""".trim()
    }
}
