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
    fun demoKey_usesDemoClientForAllProviders() {
        assertThat(clients.forKey(AiProviderId.OPENAI, "sk-demo")).isInstanceOf(DemoClient::class.java)
        assertThat(clients.forKey(AiProviderId.GEMINI, "SK-DEMO")).isInstanceOf(DemoClient::class.java)
        assertThat(clients.forKey(AiProviderId.CLAUDE, "sk-demo")).isInstanceOf(DemoClient::class.java)
    }

    @Test
    fun liveKey_selectsNativeAdapter() {
        assertThat(clients.forKey(AiProviderId.OPENAI, "sk-live")).isInstanceOf(OpenAiClient::class.java)
        assertThat(clients.forKey(AiProviderId.GEMINI, "AIza-live")).isInstanceOf(GeminiClient::class.java)
        assertThat(clients.forKey(AiProviderId.CLAUDE, "sk-ant-live")).isInstanceOf(ClaudeClient::class.java)
    }

    @Test
    fun fromStored_defaultsToOpenAi() {
        assertThat(AiProviderId.fromStored(null)).isEqualTo(AiProviderId.OPENAI)
        assertThat(AiProviderId.fromStored("gemini")).isEqualTo(AiProviderId.GEMINI)
        assertThat(AiProviderId.fromStored("claude")).isEqualTo(AiProviderId.CLAUDE)
        assertThat(AiProviderId.fromStored("anthropic")).isEqualTo(AiProviderId.CLAUDE)
        assertThat(AiProviderId.fromStored("nope")).isEqualTo(AiProviderId.OPENAI)
    }

    @Test
    fun attemptOrder_addsRemainingProvidersWhenTheirKeysExist() {
        val keys = mapOf(
            AiProviderId.OPENAI to "sk-live",
            AiProviderId.GEMINI to "AIza-live",
            AiProviderId.CLAUDE to "sk-ant-live",
        )
        val order = ProviderClients.attemptOrder(AiProviderId.OPENAI) { keys.getValue(it) }
        assertThat(order.map { it.provider })
            .containsExactly(AiProviderId.OPENAI, AiProviderId.GEMINI, AiProviderId.CLAUDE)
            .inOrder()
        assertThat(order[1].apiKey).isEqualTo("AIza-live")
        assertThat(order[2].apiKey).isEqualTo("sk-ant-live")
        val claudeFirst = ProviderClients.attemptOrder(AiProviderId.CLAUDE) { keys.getValue(it) }
        assertThat(claudeFirst.map { it.provider })
            .containsExactly(AiProviderId.CLAUDE, AiProviderId.OPENAI, AiProviderId.GEMINI)
            .inOrder()
    }

    @Test
    fun attemptOrder_skipsFallbackForDemoAndMissingAltKey() {
        val demo = ProviderClients.attemptOrder(AiProviderId.GEMINI) {
            if (it == AiProviderId.GEMINI) "sk-demo" else "AIza-live"
        }
        assertThat(demo).hasSize(1)
        assertThat(demo[0].provider).isEqualTo(AiProviderId.GEMINI)
        val noAlt = ProviderClients.attemptOrder(AiProviderId.OPENAI) {
            if (it == AiProviderId.OPENAI) "sk-live" else ""
        }
        assertThat(noAlt).hasSize(1)
        assertThat(noAlt[0].provider).isEqualTo(AiProviderId.OPENAI)
        val twoKeys = mapOf(
            AiProviderId.OPENAI to "sk-live",
            AiProviderId.GEMINI to "AIza-live",
            AiProviderId.CLAUDE to "",
        )
        val skippedClaude = ProviderClients.attemptOrder(AiProviderId.OPENAI) { twoKeys.getValue(it) }
        assertThat(skippedClaude.map { it.provider })
            .containsExactly(AiProviderId.OPENAI, AiProviderId.GEMINI)
            .inOrder()
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
    fun claudeFallbackOrder() {
        assertThat(AiModelConfig.claudeModels).containsExactly(
            "claude-sonnet-4-5",
            "claude-opus-4-5",
            "claude-3-5-sonnet-latest",
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
        assertThat(AiModelConfig.claudeCandidates(null)).isEqualTo(AiModelConfig.claudeModels)
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

class ClaudePayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun build_usesAnthropicImageSourceNotOpenAiOrGeminiShapes() {
        val body = ClaudePayload.build(
            model = "claude-sonnet-4-5",
            systemPrompt = "sys",
            userText = "user",
            imageDataUrls = listOf("data:image/jpeg;base64,abc123"),
            jsonMode = true,
        )
        val text = body.toString()
        assertThat(text).contains("base64")
        assertThat(text).contains("media_type")
        assertThat(text).contains("Return a single JSON object only")
        assertThat(text).doesNotContain("image_url")
        assertThat(text).doesNotContain("inline_data")
        assertThat(text).doesNotContain("api.openai.com")
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        val source = content[1].jsonObject["source"]!!.jsonObject
        assertThat(content[1].jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("image")
        assertThat(source["type"]!!.jsonPrimitive.content).isEqualTo("base64")
        assertThat(source["data"]!!.jsonPrimitive.content).isEqualTo("abc123")
        assertThat(source["media_type"]!!.jsonPrimitive.content).isEqualTo("image/jpeg")
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("claude-sonnet-4-5")
    }

    @Test
    fun parseText_readsContentBlocks() {
        val raw = """
            {"content":[{"type":"text","text":"{\"productName\":\"lamp\"}"}]}
        """.trimIndent()
        assertThat(ClaudePayload.parseText(json, raw)).contains("lamp")
    }

    @Test
    fun parseDataUrl_splitsMimeAndPayload() {
        assertThat(ClaudePayload.parseDataUrl("data:image/png;base64,QQ=="))
            .isEqualTo("image/png" to "QQ==")
    }
}
