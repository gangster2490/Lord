package de.spardirekt.agents.pro.ui.create

import de.spardirekt.agents.pro.model.GenerationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationProgressTest {

    @Test
    fun railHasOneLabelPerStage() {
        assertEquals(6, GenerationProgress.labels.size)
        assertEquals(GenerationProgress.labels.size, GenerationProgress.stepCount)
    }

    @Test
    fun firstStageHighlightsFirstRowWithNothingCompleted() {
        assertEquals(0, GenerationProgress.activeIndex(GenerationStage.PHOTO_ANALYSIS))
        assertEquals(-1, GenerationProgress.completedThrough(GenerationStage.PHOTO_ANALYSIS))
    }

    @Test
    fun middleStageCompletesEveryEarlierRow() {
        assertEquals(3, GenerationProgress.activeIndex(GenerationStage.FINAL_PROMPT))
        assertEquals(2, GenerationProgress.completedThrough(GenerationStage.FINAL_PROMPT))
    }

    @Test
    fun doneCompletesTheWholeRail() {
        val last = GenerationProgress.labels.lastIndex
        assertEquals(last, GenerationProgress.activeIndex(GenerationStage.DONE))
        assertEquals(last, GenerationProgress.completedThrough(GenerationStage.DONE))
    }

    @Test
    fun idleAndFailedNeverReportProgress() {
        for (stage in listOf(GenerationStage.IDLE, GenerationStage.FAILED)) {
            assertFalse(GenerationProgress.isRunning(stage))
            assertEquals(0, GenerationProgress.activeIndex(stage))
            assertEquals(-1, GenerationProgress.completedThrough(stage))
            assertEquals("", GenerationProgress.statusLine(stage))
        }
    }

    @Test
    fun statusLineNamesTheLiveStep() {
        assertEquals(
            "Шаг 2 из 6 · Понимание товара",
            GenerationProgress.statusLine(GenerationStage.PRODUCT_MODEL)
        )
        assertEquals("Готово", GenerationProgress.statusLine(GenerationStage.DONE))
    }

    @Test
    fun railStaysVisibleWhileTheRunIsStarting() {
        assertTrue(GenerationProgress.showsRail(GenerationStage.IDLE, isGenerating = true))
        assertTrue(GenerationProgress.showsRail(GenerationStage.FINAL_PROMPT, isGenerating = false))
        assertFalse(GenerationProgress.showsRail(GenerationStage.IDLE, isGenerating = false))
        assertFalse(GenerationProgress.showsRail(GenerationStage.DONE, isGenerating = false))
    }
}

class CreateFormRulesTest {

    @Test
    fun generateNeedsAtLeastOnePhoto() {
        assertFalse(CreateFormRules.canGenerate(photoCount = 0, isGenerating = false))
        assertTrue(CreateFormRules.canGenerate(photoCount = 1, isGenerating = false))
    }

    @Test
    fun generateIsBlockedWhileARunIsInFlight() {
        assertFalse(CreateFormRules.canGenerate(photoCount = 3, isGenerating = true))
    }

    @Test
    fun theMissingPhotoIsExplainedBeforeTheTapNotAfter() {
        assertEquals(
            "Добавьте хотя бы одно фото товара",
            CreateFormRules.blockingHint(photoCount = 0, isGenerating = false)
        )
        assertEquals("", CreateFormRules.blockingHint(photoCount = 1, isGenerating = false))
        assertEquals("", CreateFormRules.blockingHint(photoCount = 0, isGenerating = true))
    }

    @Test
    fun emptyPhotoListReadsAsWordsNotZero() {
        assertEquals("Фото пока не загружены", CreateFormRules.photoCountLabel(0))
        assertEquals("1 фото загружено", CreateFormRules.photoCountLabel(1))
        assertEquals("4 фото загружено", CreateFormRules.photoCountLabel(4))
    }
}
