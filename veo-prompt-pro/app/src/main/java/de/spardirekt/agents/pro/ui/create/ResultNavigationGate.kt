package de.spardirekt.agents.pro.ui.create

/**
 * One-shot Result navigation for a single generate run.
 *
 * Generation completion is reported from stage, isRunning, and Room at once.
 * Without a gate those three sources push Result repeatedly, so back from
 * Result immediately opens Result again.
 */
class ResultNavigationGate {
    private var offered = false

    @Synchronized
    fun onGenerationStarted() {
        offered = false
    }

    @Synchronized
    fun reset() {
        offered = false
    }

    @Synchronized
    fun offer(projectId: String): String? {
        if (projectId.isBlank() || offered) return null
        offered = true
        return projectId
    }
}
