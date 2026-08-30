package de.spardirekt.tiktokshop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.spardirekt.tiktokshop.data.ClaudeApiClient
import de.spardirekt.tiktokshop.data.EncodedImage
import de.spardirekt.tiktokshop.data.ImageKind
import de.spardirekt.tiktokshop.data.ImageSlot
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClaudeApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ClaudeApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ClaudeApiClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun generate_parsesClaudeProxyPayload() {
        val inner = """{"title":"TestTitle","hashtags":["#A"],"hooks":["H1"],"bannerText":["Z"],"veoPrompt":"8s","liveScript":"0:00 | Go","productFacts":{"name":"Koffer"}}"""
        val envelope = """{"content":[{"type":"text","text":${Json.encodeToString(inner)}}]}"""
        server.enqueue(MockResponse().setBody(envelope).setHeader("Content-Type", "application/json"))

        val slot = ImageSlot(
            index = 0,
            kind = ImageKind.PRODUCT,
            title = "Bild 1",
            subtitle = "",
            required = true,
            image = EncodedImage("AAAA", "image/jpeg", "p.jpg", "content://p"),
        )
        val result = client.generate(
            proxyUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "sk-ant-test",
            slots = listOf(slot),
            style = "Unboxing",
            tone = "Freundlich",
        )
        assertEquals("TestTitle", result.title)
        assertEquals("Koffer", result.productFacts.name)

        val recorded = server.takeRequest()
        assertEquals("/api/generate", recorded.path)
        assertEquals("sk-ant-test", recorded.getHeader("x-api-key-fwd"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"claude-opus-4-5\""))
        assertTrue(body.contains("Unboxing"))
        assertTrue(body.contains("image/jpeg"))
    }

    @Test(expected = IllegalStateException::class)
    fun generate_requiresProductImage() {
        client.generate(
            proxyUrl = server.url("/").toString(),
            apiKey = "sk-ant-test",
            slots = emptyList(),
            style = "Unboxing",
            tone = "Freundlich",
        )
    }
}
