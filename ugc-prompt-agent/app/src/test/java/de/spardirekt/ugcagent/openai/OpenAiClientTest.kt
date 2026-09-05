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
}
