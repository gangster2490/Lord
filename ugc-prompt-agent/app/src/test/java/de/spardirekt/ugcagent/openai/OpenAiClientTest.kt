package de.spardirekt.ugcagent.openai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenAiClientTest {

    @Test
    fun defaultModelIsGpt56Sol() {
        assertThat(OpenAiClient.DEFAULT_MODEL).isEqualTo("gpt-5.6-sol")
        assertThat(OpenAiClient.MODELS.first()).isEqualTo("gpt-5.6-sol")
        assertThat(OpenAiClient.MODELS).contains("gpt-4o")
    }

    @Test
    fun visionDetailIsPublicApiHighNotOriginal() {
        assertThat(OpenAiClient.IMAGE_DETAIL).isEqualTo("high")
    }
}

class OpenAiErrorRulesTest {

    @Test
    fun detectsUnsupportedParameterAndRelatedFields() {
        assertThat(OpenAiErrorRules.isUnsupported("""{"error":{"code":"unsupported_parameter"}}""")).isTrue()
        assertThat(OpenAiErrorRules.isUnsupported("Unknown parameter: reasoning_effort")).isTrue()
        assertThat(OpenAiErrorRules.isUnsupported("Invalid response_format for this model")).isTrue()
        assertThat(OpenAiErrorRules.isUnsupported("Invalid value for \"detail\"")).isTrue()
        assertThat(OpenAiErrorRules.isUnsupported("image too large for the request")).isFalse()
    }

    @Test
    fun detectsImageTooLarge() {
        assertThat(OpenAiErrorRules.isImageTooLarge("Request too large")).isTrue()
        assertThat(OpenAiErrorRules.isImageTooLarge("context_length_exceeded")).isTrue()
        assertThat(OpenAiErrorRules.isImageTooLarge("image exceeds limit")).isTrue()
        assertThat(OpenAiErrorRules.isImageTooLarge("unsupported_parameter")).isFalse()
    }

    @Test
    fun detectsRateLimitFromCodeOrBody() {
        assertThat(OpenAiErrorRules.isRateLimited("RATE_LIMIT")).isTrue()
        assertThat(OpenAiErrorRules.isRateLimited("429")).isTrue()
        assertThat(OpenAiErrorRules.isRateLimited("GENERIC", "Rate limit exceeded")).isTrue()
        assertThat(OpenAiErrorRules.isRateLimited("GENERIC", "too many requests")).isTrue()
        assertThat(OpenAiErrorRules.isRateLimited("GENERIC", "ok")).isFalse()
    }

    @Test
    fun detectsMissingModel() {
        assertThat(OpenAiErrorRules.isMissingModel("The model `gpt-5.6-sol` does not exist")).isTrue()
        assertThat(OpenAiErrorRules.isMissingModel("model not found")).isTrue()
        assertThat(OpenAiErrorRules.isMissingModel("unsupported_parameter")).isFalse()
    }
}

class OpenAiResponseParserTest {

    @Test
    fun flattenStringContentTrimsAndDropsNullToken() {
        assertThat(OpenAiResponseParser.flattenContent(null)).isEmpty()
        assertThat(OpenAiResponseParser.flattenContent("null")).isEmpty()
        assertThat(OpenAiResponseParser.flattenContent("  handheld ugc  ")).isEqualTo("handheld ugc")
    }
}
