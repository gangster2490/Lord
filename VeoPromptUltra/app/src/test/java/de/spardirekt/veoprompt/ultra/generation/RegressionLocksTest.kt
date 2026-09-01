package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel
import org.junit.Assert.assertTrue
import org.junit.Test

class RegressionLocksTest {

    @Test
    fun panMustKeepIdentityDetails() {
        val model = ProductModel(
            productIdentity = "Deep black pan with wooden lid",
            visualSignature = listOf(
                "deep rounded bowl", "high sides", "wooden handle", "ferrule", "rivets", "hanging ring", "wooden lid"
            )
        )
        val prompt = Fixtures.validVeoPrompt(model)
        val violations = RegressionLocks.violations(prompt, model)
        assertTrue(violations.joinToString(), violations.isEmpty())
        assertTrue(prompt.contains("deep rounded bowl"))
        assertTrue(prompt.contains("wooden handle") || prompt.contains("handle"))
        assertTrue(prompt.contains("ferrule"))
        assertTrue(prompt.contains("rivet"))
        assertTrue(prompt.contains("hanging ring"))
        assertTrue(prompt.contains("wooden lid"))
        val lock = prompt.substringAfter("PRODUCT LOCK").substringBefore("SETTING")
        assertTrue(!lock.contains("wok"))
    }

    @Test
    fun wokSubstitutionIsFlagged() {
        val model = ProductModel(productIdentity = "pan with wooden lid")
        val bad = "a generic pan wok replacement with no lid"
        val hits = RegressionLocks.violations(bad, model)
        assertTrue(hits.any { it.startsWith("forbidden:") })
    }
}
