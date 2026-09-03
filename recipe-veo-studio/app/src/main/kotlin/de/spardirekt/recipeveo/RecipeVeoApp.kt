package de.spardirekt.recipeveo

import android.app.Application
import de.spardirekt.recipeveo.storage.ApiKeyStore

class RecipeVeoApp : Application() {
    lateinit var apiKeyStore: ApiKeyStore
        private set

    override fun onCreate() {
        super.onCreate()
        apiKeyStore = ApiKeyStore(this)
    }
}
