package de.spardirekt.veoprompt.ultra.generation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TruncationGuardTest {
    @Test
    fun flagsEllipsis() {
        assertTrue(TruncationGuard.looksMechanicallyTruncated("PRODUCT LOCK\nKeep the pan..."))
        assertFalse(TruncationGuard.looksMechanicallyTruncated("PRODUCT LOCK\nKeep the pan."))
    }
}
