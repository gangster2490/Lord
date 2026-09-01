package de.spardirekt.agents.pro.data.repository

import de.spardirekt.agents.pro.model.ProjectStatus

object HistoryFilter {
    fun include(status: String, imageCount: Int, veoPrompt: String): Boolean {
        if (status != ProjectStatus.Draft.name) return true
        return imageCount > 0 || veoPrompt.trim().isNotBlank()
    }
}
