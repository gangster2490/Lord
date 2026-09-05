package de.spardirekt.ugcagent.v3.prompt

import de.spardirekt.ugcagent.v3.pipeline.PipelineSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsBuilderTest {
    @Test
    fun germanSummaryOmitsInternalJson() {
        val session = sample()
        session.speechLanguage = "DEUTSCH"
        val text = DetailsBuilder.build(session)
        assertTrue(text.contains("Produktkategorie: kitchen"))
        assertTrue(text.contains("Hauptnutzung: cover food"))
        assertTrue(text.contains("Gewähltes First Frame: photo001"))
        assertTrue(text.contains("Sichere Aktion: grip the handle"))
        assertFalse(text.contains("uncertain_hidden"))
        assertFalse(text.contains("{"))
        assertFalse(text.contains("8% commission"))
    }

    @Test
    fun russianSummaryUsesSelectedLanguage() {
        val session = sample()
        session.speechLanguage = "РУССКИЙ"
        session.captionLanguage = "РУССКИЙ"
        val text = DetailsBuilder.build(session)
        assertTrue(text.contains("Категория товара"))
        assertTrue(text.contains("Безопасное действие"))
    }

    @Test
    fun videoPackageOrderIsDetailsPromptCaptionHashtags() {
        val pack = DetailsBuilder.videoPackage(
            "DETAILS LINE",
            "VIDEO PROMPT LINE",
            "CAPTION LINE",
            listOf("#one", "#two"),
        )
        assertEquals(
            "DETAILS LINE\n\nVIDEO PROMPT LINE\n\nCAPTION LINE\n\n#one #two",
            pack,
        )
    }

    private fun sample(): PipelineSession {
        val session = PipelineSession()
        session.firstFrameId = "photo001"
        session.analysis = JSONObject()
            .put("product_category", "kitchen")
            .put("observed_use_case", "cover food")
            .put("visual_features_relevant_to_use", JSONArray().put("green handle"))
            .put("text_claims", JSONArray().put("cover food").put("Earn €1.44 per sale"))
        session.identityFingerprint = JSONObject()
            .put("overall_geometry", "transparent dome with green ring")
            .put("uncertain_hidden_geometry", JSONArray().put("internal path"))
        session.scene = JSONObject().put("main_action", "grip the handle")
        session.warnings.add("Color/finish variants detected — selected First Frame wins.")
        return session
    }
}
