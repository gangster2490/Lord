package de.spardirekt.veoprompt.ultra.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    @Test
    fun resultLoadsByProjectId() {
        assertEquals("result/xyz", Routes.result("xyz"))
        assertTrue(Routes.RESULT.contains("{projectId}"))
        assertEquals("generation/xyz", Routes.generation("xyz"))
    }
}
