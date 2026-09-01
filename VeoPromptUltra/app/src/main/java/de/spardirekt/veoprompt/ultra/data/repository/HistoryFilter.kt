package de.spardirekt.veoprompt.ultra.data.repository

import de.spardirekt.veoprompt.ultra.model.ProjectStatus

object HistoryFilter {
    fun include(status: String, imageCount: Int, veoPrompt: String): Boolean {
        if (status != ProjectStatus.Draft.name) return true
        return imageCount > 0 || veoPrompt.trim().isNotBlank()
    }
}
