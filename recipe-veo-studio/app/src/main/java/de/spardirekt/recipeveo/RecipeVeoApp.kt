package de.spardirekt.recipeveo

import android.app.Application
import de.spardirekt.recipeveo.data.db.AppDatabase
import de.spardirekt.recipeveo.data.repository.ProjectRepository
import de.spardirekt.recipeveo.generation.GenerationManager
import de.spardirekt.recipeveo.network.ChatClients
import de.spardirekt.recipeveo.network.OpenAiClient
import de.spardirekt.recipeveo.storage.SecureApiKeyStore
import de.spardirekt.recipeveo.storage.SettingsStore

class RecipeVeoApp : Application() {

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
        if (!apiKeyStore.hasKey()) {
            apiKeyStore.saveKey(ChatClients.DEMO_PREFIX)
        }
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
        lateinit var instance: RecipeVeoApp
            private set
    }
}
