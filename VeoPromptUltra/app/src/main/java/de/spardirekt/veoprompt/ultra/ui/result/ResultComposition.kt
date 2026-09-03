package de.spardirekt.veoprompt.ultra.ui.result

import de.spardirekt.veoprompt.ultra.data.db.ProjectEntity
import de.spardirekt.veoprompt.ultra.generation.GeminiVeoPromptCleanup

/**
 * Composes what the Result screen shows and copies into Gemini / VEO.
 * Always rebuilds the Gemini body through GeminiVeoPromptCleanup.
 * Does not rewrite stored veoPrompt and does not drop the safety sanitizer.
 */
object ResultComposition {

    fun geminiPrompt(
        storedPrompt: String,
        voiceover: String,
        title: String,
        hashtags: List<String>,
        marketplace: Boolean,
        tiktokShopMode: Boolean
    ): String {
        val raw = storedPrompt.trim()
        if (raw.isBlank()) return ""
        val composed = GeminiVeoPromptCleanup.composeCopiedPrompt(
            rawPrompt = raw,
            voiceover = voiceover,
            title = title,
            hashtags = hashtags,
            marketplace = marketplace,
            tiktokShopMode = tiktokShopMode
        )
        return GeminiVeoPromptCleanup.finalCleanupCopiedPrompt(
            composed,
            marketplace = marketplace
        )
    }

    fun geminiPrompt(entity: ProjectEntity, storedTags: List<String>): String {
        return geminiPrompt(
            storedPrompt = entity.veoPrompt,
            voiceover = entity.voiceover,
            title = entity.title,
            hashtags = storedTags,
            marketplace = marketplaceDetected(entity),
            tiktokShopMode = entity.tiktokShopMode
        )
    }

    fun marketplaceDetected(entity: ProjectEntity): Boolean {
        val analysis = entity.analysisResultJson
        val model = entity.productModelJson
        return analysis.contains("\"marketplaceDetected\": true", ignoreCase = true) ||
            analysis.contains("\"marketplaceDetected\":true", ignoreCase = true) ||
            model.contains("\"hasMarketplaceScreenshots\": true", ignoreCase = true) ||
            model.contains("\"hasMarketplaceScreenshots\":true", ignoreCase = true)
    }
}
