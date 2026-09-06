package de.spardirekt.ugcagent.v3.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartGateTest {
    @Test
    fun enablesWhenImagesAndLanguageReady() {
        val debug = StartGate.evaluate(3, "РУССКИЙ", "IMAGES_READY", null, false, 3, 15)
        assertTrue(debug.getBoolean("startEnabled"))
        assertTrue(debug.getBoolean("imagesOk"))
        assertTrue(debug.getBoolean("languageOk"))
    }

    @Test
    fun disablesWhenTooFewImages() {
        val debug = StartGate.evaluate(2, "DEUTSCH", "IDLE", null, false, 3, 15)
        assertFalse(debug.getBoolean("startEnabled"))
        assertFalse(debug.getBoolean("imagesOk"))
    }

    @Test
    fun ignoresStaleLowConsistencyPause() {
        val debug = StartGate.evaluate(4, "DEUTSCH", "PAUSED", PauseReasons.LOW_CONSISTENCY, false, 3, 15)
        assertTrue(debug.getBoolean("stalePauseIgnored"))
        assertTrue(debug.getBoolean("startEnabled"))
    }

    @Test
    fun disablesWhenPipelineIsBusy() {
        val debug = StartGate.evaluate(4, "DEUTSCH", "PRODUCT_ANALYSIS", null, true, 3, 15)
        assertTrue(debug.getBoolean("busy"))
        assertFalse(debug.getBoolean("startEnabled"))
    }
}
