package de.spardirekt.veoprompt.ultra

import android.app.Application
import de.spardirekt.veoprompt.ultra.data.db.AppDatabase
import de.spardirekt.veoprompt.ultra.data.repository.ProjectRepository
import de.spardirekt.veoprompt.ultra.generation.GenerationManager
import de.spardirekt.veoprompt.ultra.network.OpenAiClient
import de.spardirekt.veoprompt.ultra.storage.SecureApiKeyStore
import de.spardirekt.veoprompt.ultra.storage.SettingsStore

class VeoPromptUltraApp : Application() {

    lateinit var apiKeyStore: SecureApiKeyStore
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var projectRepository: ProjectRepository
        private set
    lateinit var generationManager: GenerationManager
        private set
    lateinit var openAiClient: OpenAiClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        apiKeyStore = SecureApiKeyStore(this)
        settingsStore = SettingsStore(this)
        openAiClient = OpenAiClient()
        val db = AppDatabase.get(this)
        projectRepository = ProjectRepository(db.projectDao())
        generationManager = GenerationManager(
            appContext = this,
            repository = projectRepository,
            apiKeyStore = apiKeyStore,
            settingsStore = settingsStore,
            openAi = openAiClient
        )
        generationManager.resumeInterruptedIfNeeded()
    }

    companion object {
        lateinit var instance: VeoPromptUltraApp
            private set
    }
}
