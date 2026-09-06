package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeStrategyEngineTest {
    @Test
    fun fishingChairUsesOutdoorComfortNotKitchen() {
        val analysis = JSONObject()
            .put("product_category", "outdoor")
            .put("observed_use_case", "fishing chair")
            .put("observed_context", "lakeside")
        val plan = CreativeStrategyEngine.plan(analysis, CreativeStrategyEngine.fishingChairFingerprint())
        assertEquals(CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY, plan.primary)
        assertEquals(CreativeStrategyEngine.HookType.OUTDOOR, plan.hookType)
        assertTrue(
            plan.idea == CreativeStrategyEngine.SellingIdea.COMFORT ||
                plan.idea == CreativeStrategyEngine.SellingIdea.OUTDOOR_PRACTICALITY,
        )
        assertFalse(plan.idea == CreativeStrategyEngine.SellingIdea.CONVENIENCE)
        assertFalse(plan.idea == CreativeStrategyEngine.SellingIdea.HOME_FEELING)
        assertFalse(plan.desire.contains("convenience"))
        assertTrue(plan.desire.isNotBlank())
        assertTrue(plan.setting.contains("lakeside") || plan.setting.contains("riverside"))
        assertFalse(plan.setting.contains("kitchen", ignoreCase = true))
        assertTrue(plan.action.contains("already seated"))
        assertFalse(plan.action.contains("fold", ignoreCase = true) && plan.action.startsWith("Fold"))
        assertTrue(plan.human.contains("seated") || plan.human.contains("tray"))
        val hook = HookEngine.generate(analysis, "РУССКИЙ", CreativeStrategyEngine.fishingChairFingerprint(), plan)
        assertTrue(hook.contains("рыбал") || hook.contains("берег"))
        assertFalse(HookEngine.isWeak(hook, "РУССКИЙ", analysis, plan))
        assertFalse(hook.contains("микроволн"))
        val prompt = ProductLock.finalizeClean(
            "ACTION:\nfold the chair and rotate the backrest 180 degrees.",
            CreativeStrategyEngine.fishingChairFingerprint(),
            "VEO",
            "РУССКИЙ",
            hook,
            true,
            analysis,
        )
        assertTrue(prompt.contains("lakeside") || prompt.contains("riverside"))
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertTrue(prompt.contains("already seated"))
        assertTrue(prompt.contains("Do not fold"))
        assertFalse(prompt.contains("microwave", ignoreCase = true))
        assertFalse(prompt.contains("Mikrowelle"))
        assertFalse(prompt.contains("микроволн"))
        assertFalse(prompt.contains("hanging ring", ignoreCase = true))
        assertFalse(prompt.contains("referenced pan", ignoreCase = true))
        assertFalse(prompt.contains("circular upper vent", ignoreCase = true))
        assertFalse(prompt.substringAfter("ACTION:").substringBefore("HUMAN").contains("fold the chair and rotate"))
        PromptComposer.CANONICAL_HEADINGS.forEach { heading ->
            assertEquals(heading, 1, PromptComposer.headingCounts(prompt)[heading] ?: 0)
        }
        assertTrue(HookEngine.isWeak("Приятно, когда дома всё просто и по-своему уютно.", "РУССКИЙ", analysis, plan))
        assertTrue(HookEngine.isWeak("Это кресло выдерживает любой вес.", "РУССКИЙ", analysis, plan))
    }

    @Test
    fun microwaveKeepsProblemHookAndKitchenIdentity() {
        val analysis = JSONObject().put("product_category", "kitchen").put("observed_use_case", "cover food")
        val plan = CreativeStrategyEngine.plan(analysis, ProductIdentity.microwaveCoverFingerprint())
        assertEquals(CreativeStrategyEngine.Motivation.PROBLEM_SOLVER, plan.primary)
        assertTrue(plan.setting.contains("kitchen", ignoreCase = true))
        val hook = HookEngine.generate(analysis, "РУССКИЙ", ProductIdentity.microwaveCoverFingerprint(), plan)
        assertTrue(hook.contains("микроволн") || hook.contains("разогрев") || hook.contains("убор"))
        val prompt = ProductLock.finalizeClean(
            "ACTION:\none hand grips the handle.",
            ProductIdentity.microwaveCoverFingerprint(),
            "VEO",
            "DEUTSCH",
            null,
            true,
            analysis,
        )
        assertTrue(ProductLock.preservesMicrowaveCover(prompt))
        assertTrue(prompt.contains("Warm, homely"))
        assertTrue(prompt.contains("kitchen", ignoreCase = true))
        assertFalse(prompt.contains("lakeside", ignoreCase = true))
        assertFalse(prompt.contains("bait tray", ignoreCase = true))
        assertFalse(prompt.contains("рыбал"))
        assertFalse(prompt.contains("hanging ring", ignoreCase = true))
    }

    @Test
    fun panKeepsHomeKitchenHook() {
        val analysis = JSONObject().put("product_category", "kitchen").put("observed_use_case", "frying pan")
        val plan = CreativeStrategyEngine.plan(analysis, ProductIdentity.cookwarePanFingerprint())
        assertEquals(CreativeStrategyEngine.Motivation.HOME_COZY, plan.primary)
        assertEquals(CreativeStrategyEngine.SellingIdea.HOME_FEELING, plan.idea)
        assertEquals(CreativeStrategyEngine.HookType.HOME, plan.hookType)
        val hook = HookEngine.generate(analysis, "DEUTSCH", ProductIdentity.cookwarePanFingerprint(), plan)
        assertTrue(hook.contains("Küche") || hook.contains("Hause") || hook.contains("Pfanne") || hook.contains("mag"))
        assertTrue(plan.action.contains("wooden handle") || plan.action.contains("lid"))
        val prompt = ProductLock.finalizeClean(
            "ACTION:\none hand touches the wooden handle.",
            ProductIdentity.cookwarePanFingerprint(),
            "VEO",
            "DEUTSCH",
            hook,
            true,
            analysis,
        )
        assertTrue(prompt.contains("hanging ring", ignoreCase = true) || prompt.contains("wooden handle", ignoreCase = true))
        assertFalse(prompt.contains("circular upper vent", ignoreCase = true))
        assertFalse(prompt.contains("rectangular modules", ignoreCase = true))
        assertFalse(prompt.contains("lakeside", ignoreCase = true))
        assertFalse(prompt.contains("bait tray", ignoreCase = true))
        assertFalse(prompt.contains("cylindrical reservoir", ignoreCase = true))
        assertFalse(prompt.contains("steam vent", ignoreCase = true))
    }

    @Test
    fun officeOrganizerDoesNotInheritKitchenOrFishingStereotypes() {
        val analysis = JSONObject()
            .put("product_category", "office")
            .put("observed_use_case", "desk organizer")
            .put("observed_context", "desk")
        val plan = CreativeStrategyEngine.plan(analysis)
        assertEquals(CreativeStrategyEngine.SettingType.OFFICE, plan.settingType)
        assertEquals(CreativeStrategyEngine.Motivation.ORGANIZATION, plan.primary)
        assertEquals(CreativeStrategyEngine.SellingIdea.ORGANIZATION, plan.idea)
        assertFalse(plan.setting.contains("kitchen", ignoreCase = true))
        assertFalse(plan.setting.contains("lakeside", ignoreCase = true))
        assertFalse(plan.formatTone.contains("lived-in kitchen feeling"))
        val prompt = ProductLock.finalizeClean(
            "ACTION:\none hand rests near the organizer.",
            JSONObject().put("overall_geometry", "desktop organizer tray"),
            "VEO",
            "DEUTSCH",
            null,
            true,
            analysis,
        )
        assertTrue(prompt.contains("desk", ignoreCase = true) || prompt.contains("office", ignoreCase = true))
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertFalse(prompt.contains("circular upper vent", ignoreCase = true))
        assertFalse(prompt.contains("bait tray", ignoreCase = true))
        assertFalse(prompt.contains("hanging ring", ignoreCase = true))
        assertFalse(prompt.contains("lakeside", ignoreCase = true))
        assertFalse(prompt.contains("Mikrowelle"))
        assertFalse(prompt.substringAfter("LIGHTING:").contains("outdoor daylight"))
        assertTrue(prompt.contains("speaks naturally in casual German"))
        assertTrue(
            CreativeStrategyEngine.gateFailures(
                prompt,
                ProductLock.extractSpokenHooks(prompt).firstOrNull().orEmpty(),
                plan,
                "DEUTSCH",
                analysis,
            ).isEmpty(),
        )
    }

    @Test
    fun unknownProductKeepsNeutralFallbackNotAnInventedNiche() {
        val analysis = JSONObject()
            .put("product_category", "unknown gadget")
            .put("observed_use_case", "small household widget")
        val plan = CreativeStrategyEngine.plan(analysis)
        assertEquals(CreativeStrategyEngine.SettingType.INDOOR_NEUTRAL, plan.settingType)
        assertTrue(plan.settingConfidence < 0.5)
        assertTrue(
            plan.primary == CreativeStrategyEngine.Motivation.OTHER ||
                plan.primary == CreativeStrategyEngine.Motivation.SIMPLICITY ||
                plan.primary == CreativeStrategyEngine.Motivation.CONVENIENCE,
        )
        assertTrue(
            plan.hookType == CreativeStrategyEngine.HookType.CURIOSITY ||
                plan.hookType == CreativeStrategyEngine.HookType.CONVENIENCE ||
                plan.hookType == CreativeStrategyEngine.HookType.SIMPLICITY,
        )
        assertFalse(plan.setting.contains("Ordinary cozy home kitchen"))
        assertFalse(plan.formatTone.contains("Warm, homely, lived-in kitchen feeling"))
        val prompt = ProductLock.finalizeClean(
            "ACTION:\none hand lightly touches the referenced product.",
            JSONObject().put("overall_geometry", "small unknown household widget"),
            "VEO",
            "DEUTSCH",
            null,
            true,
            analysis,
        )
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertFalse(prompt.contains("lakeside"))
        assertFalse(prompt.contains("circular upper vent", ignoreCase = true))
        assertFalse(prompt.contains("bait tray", ignoreCase = true))
        assertFalse(prompt.contains("hanging ring", ignoreCase = true))
        assertFalse(prompt.contains("Mikrowelle"))
        assertTrue(prompt.contains("Neutral realistic indoor") || prompt.contains("matching the First Frame"))
    }

    @Test
    fun livingRoomChairDoesNotBecomeAFishingScene() {
        val analysis = JSONObject()
            .put("product_category", "furniture")
            .put("observed_use_case", "armchair")
            .put("observed_context", "living room")
        val fingerprint = JSONObject().put("overall_geometry", "upholstered armchair with armrests and stable legs")
        val plan = CreativeStrategyEngine.plan(analysis, fingerprint)
        assertEquals(CreativeStrategyEngine.SettingType.LIVING_ROOM, plan.settingType)
        assertEquals(CreativeStrategyEngine.Motivation.COMFORT, plan.primary)
        assertEquals(CreativeStrategyEngine.SellingIdea.COMFORT, plan.idea)
        assertEquals(CreativeStrategyEngine.HookType.COMFORT, plan.hookType)
        assertFalse(plan.primary == CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY)
        assertTrue(plan.action.contains("already seated"))
        assertFalse(plan.setting.contains("lakeside", ignoreCase = true))
        assertFalse(plan.setting.contains("kitchen", ignoreCase = true))
        assertFalse(plan.human.contains("tray"))
        val prompt = ProductLock.finalizeClean(
            "ACTION:\nfold the chair.",
            fingerprint,
            "VEO",
            "DEUTSCH",
            null,
            true,
            analysis,
        )
        assertTrue(prompt.contains("living room", ignoreCase = true))
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertFalse(prompt.contains("lakeside"))
        assertFalse(prompt.contains("bait tray", ignoreCase = true))
        assertFalse(prompt.contains("circular upper vent", ignoreCase = true))
        assertFalse(prompt.contains("hanging ring", ignoreCase = true))
        assertFalse(prompt.contains("рыбал"))
        assertTrue(prompt.substringAfter("LIGHTING:").contains("home daylight") || prompt.substringAfter("LIGHTING:").contains("indoor"))
    }

    @Test
    fun firstFrameGardenBeatsKitchenCategoryStereotype() {
        val analysis = JSONObject()
            .put("product_category", "kitchen")
            .put("observed_use_case", "frying pan")
            .put("observed_context", "white background packshot")
            .put("first_frame_context", "home garden table")
        val plan = CreativeStrategyEngine.plan(analysis, ProductIdentity.cookwarePanFingerprint(), "home garden table")
        assertEquals(CreativeStrategyEngine.SettingType.GARDEN, plan.settingType)
        assertFalse(plan.setting.contains("Ordinary cozy home kitchen"))
        assertTrue(plan.lighting.contains("outdoor daylight"))
        assertFalse(plan.speechContext.contains("kitchen"))
        val prompt = ProductLock.finalizeClean(
            "SETTING:\nOrdinary cozy home kitchen.\nACTION:\none hand touches the handle.",
            ProductIdentity.cookwarePanFingerprint(),
            "VEO",
            "DEUTSCH",
            null,
            true,
            analysis,
        )
        assertTrue(prompt.contains("garden", ignoreCase = true) || prompt.contains("yard", ignoreCase = true) || prompt.contains("outdoor"))
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertTrue(prompt.substringAfter("LIGHTING:").contains("outdoor daylight"))
        assertFalse(prompt.substringAfter("SPEECH:").contains("in their own kitchen"))
    }
}
