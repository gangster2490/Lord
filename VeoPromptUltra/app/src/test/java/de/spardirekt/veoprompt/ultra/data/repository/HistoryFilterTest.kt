package de.spardirekt.veoprompt.ultra.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFilterTest {
    @Test
    fun hidesEmptyDrafts() {
        assertFalse(HistoryFilter.include("Draft", 0, ""))
        assertTrue(HistoryFilter.include("Draft", 1, ""))
        assertTrue(HistoryFilter.include("Ready", 0, "prompt"))
        assertTrue(HistoryFilter.include("Failed", 2, ""))
    }
}
