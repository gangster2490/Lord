package com.rob.aikeyboard.translate

import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.ai.AiPrompts
import com.rob.aikeyboard.data.TranslateEngine
import com.rob.aikeyboard.text.Language

class MissingModelsException(val languages: Set<Language>) : Exception("Модели перевода не загружены")

/** Picks the configured engine (on-device ML Kit or the cloud AI provider) for a translation. */
object Translate {

    suspend fun run(text: String, source: Language, target: Language, engine: TranslateEngine): String {
        val app = RobApp.instance
        return when (engine) {
            TranslateEngine.OFFLINE -> {
                val missing = app.translator.requiredModels(source, target) - app.translator.downloadedLanguages()
                if (missing.isNotEmpty()) throw MissingModelsException(missing)
                app.translator.translate(text, source, target)
            }
            TranslateEngine.AI -> {
                val (system, user) = AiPrompts.translationPrompt(text, target)
                AiPrompts.parseVariants(app.ai.complete(app.prefs.aiProvider, system, user)).first()
            }
        }
    }

    fun detect(text: String, fallback: Language): Language = RobApp.instance.detector.detect(text, fallback) ?: fallback
}
