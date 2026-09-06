package de.spardirekt.ugcclean.gen

import com.google.common.truth.Truth.assertThat
import de.spardirekt.ugcclean.model.SpeechLanguage
import de.spardirekt.ugcclean.net.ChatClient
import de.spardirekt.ugcclean.net.DemoClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class PipelineTest {
    @Test
    fun demoPipeline_producesCanonicalReadyPackage() = runTest {
        val pipeline = Pipeline(liveClient = DemoClient(), demoClient = DemoClient())
        val images = List(3) { "data:image/jpeg;base64,abc$it" }
        val result = pipeline.run("sk-demo", images, SpeechLanguage.DE)
        assertThat(PromptComposer.isCanonical(result.veoPrompt)).isTrue()
        assertThat(result.copyPack.caption).contains("Werbung")
        assertThat(result.copyPack.hashtags).hasSize(5)
        assertThat(result.veoPrompt.lowercase()).doesNotContain("microwave")
        assertThat(result.plan.category).isEqualTo("unknown")
    }

    @Test
    fun parsePlan_readsVisibleFeatures() {
        val pipeline = Pipeline(liveClient = DemoClient())
        val plan = pipeline.parsePlan(
            """{"productName":"desk tray","category":"office","visibleFeatures":["white","slots"],"desire":"tidy desk","setting":"home office","action":"drop pens","spokenHookDe":"So.","spokenHookRu":"Так."}""",
        )
        assertThat(plan.productName).isEqualTo("desk tray")
        assertThat(plan.visibleFeatures).contains("slots")
    }

    @Test
    fun liveClientJson_isComposedLocallyNotCopied() = runTest {
        val fake = object : ChatClient {
            override suspend fun chat(
                apiKey: String,
                systemPrompt: String,
                userText: String,
                imageDataUrls: List<String>,
                jsonMode: Boolean,
            ): String {
                if (systemPrompt.contains("caption", true)) {
                    return """{"caption":"Schreibtisch klar.","hashtags":["#Büro"]}"""
                }
                return Json.encodeToString(Fixtures.organizer)
            }
        }
        val pipeline = Pipeline(liveClient = fake)
        val result = pipeline.run("sk-live", List(3) { "img$it" }, SpeechLanguage.DE)
        assertThat(result.veoPrompt).contains("PRODUCT IDENTITY LOCK:")
        assertThat(result.veoPrompt).contains("desk organizer")
        assertThat(result.veoPrompt.lowercase()).doesNotContain("microwave")
        assertThat(result.veoPrompt.lowercase()).doesNotContain("frying pan")
        assertThat(result.copyPack.details).contains("desk organizer")
    }
}
