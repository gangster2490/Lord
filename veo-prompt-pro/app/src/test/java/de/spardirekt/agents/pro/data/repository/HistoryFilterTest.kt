package de.spardirekt.agents.pro.data.repository

import de.spardirekt.agents.pro.model.ProjectStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFilterTest {

    @Test
    fun readyAndErrorAlwaysShow() {
        assertTrue(HistoryFilter.include(ProjectStatus.Ready.name, 0, "FORMAT\n"))
        assertTrue(HistoryFilter.include(ProjectStatus.Error.name, 0, ""))
        assertTrue(HistoryFilter.include(ProjectStatus.Generating.name, 1, ""))
    }

    @Test
    fun emptyDraftHidden() {
        assertFalse(HistoryFilter.include(ProjectStatus.Draft.name, 0, ""))
        assertFalse(HistoryFilter.include(ProjectStatus.Draft.name, 0, "   "))
    }

    @Test
    fun draftWithPhotosOrPromptShows() {
        assertTrue(HistoryFilter.include(ProjectStatus.Draft.name, 1, ""))
        assertTrue(HistoryFilter.include(ProjectStatus.Draft.name, 0, "FORMAT\n"))
    }
}
