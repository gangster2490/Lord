package de.spardirekt.agents.pro.ui.create

import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ProjectStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultNavigationGateTest {

    @Test
    fun offersOnceAfterGeneratingThenReady() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        gate.noteProgress(ProjectStatus.Generating.name, GenerationStage.PHOTO_ANALYSIS)
        assertEquals(
            "p1",
            gate.offerSuccessful(
                projectId = "p1",
                status = ProjectStatus.Ready.name,
                stage = GenerationStage.DONE,
                veoPrompt = "FORMAT\n9:16",
                errorState = ""
            )
        )
        assertNull(
            gate.offerSuccessful(
                projectId = "p1",
                status = ProjectStatus.Ready.name,
                stage = GenerationStage.DONE,
                veoPrompt = "FORMAT\n9:16",
                errorState = ""
            )
        )
    }

    @Test
    fun newRunCanOfferAgain() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        gate.noteProgress(ProjectStatus.Generating.name, GenerationStage.PHOTO_ANALYSIS)
        assertEquals("p1", ready(gate, "p1"))
        gate.onGenerationStarted()
        gate.noteProgress(ProjectStatus.Generating.name, GenerationStage.PHOTO_ANALYSIS)
        assertEquals("p1", ready(gate, "p1"))
    }

    @Test
    fun staleReadyAfterStartDoesNotOpenResult() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        assertNull(
            gate.offerSuccessful(
                projectId = "p1",
                status = ProjectStatus.Ready.name,
                stage = GenerationStage.DONE,
                veoPrompt = "old leftover prompt",
                errorState = ""
            )
        )
    }

    @Test
    fun apiErrorAfterGeneratingDoesNotOpenResult() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        gate.noteProgress(ProjectStatus.Generating.name, GenerationStage.PHOTO_ANALYSIS)
        gate.noteProgress(ProjectStatus.Error.name, GenerationStage.FAILED)
        assertNull(
            gate.offerSuccessful(
                projectId = "p1",
                status = ProjectStatus.Error.name,
                stage = GenerationStage.FAILED,
                veoPrompt = "old leftover prompt",
                errorState = "API ключ недействителен."
            )
        )
        assertNull(ready(gate, "p1"))
    }

    @Test
    fun leftoverDoneStageAfterFailureIsIgnored() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        gate.noteProgress(ProjectStatus.Generating.name, GenerationStage.PHOTO_ANALYSIS)
        gate.onFailed()
        assertNull(
            gate.offerSuccessful(
                projectId = "p1",
                status = ProjectStatus.Ready.name,
                stage = GenerationStage.DONE,
                veoPrompt = "FORMAT\n9:16",
                errorState = ""
            )
        )
    }

    @Test
    fun blankPromptIsNotAReadyResult() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        gate.noteProgress(ProjectStatus.Generating.name, GenerationStage.FINALIZATION)
        assertNull(
            gate.offerSuccessful(
                projectId = "p1",
                status = ProjectStatus.Ready.name,
                stage = GenerationStage.DONE,
                veoPrompt = "   ",
                errorState = ""
            )
        )
    }

    @Test
    fun blankIdIsIgnored() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        gate.noteProgress(ProjectStatus.Generating.name, GenerationStage.PHOTO_ANALYSIS)
        assertNull(ready(gate, ""))
        assertEquals("p1", ready(gate, "p1"))
    }

    private fun ready(gate: ResultNavigationGate, id: String): String? =
        gate.offerSuccessful(
            projectId = id,
            status = ProjectStatus.Ready.name,
            stage = GenerationStage.DONE,
            veoPrompt = "FORMAT\n9:16",
            errorState = ""
        )
}
