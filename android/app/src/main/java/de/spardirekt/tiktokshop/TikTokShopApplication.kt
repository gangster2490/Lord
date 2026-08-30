package de.spardirekt.tiktokshop

import android.app.Application
import de.spardirekt.tiktokshop.data.AppPreferences
import de.spardirekt.tiktokshop.data.ClaudeApiClient
import de.spardirekt.tiktokshop.data.OpenAiApiClient

class TikTokShopApplication : Application() {
    lateinit var preferences: AppPreferences
        private set
    val claudeApi: ClaudeApiClient by lazy { ClaudeApiClient() }
    val openAiApi: OpenAiApiClient by lazy { OpenAiApiClient() }

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
    }
}
