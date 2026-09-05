package de.spardirekt.ugcagent.v3.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRulesTest {
    @Test
    fun exactlyThreeIsEnough() {
        assertFalse(ImageRules.canAnalyse(0))
        assertFalse(ImageRules.canAnalyse(2))
        assertTrue(ImageRules.canAnalyse(3))
        assertTrue(ImageRules.canAnalyse(20))
        assertFalse(ImageRules.canAnalyse(21))
        assertEquals("Для анализа нужно минимум 3 изображения.", ImageRules.needMoreMessage())
    }
}
