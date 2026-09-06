package de.spardirekt.ugcclean.gen

import com.google.common.truth.Truth.assertThat
import de.spardirekt.ugcclean.model.CopyPack
import de.spardirekt.ugcclean.model.PipelineStage
import de.spardirekt.ugcclean.model.ProductPlan
import de.spardirekt.ugcclean.model.ProjectRecord
import de.spardirekt.ugcclean.model.ProjectStatus
import de.spardirekt.ugcclean.model.SpeechLanguage
import de.spardirekt.ugcclean.net.ChatClient
import de.spardirekt.ugcclean.net.DemoClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * V4 acceptance: lock current UGC Clean behavior. No new product features.
 * Maps the V3 A–M checklist onto the clean composer/pipeline that already ships.
 */
class V4AcceptanceTest {

    private val cover = ProductPlan(
        productName = "microwave splatter cover",
        category = "kitchen",
        visibleFeatures = listOf(
            "transparent dome",
            "green circular base ring",
            "curved green handle",
            "two rectangular transparent upper modules",
            "green caps",
        ),
        movingParts = listOf("circular upper vent", "handle"),
        useCase = "cover food in a microwave",
        desire = "keep the microwave clean while heating",
        setting = "an ordinary home kitchen next to a microwave",
        camera = "slight handheld over the counter",
        action = "one hand grips the green handle",
        humanBehaviour = "casual kitchen glance",
        lighting = "kitchen ceiling light",
        spokenHookDe = "Schau mal, einfach drüber und fertig.",
        spokenHookRu = "Смотри, на тарелку — и всё.",
    )

    @Test
    fun testA_identityLockForbidsAddRemoveRestyleAndMerge() {
        val prompt = PromptComposer.compose(cover, SpeechLanguage.DE)
        assertThat(prompt).contains("Do not add, remove, or restyle parts")
        assertThat(prompt).contains("Do not morph, merge, or replace this product")
        assertThat(prompt).doesNotContain("You may merge")
        assertThat(prompt).doesNotContain("similar product from the same category is acceptable")
    }

    @Test
    fun testB_analyzePromptForbidsHighRiskActions() {
        assertThat(SystemPrompts.ANALYZE).contains("LOW RISK")
        assertThat(SystemPrompts.ANALYZE).contains("do not open, unfold, ignite, pour")
        val prompt = PromptComposer.compose(cover.copy(action = "one hand grips the green handle"), SpeechLanguage.DE)
        assertThat(prompt.lowercase()).doesNotContain("pour water")
        assertThat(prompt).contains("one hand grips the green handle")
    }

    @Test
    fun testC_coverPlanKeepsVisiblePartsAndDoesNotInventReservoir() {
        val prompt = PromptComposer.compose(cover, SpeechLanguage.DE)
        assertThat(prompt).contains("transparent dome")
        assertThat(prompt).contains("green circular base ring")
        assertThat(prompt).contains("two rectangular transparent upper modules")
        assertThat(prompt.lowercase()).doesNotContain("cylindrical reservoir")
    }

    @Test
    fun testD_genericCategoryReplacementIsForbidden() {
        val prompt = PromptComposer.compose(cover, SpeechLanguage.DE)
        assertThat(prompt).contains("Do not replace it with a generic category product")
        assertThat(prompt).contains("Do not invent extra components from other product categories")
    }

    @Test
    fun testE_movingPartsStayStillWhenUncertain() {
        val prompt = PromptComposer.compose(cover, SpeechLanguage.DE)
        assertThat(prompt).contains("MOVING COMPONENT LOCK:")
        assertThat(prompt).contains("circular upper vent")
        assertThat(prompt).contains("keep the product still")
        assertThat(PromptComposer.headingCounts(prompt)["MOVING COMPONENT LOCK"]).isEqualTo(1)
    }

    @Test
    fun testF_veoDurationIsExactEightSecondsNotMaximum() {
        val prompt = PromptComposer.compose(cover, SpeechLanguage.DE)
        assertThat(prompt).contains("Exactly 8.0 seconds")
        assertThat(prompt).contains("End exactly at 8.0 seconds")
        assertThat(prompt.lowercase()).doesNotContain("maximum 8")
        assertThat(prompt.lowercase()).doesNotContain("max 8")
    }

    @Test
    fun testG_spokenLineFinishesBeforeEndpoint() {
        val de = PromptComposer.compose(cover, SpeechLanguage.DE)
        val ru = PromptComposer.compose(cover, SpeechLanguage.RU)
        assertThat(de).contains("finished before 8.0s")
        assertThat(de).contains("Schau mal, einfach drüber und fertig.")
        assertThat(ru).contains("Смотри, на тарелку — и всё.")
        assertThat(PromptComposer.headingCounts(de)["SPEECH"]).isEqualTo(1)
        assertThat(PromptComposer.headingCounts(ru)["SPEECH"]).isEqualTo(1)
    }

    @Test
    fun testH_noIntroOutroOrTail() {
        val prompt = PromptComposer.compose(cover, SpeechLanguage.DE)
        assertThat(prompt).contains("No intro, outro, CTA, additional scene, freeze-frame or transition tail")
        assertThat(PromptComposer.isCanonical(prompt)).isTrue()
    }

    @Test
    fun testI_utf8CyrillicIsPreserved() {
        val hook = "Смотри, на тарелку — и всё."
        val prompt = PromptComposer.compose(cover.copy(spokenHookRu = hook), SpeechLanguage.RU)
        assertThat(prompt).contains(hook)
        assertThat(prompt).contains("тарелку")
        assertThat(prompt).doesNotContain("Ñ")
        assertThat(Compliance.sanitizeCaption("Обычный день с крышкой", SpeechLanguage.RU, cover)).contains("Реклама")
    }

