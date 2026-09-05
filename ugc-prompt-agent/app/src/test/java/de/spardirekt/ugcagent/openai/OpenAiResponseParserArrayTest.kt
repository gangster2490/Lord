package de.spardirekt.ugcagent.openai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenAiResponseParserArrayTest {

    @Test
    fun flattenArrayContentJoinsTextParts() {
        val raw = """
            {"choices":[{"message":{"content":[
              {"type":"text","text":"Handheld "},
              {"type":"text","text":"UGC"}
            ]}}]}
        """.trimIndent()
        assertThat(OpenAiResponseParser.messageText(raw)).isEqualTo("Handheld UGC")
    }

    @Test
    fun messageTextReadsGpt5ArrayContent() {
        val raw = """{"choices":[{"message":{"content":[{"type":"text","text":"9:16 Handheld"}]}}]}"""
        assertThat(OpenAiResponseParser.messageText(raw)).isEqualTo("9:16 Handheld")
    }
}
