package de.spardirekt.ugcagent.v3.pipeline

import android.app.Application
import de.spardirekt.ugcagent.v3.data.ProjectRecord
import de.spardirekt.ugcagent.v3.worker.PipelineForegroundService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-scoped pipeline job. Survives Activity recreation and brief backgrounding.
 * Does not start a second job for the same run.
 */
class PipelineRunner(private val app: Application) {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val runningFlag = AtomicBoolean(false)

    @Volatile var liveProject: ProjectRecord? = null
        private set

    @Volatile var ui: Any? = null

    val isRunning: Boolean get() = runningFlag.get()

    fun adopt(candidate: ProjectRecord): ProjectRecord {
        synchronized(lock) {
            liveProject?.let { return it }
            liveProject = candidate
            return candidate
        }
    }

    fun replace(project: ProjectRecord): ProjectRecord {
        synchronized(lock) {
            if (isRunning) return liveProject ?: project
            liveProject = project
            return project
        }
    }

    fun attachUi(bridge: Any) {
        ui = bridge
    }

    fun detachUi(bridge: Any) {
        if (ui === bridge) ui = null
    }

    fun start(project: ProjectRecord, work: () -> Unit): Boolean {
        synchronized(lock) {
            if (runningFlag.get()) return false
            runningFlag.set(true)
            liveProject = project
        }
        val russian = project.speechLanguage.equals("РУССКИЙ", true)
        PipelineForegroundService.start(app, PipelineProgress.label(PipelineStage.IMAGES_READY, russian))
        executor.execute {
            try {
                work()
            } finally {
                runningFlag.set(false)
                PipelineForegroundService.stop(app)
            }
        }
        return true
    }

    fun notifyStage(stage: PipelineStage, russian: Boolean) {
        if (!isRunning) return
        PipelineForegroundService.start(app, PipelineProgress.label(stage, russian))
    }
}
