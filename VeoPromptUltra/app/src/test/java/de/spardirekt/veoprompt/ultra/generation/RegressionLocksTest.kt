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
        val model = Fixtures.fishingChairModel()
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
        val model = Fixtures.fishingChairModel()
        val thin = Fixtures.validVeoPrompt(
            model.copy(visualSignature = listOf("metal frame", "padded backrest", "rubber feet"))
        )
        val hits = RegressionLocks.violations(thin, model)
        assertTrue(hits.any { it == "missing:tray" })
    }

    @Test
    fun phBitsKeepTipAndCollar() {
        val model = Fixtures.phBitsModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        assertTrue(prompt.contains("PH tip") || prompt.contains("PH"))
        assertTrue(prompt.contains("collar"))
    }

    @Test
    fun riceWasherKeepsBowlLidDrain() {
        val model = Fixtures.riceWasherModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        assertTrue(prompt.contains("bowl"))
        assertTrue(prompt.contains("lid"))
        assertTrue(prompt.contains("drain"))
    }

    @Test
    fun closedStoveCaseDoesNotInventBurner() {
        val model = Fixtures.stoveCaseModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        val lock = prompt.substringAfter("PRODUCT LOCK").substringBefore("SETTING")
        assertTrue(lock.contains("closed") && lock.contains("case"))
        val invented = prompt.replace(Regex("(?is)NEGATIVE PROMPT.*"), "")
        assertTrue(!invented.contains("open burner"))
        assertTrue(!invented.contains("flame"))
        assertTrue(!invented.contains("canister"))
    }

    @Test
    fun contactGrillKeepsPlatesAndRejectsInventedCoils() {
        val model = Fixtures.contactGrillModel()
        val prompt = Fixtures.validVeoPrompt(model)
        assertTrue(RegressionLocks.violations(prompt, model).isEmpty())
        assertTrue(prompt.contains("plates"))
        val bad = prompt + "\ninvented heating coils visible under the lid"
        assertTrue(RegressionLocks.violations(bad, model).any { it.startsWith("forbidden:") })
    }

    @Test
    fun everyRegressionProductMatchesItsSpec() {
        assertEquals("fishing_chair", RegressionLocks.matchingSpec(Fixtures.fishingChairModel())?.id)
        assertEquals("ph_screwdriver_bits", RegressionLocks.matchingSpec(Fixtures.phBitsModel())?.id)
        assertEquals("rice_washing_container", RegressionLocks.matchingSpec(Fixtures.riceWasherModel())?.id)
        assertEquals("closed_portable_stove_case", RegressionLocks.matchingSpec(Fixtures.stoveCaseModel())?.id)
        assertEquals("contact_grill", RegressionLocks.matchingSpec(Fixtures.contactGrillModel())?.id)
        assertEquals(
            "deep_black_pan_wooden_lid",
            RegressionLocks.matchingSpec(
                ProductModel(
                    productIdentity = "Deep black pan with wooden lid",
                    visualSignature = listOf("deep rounded bowl")
                )
            )?.id
        )
    }
}
