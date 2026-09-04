package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.CreativeDirection
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.ProductModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CreativeDirectorRulesTest {
    @Test
    fun autoDoesNotDefaultToLifestyle() {
        val plan = CreativeDirection(selectedMode = "LIFESTYLE", usePeople = true)
        val sanitized = CreativeDirectorRules.sanitizePlan(CreativeMode.AUTO, plan)
        assertEquals(CreativeMode.SHOWCASE.name, sanitized.selectedMode)
        assertFalse(sanitized.usePeople)
    }

    @Test
    fun preferredPatternUsesResultWhenFunctionExists() {
        val model = ProductModel(confirmedFunctions = listOf("drains water"))
        assertEquals(
            "RESULT / HOOK → PRODUCT IDENTITY → ONE SIMPLE ACTION → HERO",
            CreativeDirectorRules.preferredPattern(model)
        )
    }
}
