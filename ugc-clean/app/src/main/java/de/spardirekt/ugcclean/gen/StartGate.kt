package de.spardirekt.ugcclean.gen

import de.spardirekt.ugcclean.model.SpeechLanguage

object StartGate {
    const val MIN_PHOTOS = 3
    const val MAX_PHOTOS = 15

    fun canStart(photoCount: Int, hasKey: Boolean, running: Boolean): Boolean {
        return photoCount in MIN_PHOTOS..MAX_PHOTOS && hasKey && !running
    }

    fun blockReason(photoCount: Int, hasKey: Boolean, running: Boolean, language: SpeechLanguage?): String? {
        if (running) return "Ein Lauf läuft bereits."
        if (!hasKey) return "Bitte zuerst einen API-Key speichern."
        if (photoCount < MIN_PHOTOS) return "Mindestens $MIN_PHOTOS Fotos nötig."
        if (photoCount > MAX_PHOTOS) return "Maximal $MAX_PHOTOS Fotos."
        if (language == null) return "Bitte eine Sprache wählen."
        return null
    }
}
