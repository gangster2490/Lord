package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionEngineTest {
    @Test
    fun microwaveWithoutVisualSplashUsesSaferFallback() {
        val analysis = JSONObject()
            .put("product_category", "kitchen")
            .put("observed_use_case", "cover food")
            .put("text_claims", JSONArray().put("preserves moisture").put("BPA-free").put("makes food softer"))
        val evidence = EvidenceModel.classify(analysis)
        val caption = CaptionEngine.finalize(
            raw = "Крышка сохраняет влагу, делает еду мягче и уменьшает количество брызг. BPA-free.",
            analysis = analysis,
            evidence = evidence,
            fingerprint = ProductIdentity.microwaveCoverFingerprint(),
            language = "РУССКИЙ",
            appendDisclosure = true,
        )
        assertEquals(CaptionEngine.RU_FALLBACK + "\nAnzeige", caption)
        assertFalse(caption.contains("влаг"))
        assertFalse(caption.contains("мягче"))
        assertFalse(caption.contains("BPA"))
        assertFalse(caption.contains("брызг"))
    }

    @Test
    fun visuallyConfirmedSplashUsesPreferredCaption() {
        val analysis = JSONObject()
            .put("product_category", "kitchen")
            .put("observed_use_case", "microwave cover")
            .put("visual_features_relevant_to_use", JSONArray().put("transparent dome").put("visible splash guard geometry"))
        val caption = CaptionEngine.finalize(
            raw = "seller pitch",
            analysis = analysis,
            evidence = EvidenceModel.classify(analysis),
            fingerprint = ProductIdentity.microwaveCoverFingerprint(),
            language = "РУССКИЙ",
            appendDisclosure = false,
        )
        assertEquals(CaptionEngine.RU_PREFERRED, caption)
    }

    @Test
    fun germanFallbackOmitsUnverifiedPerformanceClaims() {
        val analysis = JSONObject()
            .put("product_category", "kitchen")
            .put("observed_use_case", "cover food")
            .put("text_claims", JSONArray().put("bewahrt Feuchtigkeit").put("anti-scratch"))
        val caption = CaptionEngine.finalize(
            raw = "Deckel bewahrt Feuchtigkeit, macht das Essen weicher und ist anti-scratch.",
            analysis = analysis,
            evidence = EvidenceModel.classify(analysis),
            fingerprint = ProductIdentity.microwaveCoverFingerprint(),
            language = "DEUTSCH",
            appendDisclosure = true,
        )
        assertEquals(CaptionEngine.DE_FALLBACK + "\nWerbung", caption)
        assertFalse(caption.contains("Feuchtigkeit"))
        assertFalse(caption.contains("weicher"))
        assertFalse(caption.contains("anti-scratch"))
    }

    @Test
    fun kitchenPanDoesNotInheritMicrowaveCaption() {
        val analysis = JSONObject()
            .put("product_category", "kitchen")
            .put("observed_use_case", "frying pan")
            .put("possible_scene", "microwave cover leftover")
        assertFalse(CaptionEngine.isMicrowaveCover(analysis, ProductIdentity.cookwarePanFingerprint()))
        val caption = CaptionEngine.finalize(
            raw = "seller pitch",
            analysis = analysis,
            evidence = EvidenceModel.classify(analysis),
            fingerprint = ProductIdentity.cookwarePanFingerprint(),
            language = "DEUTSCH",
            appendDisclosure = false,
        )
        assertFalse(caption.contains("Mikrowelle"))
        assertFalse(caption.contains("Tellerabdeckung"))
        assertFalse(caption.contains("микроволн"))
    }
}
