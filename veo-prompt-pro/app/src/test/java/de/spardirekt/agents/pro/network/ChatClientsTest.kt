package de.spardirekt.agents.pro.network

import de.spardirekt.agents.pro.generation.PromptTemplates
import de.spardirekt.agents.pro.generation.VoiceoverSystem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatClientsTest {

    @Test
    fun demoPrefixSelectsOfflineClient() {
        assertTrue(ChatClients.isDemoKey("sk-demo"))
        assertTrue(ChatClients.isDemoKey("sk-demo-local"))
        assertTrue(ChatClients.isDemoKey("SK-DEMO"))
        assertFalse(ChatClients.isDemoKey("sk-live-key"))
        assertFalse(ChatClients.isDemoKey(""))
        assertSame(DemoChatClient, ChatClients.fromApiKey("sk-demo"))
        val live = OpenAiClient()
        assertSame(live, ChatClients.fromApiKey("sk-abc", live = live))
    }

    @Test
    fun demoDetectsEachPipelineStage() {
        assertEquals(
            DemoChatClient.Stage.PHOTO_ANALYSIS,
            DemoChatClient.detectStage(PromptTemplates.photoAnalysisSystem())
        )
        assertEquals(
            DemoChatClient.Stage.PRODUCT_MODEL,
            DemoChatClient.detectStage(PromptTemplates.productModelSystem())
        )
        assertEquals(
            DemoChatClient.Stage.CREATIVE_DIRECTOR,
            DemoChatClient.detectStage(PromptTemplates.creativeDirectorSystem())
        )
        assertEquals(
            DemoChatClient.Stage.FINAL_PROMPT,
            DemoChatClient.detectStage(PromptTemplates.finalPromptSystem("DE", true))
        )
        assertEquals(
            DemoChatClient.Stage.TARGETED_REPAIR,
            DemoChatClient.detectStage(PromptTemplates.targetedRepairSystem(listOf("VOICEOVER")))
        )
        assertEquals(
            DemoChatClient.Stage.VOICEOVER,
            DemoChatClient.detectStage(VoiceoverSystem.systemPrompt("DE", true))
        )
    }

    @Test
    fun demoVoiceoverHonorsLanguage() = runBlocking {
        val client: ChatClient = DemoChatClient
        val de = client.chat(
            apiKey = "sk-demo",
            model = "gpt-5.6-sol",
            systemPrompt = VoiceoverSystem.systemPrompt("DE", true),
            userText = "Write the spoken voiceover from this evidence.",
            timeoutSeconds = 10
        ).getOrThrow()
        assertTrue(de.contains("Schau ihn dir im TikTok Shop an"))

        val ru = client.chat(
            apiKey = "sk-demo",
            model = "gpt-5.6-sol",
            systemPrompt = VoiceoverSystem.systemPrompt("RU", true),
            userText = "Write the spoken voiceover from this evidence.",
            timeoutSeconds = 10
        ).getOrThrow()
        assertTrue(ru.contains("Загляни скорее в TikTok Shop"))

        val off = client.chat(
            apiKey = "sk-demo",
            model = "gpt-5.6-sol",
            systemPrompt = VoiceoverSystem.systemPrompt("OFF", true),
            userText = "Write the spoken voiceover from this evidence.",
            timeoutSeconds = 10
        ).getOrThrow()
        assertTrue(off.contains("\"voiceover\":\"OFF\"") || off.contains("\"voiceover\": \"OFF\""))
    }

    @Test
    fun demoConnectionCheckDoesNotHitTheNetwork() {
        val result = OpenAiClient().testConnection("sk-demo")
        assertTrue(result.isSuccess)
        assertEquals("Соединение успешно", result.getOrThrow())
    }
}
