package de.spardirekt.agents.pro.generation

import de.spardirekt.agents.pro.diagnostics.AppError
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ProjectImage
import de.spardirekt.agents.pro.model.VoiceLanguage
import de.spardirekt.agents.pro.network.ChatClient
import de.spardirekt.agents.pro.network.DemoChatClient
import de.spardirekt.agents.pro.ui.result.ResultComposition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationPipelineTest {

    private val fakeJpeg = listOf("data:image/jpeg;base64,ZmFrZQ==")

    private fun encoder(urls: List<String> = fakeJpeg) = ImageDataUrlEncoder { images ->
        if (images.isEmpty()) emptyList() else urls
    }

    private fun input(
        images: List<ProjectImage> = listOf(
            ProjectImage(id = "img_1", uri = "file:///tmp/demo.jpg", localPath = "/tmp/demo.jpg")
        ),
        voice: VoiceLanguage = VoiceLanguage.DE,
        wish: String = "luxury cream",
        apiKey: String = "sk-demo"
    ) = GenerationPipeline.PipelineInput(
        projectId = "pipeline-test",
        images = images,
        optionalWish = wish,
        voiceLanguage = voice,
        mode = AppMode.Simple,
        creativeMode = CreativeMode.Auto,
        tiktokShopMode = true,
        apiKey = apiKey,
        model = "gpt-5.6-sol"
    )

    @Test
    fun emptyImagesFailWithoutCallingTheModel() = runBlocking {
        val client = RecordingChatClient()
        val pipeline = GenerationPipeline(client, encoder())
        val result = pipeline.run(input(images = emptyList())) { }
        assertTrue(result.isFailure)
        val err = result.exceptionOrNull() as AppError
        assertTrue(err.detail.contains("изображений"))
        assertTrue(client.stages.isEmpty())
    }

    @Test
    fun encoderThatYieldsNothingFails() = runBlocking {
        val client = RecordingChatClient()
        val pipeline = GenerationPipeline(client, ImageDataUrlEncoder { emptyList() })
        val result = pipeline.run(input()) { }
        assertTrue(result.isFailure)
        assertTrue(client.stages.isEmpty())
    }

    @Test
    fun demoPipelineProducesCopyReadyTwelveSectionPrompt() = runBlocking {
        val client = RecordingChatClient()
        val pipeline = GenerationPipeline(client, encoder())
        val stagesSeen = mutableListOf<GenerationStage>()
        val result = pipeline.run(input()) { update -> stagesSeen += update.stage }
        assertTrue(result.isSuccess)
        val bundle = result.getOrThrow()

        assertTrue(bundle.veoPrompt.isNotBlank())
        assertTrue(bundle.analysisJson.contains("PRODUCT_PHOTO"))
        assertTrue(bundle.productModelJson.contains("Velvet Gold Night Cream"))
        assertTrue(bundle.creativePlanJson.contains("HighPerformingProductAd"))
        assertEquals(5, bundle.hashtags.size)
        assertTrue(bundle.hashtags.any { it.equals("#TikTokShop", ignoreCase = true) })
        assertTrue(bundle.voiceover.isNotBlank())
        assertFalse(bundle.voiceover.equals("OFF", ignoreCase = true))

        val completeness = PromptCleanup.validateCompleteness(bundle.veoPrompt, bundle.hashtags)
        assertTrue(
            "completeness issues: $completeness\n${bundle.veoPrompt}",
            completeness.none {
                it.startsWith("missing_") ||
                    it.startsWith("blank_") ||
                    it == "incomplete_timeline" ||
                    it == "section_order_wrong" ||
                    it.startsWith("hashtag_count")
            }
        )

        val entity = de.spardirekt.agents.pro.data.db.ProjectEntity(
            id = "pipeline-test",
            createdAt = 0L,
            updatedAt = 0L,
            veoPrompt = bundle.veoPrompt,
            voiceover = bundle.voiceover,
            title = bundle.title,
            tiktokShopMode = true,
            voiceLanguage = VoiceLanguage.DE.name,
            analysisResultJson = bundle.analysisJson,
            productModelJson = bundle.productModelJson
        )
        val composed = ResultComposition.veoPrompt(entity, bundle.hashtags)
        assertTrue(ResultComposition.hasRequiredSectionHeaders(composed))
        assertTrue(ResultComposition.nothingAfterHashtags(composed))
        assertEquals("Velvet Gold Night Cream", ResultComposition.title(entity))
        assertEquals(5, ResultComposition.hashtags(entity, bundle.hashtags).size)

        assertTrue(client.stages.contains(DemoChatClient.Stage.PHOTO_ANALYSIS))
        assertTrue(client.stages.contains(DemoChatClient.Stage.PRODUCT_MODEL))
        assertTrue(client.stages.contains(DemoChatClient.Stage.CREATIVE_DIRECTOR))
        assertTrue(client.stages.contains(DemoChatClient.Stage.FINAL_PROMPT))
        assertTrue(client.stages.contains(DemoChatClient.Stage.VOICEOVER))
        assertFalse(client.stages.contains(DemoChatClient.Stage.TARGETED_REPAIR))
        assertTrue(stagesSeen.contains(GenerationStage.DONE))
    }

    @Test
    fun panRuntimePathUsesFidelityFirstPromptAndPreservesCompleteOutput() = runBlocking {
        val client = RecordingChatClient()
        val pipeline = GenerationPipeline(client, encoder())
        val result = pipeline.run(
            input(
                wish = "Deep Black Pan with wooden crossbar lid",
                apiKey = "sk-demo-pan"
            )
        ) { }
        assertTrue(result.isSuccess)
        val bundle = result.getOrThrow()

        assertTrue(bundle.productModelJson.contains("wooden crossbar lid"))
        assertTrue("prompt must exceed the removed legacy budget", bundle.veoPrompt.length > 1200)
        listOf(
            "deep black bowl",
            "high curved sides",
            "long dark wooden handle",
            "hanging ring",
            "gold-tone ferrule",
            "riveted shank",
            "wooden crossbar lid",
            "no impossible hand anatomy"
        ).forEach { detail ->
            assertTrue("runtime prompt missing '$detail'", bundle.veoPrompt.contains(detail))
        }
        assertTrue(bundle.veoPrompt.contains("all unobstructed"))
        assertFalse(bundle.veoPrompt.contains("…"))

        val finalContract = client.finalPromptSystem.substringAfter("CURRENT STAGE: FINAL_PROMPT")
        assertFalse(finalContract.contains("under ~1200"))
        assertFalse(finalContract.contains("5–6 short bullets"))
        assertFalse(finalContract.contains("ONE line of 5–8"))
        assertTrue(finalContract.contains("There is no character target or prompt-length budget."))
        assertFalse(client.finalPromptUser.contains("Keep PRODUCT LOCK short"))
        assertTrue(client.finalPromptUser.contains("Do not compress or truncate product-specific content"))
    }

    @Test
    fun voiceOffSkipsSpokenLineAndWritesOff() = runBlocking {
        val client = RecordingChatClient()
        val pipeline = GenerationPipeline(client, encoder())
        val result = pipeline.run(input(voice = VoiceLanguage.OFF)) { }
        assertTrue(result.isSuccess)
        val bundle = result.getOrThrow()
        assertEquals("OFF", bundle.voiceover)
        assertFalse(client.stages.contains(DemoChatClient.Stage.VOICEOVER))
    }

    @Test
    fun liveClientFailureSurfacesAsPipelineFailure() = runBlocking {
        val pipeline = GenerationPipeline(FailingChatClient, encoder())
        val result = pipeline.run(input()) { }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Network)
    }

    @Test
    fun unusableFinalPromptIsAFailureNotAFakeReadyResult() = runBlocking {
        val client = RecordingChatClient(UnusableFinalPromptClient)
        val pipeline = GenerationPipeline(client, encoder())
        val stagesSeen = mutableListOf<GenerationStage>()
        val result = pipeline.run(input()) { update -> stagesSeen += update.stage }
        assertTrue(result.isFailure)
        val err = result.exceptionOrNull() as AppError
        assertTrue(err.detail.contains("VEO prompt") || err.userMessage.isNotBlank())
        assertFalse(stagesSeen.contains(GenerationStage.DONE))
    }

    private class RecordingChatClient(
        private val inner: ChatClient = DemoChatClient
    ) : ChatClient {
        val stages = mutableListOf<DemoChatClient.Stage>()
        var finalPromptSystem: String = ""
        var finalPromptUser: String = ""

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
            maxAttempts: Int,
            reasoningEffort: String?
        ): Result<String> {
            val stage = DemoChatClient.detectStage("$systemPrompt\n$userText")
            stages += stage
            if (stage == DemoChatClient.Stage.FINAL_PROMPT) {
                finalPromptSystem = systemPrompt
                finalPromptUser = userText
            }
            return inner.chat(
                apiKey, model, systemPrompt, userText, imageDataUrls,
                timeoutSeconds, jsonMode, temperature, maxTokens, maxAttempts, reasoningEffort
            )
        }
    }

    private object UnusableFinalPromptClient : ChatClient {
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
            maxAttempts: Int,
            reasoningEffort: String?
        ): Result<String> {
            val stage = DemoChatClient.detectStage("$systemPrompt\n$userText")
            if (stage == DemoChatClient.Stage.FINAL_PROMPT ||
                stage == DemoChatClient.Stage.TARGETED_REPAIR
            ) {
                return Result.success("""{"error":"invalid_api_key","message":"Incorrect API key provided"}""")
            }
            return DemoChatClient.chat(
                apiKey, model, systemPrompt, userText, imageDataUrls,
                timeoutSeconds, jsonMode, temperature, maxTokens, maxAttempts, reasoningEffort
            )
        }
    }

    private object FailingChatClient : ChatClient {
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
            maxAttempts: Int,
            reasoningEffort: String?
        ): Result<String> = Result.failure(AppError.Network("offline"))
    }
}