    @Test
    fun testJ_canonicalTwelveHeadingsOnceAndNoLeakedAnalysis() {
        val prompt = PromptComposer.compose(cover, SpeechLanguage.DE)
        PromptComposer.HEADINGS.forEach { heading ->
            assertThat(PromptComposer.headingCounts(prompt)[heading]).isEqualTo(1)
        }
        listOf(
            "FINAL IDENTITY LOCK",
            "STRUCTURAL IDENTITY LOCK",
            "DURATION:",
            "uncertain_hidden_geometry",
            "ambiguity_warning",
        ).forEach { banned ->
            assertThat(prompt).doesNotContain(banned)
        }
    }

    @Test
    fun testK_firstFrameWinsInReference() {
        val prompt = PromptComposer.compose(cover, SpeechLanguage.DE)
        assertThat(prompt).contains("First uploaded photo is the First Frame")
        assertThat(prompt).contains("Recreate that exact physical product")
        assertThat(SystemPrompts.ANALYZE).contains("first photo is the First Frame")
    }

    @Test
    fun testL_threeImagesAndDemoKeyReachReadyCopyPackage() = runTest {
        val stages = mutableListOf<PipelineStage>()
        val percents = mutableListOf<Int>()
        val pipeline = Pipeline(liveClient = DemoClient(), demoClient = DemoClient())
        val result = pipeline.run("sk-demo", List(3) { "data:image/jpeg;base64,v4$it" }, SpeechLanguage.DE) { stage, percent ->
            stages += stage
            percents += percent
        }
        assertThat(PromptComposer.isCanonical(result.veoPrompt)).isTrue()
        assertThat(result.copyPack.caption).isNotEmpty()
        assertThat(result.copyPack.hashtags).hasSize(5)
        assertThat(result.copyPack.details).isNotEmpty()
        assertThat(stages).contains(PipelineStage.DONE)
        assertThat(percents.last()).isEqualTo(100)
        val record = readyRecord(result.veoPrompt, result.copyPack)
        val pack = record.videoPackage()
        assertThat(pack).contains(result.veoPrompt.trim())
        assertThat(pack).contains(result.copyPack.caption)
        assertThat(pack).contains(result.copyPack.hashtags.first())
        assertThat(pack).doesNotContain("Produkt:")
    }

    @Test
    fun testM_secondStartBlockedWhileRunningAndRetryIsAFreshRun() = runTest {
        assertThat(StartGate.canStart(3, true, running = true)).isFalse()
        assertThat(StartGate.blockReason(3, true, true, SpeechLanguage.DE)).contains("läuft bereits")
        val pipeline = Pipeline(liveClient = DemoClient(), demoClient = DemoClient())
        val first = pipeline.run("sk-demo", List(3) { "img$it" }, SpeechLanguage.DE)
        val second = pipeline.run("sk-demo", List(3) { "img$it" }, SpeechLanguage.RU)
        assertThat(PromptComposer.isCanonical(first.veoPrompt)).isTrue()
        assertThat(PromptComposer.isCanonical(second.veoPrompt)).isTrue()
        assertThat(second.copyPack.caption).contains("Реклама")
    }

    @Test
    fun testChairDoesNotInheritCoverOrPanParts() {
        val prompt = PromptComposer.compose(Fixtures.chair, SpeechLanguage.DE).lowercase()
        listOf("microwave", "splatter", "transparent dome", "frying pan", "reservoir").forEach { leak ->
            assertThat(prompt).doesNotContain(leak)
        }
        assertThat(prompt).contains("armchair")
    }

    @Test
    fun testTooFewPhotosFailClosed() = runTest {
        val pipeline = Pipeline(liveClient = DemoClient())
        val error = runCatching { pipeline.run("sk-demo", listOf("a", "b"), SpeechLanguage.DE) }.exceptionOrNull()
        assertThat(error).isInstanceOf(PipelineException::class.java)
        assertThat(error!!.message).contains("3")
    }

    @Test
    fun testLiveAnalyzeJsonIsComposedNotEchoed() = runTest {
        val fake = object : ChatClient {
            override suspend fun chat(
                apiKey: String,
                systemPrompt: String,
                userText: String,
                imageDataUrls: List<String>,
                jsonMode: Boolean,
            ): String {
                if (systemPrompt.contains("caption", true)) {
                    return """{"caption":"Alltag mit der Pfanne.","hashtags":["#Pfanne"]}"""
                }
                return Json.encodeToString(Fixtures.pan)
            }
        }
        val result = Pipeline(liveClient = fake).run("sk-live", List(4) { "p$it" }, SpeechLanguage.DE)
        assertThat(result.veoPrompt).contains("deep black frying pan")
        assertThat(result.veoPrompt).contains("PRODUCT IDENTITY LOCK:")
        assertThat(result.copyPack.caption).contains("Werbung")
        assertThat(result.veoPrompt.lowercase()).doesNotContain("armchair")
        assertThat(result.veoPrompt.lowercase()).doesNotContain("microwave")
    }

    private fun readyRecord(prompt: String, pack: CopyPack) = ProjectRecord(
        id = "v4",
        createdAt = 1,
        updatedAt = 1,
        status = ProjectStatus.READY,
        language = SpeechLanguage.DE,
        photoUris = listOf("a", "b", "c"),
        veoPrompt = prompt,
        copyPack = pack,
    )
}
