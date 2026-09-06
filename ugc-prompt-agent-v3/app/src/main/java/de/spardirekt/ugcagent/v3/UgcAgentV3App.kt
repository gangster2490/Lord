package de.spardirekt.ugcagent.v3

import android.app.Application
import de.spardirekt.ugcagent.v3.pipeline.PipelineRunner

class UgcAgentV3App : Application() {
    lateinit var pipelineRunner: PipelineRunner
        private set

    override fun onCreate() {
        super.onCreate()
        pipelineRunner = PipelineRunner(this)
    }
}
