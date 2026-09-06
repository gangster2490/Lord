package de.spardirekt.ugcclean.net

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ProviderClientsTest {
    private val clients = ProviderClients()

    @Test
    fun demoKey_usesDemoClientForBothProviders() {
        assertThat(clients.forKey(AiProviderId.OPENAI, "sk-demo")).isInstanceOf(DemoClient::class.java)
        assertThat(clients.forKey(AiProviderId.GEMINI, "SK-DEMO")).isInstanceOf(DemoClient::class.java)
    }

    @Test
    fun liveKey_selectsNativeAdapter() {
        assertThat(clients.forKey(AiProviderId.OPENAI, "sk-live")).isInstanceOf(OpenAiClient::class.java)
        assertThat(clients.forKey(AiProviderId.GEMINI, "AIza-live")).isInstanceOf(GeminiClient::class.java)
    }

    @Test
    fun fromStored_defaultsToOpenAi() {
        assertThat(AiProviderId.fromStored(null)).isEqualTo(AiProviderId.OPENAI)
        assertThat(AiProviderId.fromStored("gemini")).isEqualTo(AiProviderId.GEMINI)
        assertThat(AiProviderId.fromStored("nope")).isEqualTo(AiProviderId.OPENAI)
    }
}

class AiModelConfigTest {
    @Test
    fun openaiFallbackOrder() {
        assertThat(AiModelConfig.openaiModels).containsExactly(
            "gpt-5.6-sol",
            "gpt-5.6-terra",
            "gpt-4o",
        ).inOrder()
    }

    @Test
    fun geminiFallbackOrder() {
        assertThat(AiModelConfig.geminiModels).containsExactly(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
        ).inOrder()
    }

    @Test
    fun geminiCandidates_keepKnownNamesFromListModelsPath() {
        val available = setOf("models/gemini-2.0-flash", "models/gemini-1.5-flash")
        assertThat(AiModelConfig.geminiCandidates(available)).containsExactly(
            "gemini-2.0-flash",
            "gemini-1.5-flash",
        ).inOrder()
    }

    @Test
    fun emptyAvailable_returnsFullChain() {
        assertThat(AiModelConfig.openaiCandidates(emptySet())).isEqualTo(AiModelConfig.openaiModels)
        assertThat(AiModelConfig.geminiCandidates(null)).isEqualTo(AiModelConfig.geminiModels)
    }
}

class GeminiPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun build_usesInlineDataNotOpenAiImageUrl() {
        val body = GeminiPayload.build(
            systemPrompt = "sys",
            userText = "user",
            imageDataUrls = listOf("data:image/jpeg;base64,abc123"),
            jsonMode = true,
        )
        val text = body.toString()
        assertThat(text).contains("inline_data")
        assertThat(text).contains("mime_type")
        assertThat(text).contains("application/json")
        assertThat(text).doesNotContain("image_url")
        assertThat(text).doesNotContain("api.openai.com")
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        val inline = parts[1].jsonObject["inline_data"]!!.jsonObject
        assertThat(inline["data"]!!.jsonPrimitive.content).isEqualTo("abc123")
        assertThat(inline["mime_type"]!!.jsonPrimitive.content).isEqualTo("image/jpeg")
    }

    @Test
    fun parseText_readsCandidateParts() {
        val raw = """
            {"candidates":[{"content":{"parts":[{"text":"{\"productName\":\"lamp\"}"}]}}]}
        """.trimIndent()
        assertThat(GeminiPayload.parseText(json, raw)).contains("lamp")
    }

    @Test
    fun parseDataUrl_splitsMimeAndPayload() {
        assertThat(GeminiPayload.parseDataUrl("data:image/png;base64,QQ=="))
            .isEqualTo("image/png" to "QQ==")
    }
}
