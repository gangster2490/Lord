package de.spardirekt.recipeveo.ui.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultNavigationGateTest {

    @Test
    fun offersOncePerGeneration() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        assertEquals("p1", gate.offer("p1"))
        assertNull(gate.offer("p1"))
        assertNull(gate.offer("p2"))
    }

    @Test
    fun newRunCanOfferAgain() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        assertEquals("p1", gate.offer("p1"))
        gate.onGenerationStarted()
        assertEquals("p1", gate.offer("p1"))
    }

    @Test
    fun blankIdIsIgnored() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        assertNull(gate.offer(""))
        assertEquals("p1", gate.offer("p1"))
    }
}
