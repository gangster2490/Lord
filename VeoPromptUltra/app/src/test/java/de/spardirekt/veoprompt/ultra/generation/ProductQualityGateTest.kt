package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.AnalysisResult
import de.spardirekt.veoprompt.ultra.model.ConfidenceFact
import de.spardirekt.veoprompt.ultra.model.ProductModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductQualityGateTest {
    @Test
    fun dropsUnverifiedCastIronAndCapsSignature() {
        val model = ProductModel(
            confirmedMaterials = listOf("cast iron", "matte black"),
            visualSignature = (1..15).map { "detail-$it-shape" }
        )
        val analysis = AnalysisResult(
            visualFacts = listOf(ConfidenceFact("matte black bowl", "HIGH", "photo"))
        )
        val locked = ProductQualityGate.lockAppearance(model, analysis)
        assertFalse(locked.confirmedMaterials.contains("cast iron"))
        assertTrue(locked.confirmedMaterials.contains("matte black"))
        assertEquals(12, locked.visualSignature.size)
    }
}
