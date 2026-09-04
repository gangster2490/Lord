package de.spardirekt.veoprompt.ultra.ui.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultNavigationGateTest {
    @Test
    fun offersExactlyOnce() {
        val gate = ResultNavigationGate()
        gate.onGenerationStarted()
        assertEquals("abc", gate.offer("abc"))
        assertNull(gate.offer("abc"))
        gate.reset()
        assertEquals("abc", gate.offer("abc"))
    }

    @Test
    fun createRules() {
        assertTrue(CreateFormRules.canGenerate(1, false))
        assertEquals("Добавьте хотя бы одно фото товара", CreateFormRules.blockingHint(0, false))
        assertEquals(7, GenerationProgress.labels.size)
    }
}
