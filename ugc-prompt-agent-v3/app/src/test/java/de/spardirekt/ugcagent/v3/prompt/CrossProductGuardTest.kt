package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossProductGuardTest {
    private val dirtyIdentity = """
PRODUCT IDENTITY LOCK:
1. circular upper vent
2. side bait tray
3. hanging ring
4. two separate rectangular modules
5. desktop organizer compartments
SETTING:
Ordinary cozy home kitchen by the lakeside fishing spot.
ACTION:
one hand grips the hanging ring of the referenced pan near the microwave.
SPEECH:
"Вот так на рыбалке сидеть уже совсем другое дело."
    """.trimIndent()

    @Test
    fun dirtyIdentityLockDoesNotSurviveOnAnOfficeProduct() {
        val analysis = JSONObject()
            .put("product_category", "office")
            .put("observed_use_case", "desk organizer")
            .put("observed_context", "desk")
        val fingerprint = JSONObject().put("overall_geometry", "desktop organizer tray with rectangular compartments")
        val extras = listOf(
            "circular upper vent",
            "side bait tray",
            "hanging ring",
            "desktop organizer compartments",
        )
        val features = ProductIdentity.compactIdentityFeatures(fingerprint, extras, analysis)
        assertTrue(features.any { it.contains("organizer", ignoreCase = true) || it.contains("desktop", ignoreCase = true) })
        assertFalse(features.any { CrossProductGuard.isForeignIdentityLine(it, fingerprint, analysis) })
        assertFalse(features.joinToString(" ").contains("circular upper vent", ignoreCase = true))
        assertFalse(features.joinToString(" ").contains("bait tray", ignoreCase = true))
        assertFalse(features.joinToString(" ").contains("hanging ring", ignoreCase = true))

        val prompt = ProductLock.finalizeClean(
            dirtyIdentity,
            fingerprint,
            "VEO",
            "DEUTSCH",
            "Вот так на рыбалке сидеть уже совсем другое дело.",
            true,
            analysis,
        )
        assertNoMicrowaveFamily(prompt)
        assertNoFishingFamily(prompt)
        assertNoPanFamily(prompt)
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertFalse(prompt.contains("рыбал"))
        assertTrue(prompt.contains("desk", ignoreCase = true) || prompt.contains("office", ignoreCase = true))
        assertEquals(
            emptyList<String>(),
            PromptComposer.canonicalFailures(prompt, "DEUTSCH", analysis, null, fingerprint),
        )
    }

    @Test
    fun fishingPromptKeepsBaitTrayAndDropsMicrowaveAndPanLanguage() {
        val analysis = JSONObject()
            .put("product_category", "outdoor")
            .put("observed_use_case", "fishing chair")
            .put("observed_context", "lakeside")
        val fingerprint = CreativeStrategyEngine.fishingChairFingerprint()
        val prompt = ProductLock.finalizeClean(
            dirtyIdentity,
            fingerprint,
            "VEO",
            "РУССКИЙ",
            "Ich mag's, wenn in der Küche alles einfach bleibt.",
            true,
            analysis,
        )
        assertTrue(prompt.contains("lakeside") || prompt.contains("riverside"))
        assertTrue(prompt.contains("bait tray") || prompt.contains("already seated"))
        assertNoMicrowaveFamily(prompt)
        assertNoPanFamily(prompt)
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertFalse(prompt.contains("Mikrowelle"))
        val hook = ProductLock.extractSpokenHooks(prompt).first()
        assertTrue(hook.contains("рыбал") || hook.contains("берег"))
        assertFalse(hook.contains("Küche"))
        assertFalse(hook.contains("микроволн"))
    }

    @Test
    fun microwavePromptKeepsVentAndDropsFishingAndPanLanguage() {
        val analysis = JSONObject().put("product_category", "kitchen").put("observed_use_case", "cover food")
        val fingerprint = ProductIdentity.microwaveCoverFingerprint()
        val prompt = ProductLock.finalizeClean(
            dirtyIdentity,
            fingerprint,
            "VEO",
            "DEUTSCH",
            "So sitzt sich's beim Angeln schon ganz anders.",
            true,
            analysis,
        )
        assertTrue(ProductLock.preservesMicrowaveCover(prompt))
        assertTrue(prompt.contains("circular upper vent", ignoreCase = true))
        assertNoFishingFamily(prompt)
        assertNoPanFamily(prompt)
        val hook = ProductLock.extractSpokenHooks(prompt).first()
        assertFalse(hook.contains("Angeln"))
        assertFalse(hook.contains("рыбал"))
    }

    @Test
    fun panPromptKeepsHangingRingAndDropsMicrowaveAndFishingLanguage() {
        val analysis = JSONObject().put("product_category", "kitchen").put("observed_use_case", "frying pan")
        val fingerprint = ProductIdentity.cookwarePanFingerprint()
        val prompt = ProductLock.finalizeClean(
            dirtyIdentity,
            fingerprint,
            "VEO",
            "DEUTSCH",
            "Вот так на рыбалке сидеть уже совсем другое дело.",
            true,
            analysis,
        )
        assertTrue(prompt.contains("hanging ring", ignoreCase = true))
        assertTrue(prompt.contains("wooden handle", ignoreCase = true) || prompt.contains("lid", ignoreCase = true))
        assertNoMicrowaveFamily(prompt)
        assertNoFishingFamily(prompt)
        assertFalse(prompt.contains("cylindrical reservoir", ignoreCase = true))
        assertFalse(prompt.contains("steam vent", ignoreCase = true))
    }

    @Test
    fun leakingSpokenHookIsWeakForTheWrongProduct() {
        val analysis = JSONObject().put("product_category", "office").put("observed_use_case", "desk organizer")
        val plan = CreativeStrategyEngine.plan(analysis)
        assertTrue(
            HookEngine.isWeak(
                "Вот так на рыбалке сидеть уже совсем другое дело.",
                "РУССКИЙ",
                analysis,
                plan,
            ),
        )
        assertTrue(
            HookEngine.isWeak(
                "Keine Lust, die Mikrowelle nach jedem Aufwärmen zu putzen?",
                "DEUTSCH",
                analysis,
                plan,
            ),
        )
    }

    @Test
    fun pollutedAnalysisContextDoesNotTurnAnOfficeProductIntoKitchenOrFishing() {
        val analysis = JSONObject()
            .put("product_category", "office")
            .put("observed_use_case", "desk organizer")
            .put("observed_context", "desk")
            .put("possible_scene", "Ordinary cozy home kitchen by the lakeside fishing spot")
            .put("possible_pain_point", "circular upper vent and hanging ring leftover from another product")
            .put("notes", "microwave cover, bait tray, frying pan, Mikrowelle, рыбалка")
        val fingerprint = JSONObject()
            .put("overall_geometry", "desktop organizer tray with rectangular compartments")
            .put(
                "identity_critical_components",
                org.json.JSONArray()
                    .put("desktop compartments")
                    .put("circular upper vent")
                    .put("side bait tray")
                    .put("hanging ring"),
            )
        assertFalse(CreativeStrategyEngine.looksLikeFishingChair(fingerprint, analysis))
        assertFalse(ProductIdentity.looksLikeMicrowaveCover(fingerprint))
        assertFalse(ProductIdentity.looksLikeCookwarePan(fingerprint, analysis))
        val plan = CreativeStrategyEngine.plan(analysis, fingerprint)
        assertEquals(CreativeStrategyEngine.SettingType.OFFICE, plan.settingType)
        assertFalse(plan.primary == CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY)
        assertFalse(plan.primary == CreativeStrategyEngine.Motivation.PROBLEM_SOLVER)
        assertFalse(plan.setting.contains("lakeside", ignoreCase = true))
        assertFalse(plan.setting.contains("kitchen", ignoreCase = true))
        assertFalse(plan.formatTone.contains("lived-in kitchen", ignoreCase = true))
        assertFalse(plan.formatTone.contains("outdoor hobby", ignoreCase = true))
        val prompt = ProductLock.finalizeClean(
            dirtyIdentity,
            fingerprint,
            "VEO",
            "DEUTSCH",
            "So sitzt sich's beim Angeln schon ganz anders.",
            true,
            analysis,
        )
        assertTrue(prompt.contains("desk", ignoreCase = true) || prompt.contains("office", ignoreCase = true))
        assertNoMicrowaveFamily(prompt)
        assertNoFishingFamily(prompt)
        assertNoPanFamily(prompt)
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertFalse(prompt.contains("Warm, homely"))
    }

    @Test
    fun pollutedLivingRoomChairDoesNotBecomeAFishingScene() {
        val analysis = JSONObject()
            .put("product_category", "furniture")
            .put("observed_use_case", "armchair")
            .put("observed_context", "living room")
            .put("possible_scene", "lakeside fishing chair with bait tray next to a microwave")
        val fingerprint = JSONObject().put("overall_geometry", "upholstered armchair with armrests and stable legs")
        assertFalse(CreativeStrategyEngine.looksLikeFishingChair(fingerprint, analysis))
        val plan = CreativeStrategyEngine.plan(analysis, fingerprint)
        assertEquals(CreativeStrategyEngine.SettingType.LIVING_ROOM, plan.settingType)
        assertEquals(CreativeStrategyEngine.Motivation.COMFORT, plan.primary)
        assertFalse(plan.setting.contains("lakeside", ignoreCase = true))
        assertFalse(plan.human.contains("tray"))
        val prompt = ProductLock.finalizeClean(
            "SETTING:\nReal lakeside or riverside fishing spot.\nACTION:\nalready seated with the bait tray.",
            fingerprint,
            "VEO",
            "DEUTSCH",
            null,
            true,
            analysis,
        )
        assertTrue(prompt.contains("living room", ignoreCase = true))
        assertNoFishingFamily(prompt)
        assertNoMicrowaveFamily(prompt)
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
    }

    @Test
    fun travelBathroomWorkshopAndUnknownKeepTheirOwnContextWhenLeftoversArePresent() {
        data class Case(
            val category: String,
            val use: String,
            val context: String,
            val geometry: String,
            val setting: CreativeStrategyEngine.SettingType,
        )
        val cases = listOf(
            Case("travel", "portable folding suitcase", "airport", "soft travel bag with zipper and carry handle", CreativeStrategyEngine.SettingType.TRAVEL),
            Case("personal care", "soap dispenser", "bathroom", "pump bottle with cylindrical body", CreativeStrategyEngine.SettingType.BATHROOM),
            Case("tools", "bench clamp", "workshop", "metal clamp with screw handle", CreativeStrategyEngine.SettingType.WORKSHOP),
            Case("unknown gadget", "small household widget", "white background packshot", "small unknown household widget", CreativeStrategyEngine.SettingType.INDOOR_NEUTRAL),
        )
        cases.forEach { item ->
            val analysis = JSONObject()
                .put("product_category", item.category)
                .put("observed_use_case", item.use)
                .put("observed_context", item.context)
                .put("possible_scene", "Ordinary cozy home kitchen by the lakeside fishing spot with a microwave cover")
                .put("notes", "circular upper vent, side bait tray, hanging ring, desk organizer")
            val fingerprint = JSONObject().put("overall_geometry", item.geometry)
            val plan = CreativeStrategyEngine.plan(analysis, fingerprint)
            assertEquals(item.use, item.setting, plan.settingType)
            assertFalse(item.use, plan.setting.contains("Ordinary cozy home kitchen"))
            assertFalse(item.use, plan.setting.contains("lakeside"))
            assertFalse(item.use, plan.formatTone.contains("lived-in kitchen feeling"))
            val prompt = ProductLock.finalizeClean(
                dirtyIdentity,
                fingerprint,
                "VEO",
                "DEUTSCH",
                "Вот так на рыбалке сидеть уже совсем другое дело.",
                true,
                analysis,
            )
            assertNoMicrowaveFamily(prompt)
            assertNoFishingFamily(prompt)
            assertNoPanFamily(prompt)
            assertFalse(item.use, prompt.contains("Ordinary cozy home kitchen"))
            assertFalse(item.use, prompt.contains("Warm, homely"))
        }
    }

    private fun assertNoMicrowaveFamily(text: String) {
        assertFalse(text.contains("circular upper vent", ignoreCase = true))
        assertFalse(text.contains("rectangular modules", ignoreCase = true))
        assertFalse(text.contains("green circular base ring", ignoreCase = true))
        assertFalse(text.contains("Mikrowelle"))
        assertFalse(text.contains("микроволн"))
        assertFalse(Regex("(?i)\\bmicrowave\\b").containsMatchIn(text))
    }

    private fun assertNoFishingFamily(text: String) {
        assertFalse(text.contains("lakeside", ignoreCase = true))
        assertFalse(text.contains("bait tray", ignoreCase = true))
        assertFalse(text.contains("рыбал"))
        assertFalse(text.contains("beim Angeln", ignoreCase = true))
    }

    private fun assertNoPanFamily(text: String) {
        assertFalse(text.contains("hanging ring", ignoreCase = true))
        assertFalse(text.contains("referenced pan", ignoreCase = true))
        assertFalse(text.contains("wooden handle of the referenced pan", ignoreCase = true))
    }
}
