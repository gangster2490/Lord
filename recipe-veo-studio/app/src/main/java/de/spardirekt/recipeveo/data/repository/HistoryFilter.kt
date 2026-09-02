package de.spardirekt.recipeveo.data.repository

import de.spardirekt.recipeveo.model.ProjectStatus

object HistoryFilter {
    fun include(status: String, imageCount: Int, veoPrompt: String): Boolean {
        if (status != ProjectStatus.Draft.name) return true
        return imageCount > 0 || veoPrompt.trim().isNotBlank()
    }
}
