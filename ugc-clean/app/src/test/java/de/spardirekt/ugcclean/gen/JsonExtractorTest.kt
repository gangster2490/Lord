package de.spardirekt.ugcclean.gen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JsonExtractorTest {
    @Test
    fun extractObject_fromFencedJson() {
        val raw = """
            here you go
            ```json
            {"productName":"pan","ok":true}
            ```
        """.trimIndent()
        val obj = JsonExtractor.extractObject(raw)
        assertThat(obj).contains("\"productName\":\"pan\"")
    }

    @Test
    fun repair_escapesNewlinesInsideStrings() {
        val broken = "{\"caption\": \"line1\nline2\"}"
        val repaired = JsonExtractor.repairLiteralControlsInStrings(broken)
        assertThat(repaired).contains("\\n")
    }
}
