package de.spardirekt.ugcagent.openai

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class OpenAiResponseParserArrayTest {

    @Test
    fun flattenArrayContentJoinsTextParts() {
        val parts = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "Handheld "))
            .put("UGC")
        assertThat(OpenAiResponseParser.flattenContent(parts)).isEqualTo("Handheld UGC")
    }

    @Test
    fun messageTextReadsGpt5ArrayContent() {
        val raw = JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put(
                        "message",
                        JSONObject().put(
                            "content",
                            JSONArray().put(JSONObject().put("type", "text").put("text", "9:16 Handheld")),
                        ),
                    ),
                ),
            )
            .toString()
        assertThat(OpenAiResponseParser.messageText(raw)).isEqualTo("9:16 Handheld")
    }
}
