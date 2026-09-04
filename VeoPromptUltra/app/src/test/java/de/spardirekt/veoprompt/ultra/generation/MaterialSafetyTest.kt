package de.spardirekt.veoprompt.ultra.generation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialSafetyTest {
    @Test
    fun dropsUnverifiedCastIron() {
        val filtered = MaterialSafety.filterUnverifiedMaterials(
            listOf("cast iron", "matte black coating"),
            highConfidenceFacts = listOf("matte black coating")
        )
        assertFalse(filtered.any { it.contains("cast iron") })
        assertTrue(filtered.contains("matte black coating"))
    }

    @Test
    fun keepsHighConfidenceSteel() {
        val filtered = MaterialSafety.filterUnverifiedMaterials(
            listOf("stainless steel"),
            highConfidenceFacts = listOf("stainless steel body")
        )
        assertTrue(filtered.contains("stainless steel"))
    }
}
