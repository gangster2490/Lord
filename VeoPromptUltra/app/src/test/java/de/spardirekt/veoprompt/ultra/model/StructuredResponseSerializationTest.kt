package de.spardirekt.veoprompt.ultra.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredResponseSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun fieldIsVeoPromptNotMainPrompt() {
        val encoded = json.encodeToString(
            StructuredResponse.serializer(),
            StructuredResponse(veoPrompt = "FORMAT\n", hashtags = listOf("#a", "#b", "#c", "#d", "#e"))
        )
        assertTrue(encoded.contains("\"veoPrompt\""))
        assertTrue(!encoded.contains("\"mainPrompt\""))
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertTrue(obj["hashtags"] is JsonArray)
        assertEquals(5, (obj["hashtags"] as JsonArray).size)
    }

    @Test
    fun imageTypesIncludeLifestyle() {
        assertEquals(ImageType.LIFESTYLE_REFERENCE, ImageType.fromRaw("LIFESTYLE_REFERENCE"))
        assertEquals(ImageType.PRODUCT_DETAIL, ImageType.fromRaw("PRODUCT_DETAIL_PHOTO"))
        assertEquals("Detail", ImageType.PRODUCT_DETAIL.badgeLabel())
        assertEquals("Listing", ImageType.MARKETPLACE_LISTING.badgeLabel())
    }
}
