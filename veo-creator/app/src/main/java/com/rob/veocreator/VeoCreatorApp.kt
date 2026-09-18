package com.rob.veocreator

import android.app.Application
import com.rob.veocreator.data.api.GeminiVeoClient
import com.rob.veocreator.data.db.AppDatabase
import com.rob.veocreator.data.prefs.SecureApiKeyStore

class VeoCreatorApp : Application() {

    lateinit var apiKeyStore: SecureApiKeyStore
        private set
    lateinit var veoClient: GeminiVeoClient
        private set
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        apiKeyStore = SecureApiKeyStore(this)
        veoClient = GeminiVeoClient()
        database = AppDatabase.get(this)
    }
}
