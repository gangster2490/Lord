package de.spardirekt.ugcclean

import android.app.Application
import de.spardirekt.ugcclean.data.ProjectStore
import de.spardirekt.ugcclean.data.SettingsStore
import de.spardirekt.ugcclean.gen.GenerationSession

class UgcCleanApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var projects: ProjectStore
        private set
    lateinit var session: GenerationSession
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        projects = ProjectStore(this)
        session = GenerationSession(this, projects, settings)
    }
}
