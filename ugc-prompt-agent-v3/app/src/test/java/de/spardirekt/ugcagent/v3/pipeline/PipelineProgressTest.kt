package de.spardirekt.ugcagent.v3.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineProgressTest {
    @Test
    fun displayStepsCoverTheRunnablePipelineOnce() {
        assertEquals(12, PipelineProgress.steps.size)
        assertFalse(PipelineProgress.steps.contains(PipelineStage.READY))
        assertEquals(PipelineStage.IMAGES_READY, PipelineProgress.steps.first())
        assertEquals(PipelineStage.FINAL_QUALITY_CHECK, PipelineProgress.steps.last())
    }

    @Test
    fun percentReaches100OnlyWhenReady() {
        val none = emptySet<PipelineStage>()
        assertEquals(0, PipelineProgress.percent(none, PipelineStage.IMAGES_READY))
        assertEquals(100, PipelineProgress.percent(PipelineProgress.steps.toSet(), PipelineStage.READY))
        val mid = setOf(PipelineStage.IMAGES_READY, PipelineStage.PRODUCT_ANALYSIS)
        val pct = PipelineProgress.percent(mid, PipelineStage.IDENTITY_EXTRACTION)
        assertTrue(pct in 1..99)
        assertEquals(2, PipelineProgress.index(PipelineStage.PRODUCT_ANALYSIS))
    }

    @Test
    fun labelsStayOnThisProductDesireLanguage() {
        assertEquals("Verkaufsidee", PipelineProgress.label(PipelineStage.PURCHASE_APPEAL, false))
        assertEquals("Идея продажи", PipelineProgress.label(PipelineStage.PURCHASE_APPEAL, true))
        val json = PipelineProgress.toJson(
            PipelineStage.PURCHASE_APPEAL,
            setOf(PipelineStage.IMAGES_READY),
            true,
            false,
        )
        assertTrue(json.getBoolean("running"))
        assertEquals(12, json.getJSONArray("steps").length())
        assertEquals("Verkaufsidee", json.getString("label"))
    }
}
