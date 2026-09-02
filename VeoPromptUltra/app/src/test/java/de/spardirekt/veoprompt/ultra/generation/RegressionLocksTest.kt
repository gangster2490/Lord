package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel
import org.junit.Assert.assertEquals
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
    fun allRegressionProductsHaveSpecs() {
        assertEquals(6, RegressionLocks.all.size)
        val ids = RegressionLocks.all.map { it.id }
        assertTrue(ids.containsAll(listOf(
            "deep_black_pan_wooden_lid",
            "fishing_chair",
            "ph_screwdriver_bits",
            "rice_washing_container",
            "closed_portable_stove_case",
            "contact_grill"
        )))
    }

    @Test
    fun wokSubstitutionIsFlagged() {
        val model = ProductModel(productIdentity = "pan with wooden lid")
        val bad = "a generic pan wok replacement with no lid"
        val hits = RegressionLocks.violations(bad, model)
        assertTrue(hits.any { it.startsWith("forbidden:") })
    }

    @Test
    fun fishingChairKeepsFrameTrayAndFeet() {
        val model = fishingChairModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        val lock = prompt.substringAfter("PRODUCT LOCK").substringBefore("SETTING")
        assertTrue(lock.contains("frame"))
        assertTrue(lock.contains("backrest"))
        assertTrue(lock.contains("tray"))
        assertTrue(lock.contains("feet"))
        assertTrue(!lock.contains("generic camping chair"))
    }

    @Test
    fun fishingChairMissingTrayIsFlagged() {
        val model = fishingChairModel()
        val thin = Fixtures.validVeoPrompt(
            model.copy(visualSignature = listOf("metal frame", "padded backrest", "rubber feet"))
        )
        val hits = RegressionLocks.violations(thin, model)
        assertTrue(hits.any { it == "missing:tray" })
    }

    @Test
    fun phBitsKeepTipAndCollar() {
        val model = phBitsModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        assertTrue(prompt.contains("PH tip") || prompt.contains("PH"))
        assertTrue(prompt.contains("collar"))
    }

    @Test
    fun riceWasherKeepsBowlLidDrain() {
        val model = riceWasherModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        assertTrue(prompt.contains("bowl"))
        assertTrue(prompt.contains("lid"))
        assertTrue(prompt.contains("drain"))
    }

    @Test
    fun closedStoveCaseDoesNotInventBurner() {
        val model = stoveCaseModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        val lock = prompt.substringAfter("PRODUCT LOCK").substringBefore("SETTING")
        assertTrue(lock.contains("closed case") || (lock.contains("closed") && lock.contains("case")))
        val invented = prompt.replace(Regex("(?is)NEGATIVE PROMPT.*"), "")
        assertTrue(!invented.contains("open burner"))
        assertTrue(!invented.contains("flame"))
        assertTrue(!invented.contains("canister"))
    }

    @Test
    fun contactGrillKeepsPlatesAndRejectsInventedCoils() {
        val model = contactGrillModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        assertTrue(prompt.contains("plates"))
        val bad = prompt + "\ninvented heating coils visible under the lid"
        assertTrue(RegressionLocks.violations(bad, model).any { it.startsWith("forbidden:") })
    }

    @Test
    fun everyRegressionProductMatchesItsSpec() {
        val pairs = listOf(
            fishingChairModel() to "fishing_chair",
            phBitsModel() to "ph_screwdriver_bits",
            riceWasherModel() to "rice_washing_container",
            stoveCaseModel() to "closed_portable_stove_case",
            contactGrillModel() to "contact_grill",
            ProductModel(
                productIdentity = "Deep black pan with wooden lid",
                visualSignature = listOf("deep rounded bowl")
            ) to "deep_black_pan_wooden_lid"
        )
        pairs.forEach { (model, id) ->
            assertEquals(id, RegressionLocks.matchingSpec(model)?.id)
        }
    }

    private fun fishingChairModel() = ProductModel(
        productCategory = "outdoor seating",
        productIdentity = "folding fishing chair",
        visualSignature = listOf("metal frame", "padded backrest", "side tray", "rubber feet")
    )

    private fun phBitsModel() = ProductModel(
        productCategory = "tools",
        productIdentity = "PH screwdriver bits",
        visualSignature = listOf("PH tip", "hex collar", "length markings")
    )

    private fun riceWasherModel() = ProductModel(
        productCategory = "kitchen",
        productIdentity = "rice washing container",
        visualSignature = listOf("clear bowl", "fitted lid", "side drain")
    )

    private fun stoveCaseModel() = ProductModel(
        productCategory = "camping stove",
        productIdentity = "closed portable stove case",
        visualSignature = listOf("closed case", "latches", "carry handle")
    )

    private fun contactGrillModel() = ProductModel(
        productCategory = "cookware",
        productIdentity = "contact grill",
        visualSignature = listOf("ridged plates", "hinge", "lid handle")
    }
}
