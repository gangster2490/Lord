package de.spardirekt.recipeveo.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {

    @Test
    fun createFreshUsesReservedToken() {
        assertEquals("__new__", Routes.NEW_PROJECT)
        assertEquals("create?projectId=__new__", Routes.createFresh())
        assertTrue(Routes.create("abc").contains("abc"))
    }
}
