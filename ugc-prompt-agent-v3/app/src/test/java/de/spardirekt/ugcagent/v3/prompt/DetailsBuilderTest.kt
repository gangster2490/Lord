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
    fun videoPackageOrderIsPromptCaptionHashtags() {
        val pack = DetailsBuilder.videoPackage(
            "VIDEO PROMPT LINE",
            "CAPTION LINE",
            listOf("#one", "#two"),
        )
        assertEquals(
            "VIDEO PROMPT LINE\n\nCAPTION LINE\n\n#one #two",
            pack,
        )
        val all = DetailsBuilder.copyAll("DETAILS LINE", "VIDEO PROMPT LINE", "CAPTION LINE", listOf("#one", "#two"))
        assertEquals(
            "DETAILS LINE\n\nVIDEO PROMPT LINE\n\nCAPTION LINE\n\n#one #two",
            all,
        )
    }

    @Test
    fun detailsDropForeignIdentityExtrasFromAnotherProduct() {
        val session = PipelineSession()
        session.speechLanguage = "DEUTSCH"
        session.analysis = JSONObject()
            .put("product_category", "office")
            .put("observed_use_case", "desk organizer")
            .put("possible_scene", "lakeside kitchen microwave leftover")
        session.identityFingerprint = JSONObject()
            .put("overall_geometry", "desktop organizer tray with rectangular compartments")
            .put(
                "identity_critical_components",
                JSONArray()
                    .put("desktop compartments")
                    .put("circular upper vent")
                    .put("side bait tray")
                    .put("hanging ring"),
            )
        val text = DetailsBuilder.build(session)
        assertTrue(text.contains("desk organizer") || text.contains("office"))
        assertFalse(text.contains("circular upper vent"))
        assertFalse(text.contains("bait tray"))
        assertFalse(text.contains("hanging ring"))
        assertFalse(text.contains("lakeside"))
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
