package de.spardirekt.agents.pro.ui.create

import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ProjectStatus

/**
 * One-shot Result navigation for a single generate run.
 *
 * Completion is reported from stage, isRunning, and Room at once. The gate
 * must not open Result for a leftover Ready row, a FAILED run, or an empty
 * prompt fabricated after an API error.
 */
class ResultNavigationGate {
    private var offered = false
    private var runActive = false
    private var sawGenerating = false

    @Synchronized
    fun onGenerationStarted() {
        offered = false
        runActive = true
        sawGenerating = false
    }

    @Synchronized
    fun reset() {
        offered = false
        runActive = false
        sawGenerating = false
    }

    @Synchronized
    fun onFailed() {
        runActive = false
        sawGenerating = false
        offered = true
    }

    @Synchronized
    fun noteProgress(status: String, stage: GenerationStage) {
        if (!runActive) return
        if (status == ProjectStatus.Error.name || stage == GenerationStage.FAILED) {
            onFailed()
            return
        }
        if (status == ProjectStatus.Generating.name || GenerationProgress.isRunning(stage)) {
            sawGenerating = true
        }
    }

    /**
     * Offer Result only when this run actually reached a real ready prompt.
     * A previous Ready row, a FAILED project, or a blank prompt must not open
     * the result screen.
     */
    @Synchronized
    fun offerSuccessful(
        projectId: String,
        status: String,
        stage: GenerationStage,
        veoPrompt: String,
        errorState: String
    ): String? {
        if (!runActive || offered) return null
        if (projectId.isBlank()) return null
        if (!sawGenerating) return null
        if (status == ProjectStatus.Error.name || stage == GenerationStage.FAILED) {
            onFailed()
            return null
        }
        if (status != ProjectStatus.Ready.name) return null
        if (stage != GenerationStage.DONE) return null
        if (veoPrompt.isBlank()) return null
        if (errorState.isNotBlank()) return null
        offered = true
        runActive = false
        return projectId
    }
}
