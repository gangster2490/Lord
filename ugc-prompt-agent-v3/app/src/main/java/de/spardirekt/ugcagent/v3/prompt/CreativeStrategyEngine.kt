package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

object CreativeStrategyEngine {
    enum class Motivation {
        PROBLEM_SOLVER, COMFORT, CONVENIENCE, HOME_COZY, VISUAL, DEMONSTRABLE_FUNCTION,
        PORTABLE, ORGANIZATION, CLEANLINESS, OUTDOOR_HOBBY, TIME_SAVING, SPACE_SAVING,
        DURABILITY_APPEAL, SIMPLICITY, PREMIUM_FEEL, GIFT_APPEAL, SAFETY_CONVENIENCE, OTHER,
    }
    enum class HookType {
        PROBLEM, COMFORT, CONVENIENCE, HOME, CURIOSITY, VISUAL, OUTDOOR,
        ORGANIZATION, SIMPLICITY, DAILY_FRUSTRATION, SATISFYING_USE, OWNERSHIP,
    }
    enum class SellingIdea {
        COMFORT, CONVENIENCE, CLEANLINESS, PORTABILITY, SIMPLE_USE, VISUAL_APPEAL,
        ORGANIZATION, OUTDOOR_PRACTICALITY, HOME_FEELING,
    }
    enum class SettingType {
        HOME_KITCHEN, BATHROOM, BEDROOM, LIVING_ROOM, OFFICE, WORKSHOP, GARAGE, CAR,
        GARDEN, CAMPSITE, FISHING_SPOT, BEACH, GYM, TRAVEL, RETAIL_NEUTRAL_TABLETOP,
        OUTDOOR_NEUTRAL, INDOOR_NEUTRAL, OTHER,
    }

    data class Angle(
        val idea: SellingIdea,
        val motivation: Motivation,
        val hookType: HookType,
        val pitch: String,
        val relevance: Double,
        val visual: Double,
        val purchase: Double,
        val claimSafety: Double,
        val motionSafety: Double,
        val identity: Double,
    ) {
        val total: Double
            get() = (relevance + visual + purchase + claimSafety + motionSafety + identity) / 6.0
    }

    data class Concept(
        val kind: String,
        val angle: Angle,
        val action: String,
        val setting: String,
        val human: String,
        val score: Double,
    )

    data class Plan(
        val primary: Motivation,
        val secondary: Motivation?,
        val idea: SellingIdea,
        val hookType: HookType,
        val setting: String,
        val human: String,
        val action: String,
        val lighting: String,
        val camera: String,
        val formatTone: String,
        val speechContext: String,
        val opening: String,
        val pitch: String,
        val confidence: Double,
        val conceptKind: String,
        val kitchenDefault: Boolean,
        val boughtFor: String = "",
        val viewerFeel: String = "",
        val desire: String = "",
        val settingType: SettingType = SettingType.INDOOR_NEUTRAL,
        val settingConfidence: Double = 0.4,
        val emotionalTone: String = "",
        val hookIntent: String = "",
        val speechIntent: String = "",
        val visualStyle: String = "",
        val actionRiskLabel: String = "LOW",
    ) {
        fun toPublicJson(): JSONObject = JSONObject()
            .put("primary_motivation", primary.name)
            .put("secondary_motivation", secondary?.name ?: "")
            .put("selling_idea", idea.name)
            .put("hook_type", hookType.name)
            .put("concept_kind", conceptKind)
            .put("confidence", confidence)
            .put("bought_for", boughtFor)
            .put("viewer_should_feel", viewerFeel)
            .put("desire", desire)
            .put("setting_type", settingType.name)
            .put("setting_confidence", settingConfidence)
            .put("emotional_tone", emotionalTone)
            .put("action_risk", actionRiskLabel)
    }

    fun plan(analysis: JSONObject?, fingerprint: JSONObject? = null, firstFrameContext: String? = null): Plan {
        val (cleanAnalysis, cleanFingerprint) = CrossProductGuard.clean(analysis, fingerprint)
        val draft = CreativeConsistencyEngine.build(cleanAnalysis, cleanFingerprint, firstFrameContext)
        return PurchaseAppealEngine.apply(draft, cleanAnalysis, cleanFingerprint)
    }

    fun looksLikeFishingChair(fingerprint: JSONObject?, analysis: JSONObject? = null): Boolean {
        val id = CrossProductGuard.productScopeText(fingerprint, analysis)
        val chair = listOf("chair", "stuhl", "кресл", "стул", "seat", "armchair").any { id.contains(it) }
        val fishing = listOf("fish", "рыбал", "bait", "angel", "fishing").any { id.contains(it) }
        return chair && fishing
    }

    fun settingConflicts(setting: String, plan: Plan): Boolean =
        CreativeConsistencyEngine.settingConflicts(setting, plan)

    fun actionConflicts(action: String, plan: Plan): Boolean {
        if (ActionIdentity.isUnsafeAction(instructedAction(action))) return true
        val lower = instructedAction(action).lowercase()
        if (plan.primary == Motivation.OUTDOOR_HOBBY || plan.settingType == SettingType.FISHING_SPOT) {
            if (listOf("fold", "unfold", "180", "recline", "leg height", "height adjustment").any { lower.contains(it) }) {
                return true
            }
        }
        return false
    }

    fun instructedAction(action: String): String =
        action.replace(Regex("(?i)do not[^.\\n]*"), " ").replace(Regex("\\s+"), " ").trim()

    fun ideaLabel(plan: Plan, russian: Boolean): String = when (plan.idea) {
        SellingIdea.COMFORT -> if (russian) "комфорт в реальном использовании" else "Komfort in der echten Nutzung"
        SellingIdea.CONVENIENCE -> if (russian) "удобство под рукой" else "alles Nötige in Reichweite"
        SellingIdea.CLEANLINESS -> if (russian) "меньше возни после использования" else "weniger Nacharbeit nach dem Nutzen"
        SellingIdea.PORTABILITY -> if (russian) "легко взять с собой" else "leicht dabeihaben"
        SellingIdea.SIMPLE_USE -> if (russian) "простое понятное действие" else "eine klare einfache Nutzung"
        SellingIdea.VISUAL_APPEAL -> if (russian) "как это выглядит вживую" else "wie es in echt wirkt"
        SellingIdea.ORGANIZATION -> if (russian) "порядок под рукой" else "Ordnung in Griffweite"
        SellingIdea.OUTDOOR_PRACTICALITY -> if (russian) "практичность на улице" else "praktisch draußen"
        SellingIdea.HOME_FEELING -> if (russian) "домашнее ощущение" else "ein homeliges Alltagsgefühl"
    }

    fun gateFailures(prompt: String, hook: String, plan: Plan, language: String, analysis: JSONObject?): List<String> {
        val failures = mutableListOf<String>()
        val timing = prompt.substringAfter("TIMING:", missingDelimiterValue = "").lowercase()
        val actionBody = instructedAction(PromptComposer.extractAction(prompt)).lowercase()
        if (hook.isNotBlank() && HookEngine.isWeak(hook, language, analysis, plan)) failures.add("hook_mismatch")
        if (settingConflicts(prompt, plan)) failures.add("setting_mismatch")
        if (actionConflicts(actionBody, plan) ||
            ((plan.primary == Motivation.OUTDOOR_HOBBY || plan.settingType == SettingType.FISHING_SPOT) &&
                (actionBody.contains("fold") || actionBody.contains("180")))
        ) {
            failures.add("high_risk_motion")
        }
        if (listOf("best", "perfect", "always", "never", "guaranteed").any { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(hook) }) {
            failures.add("unsafe_claim")
        }
        if (plan.hookType != HookType.VISUAL && timing.contains("neutral") && timing.contains("beauty")) {
            failures.add("wasted_opening")
        }
        val cleaned = prompt.replace(Regex("(?i)(no|do not|never)[^.\\n]*"), " ").lowercase()
        if (listOf("presenter pose", "pointing at features", "feature list", "montage", "showroom demonstration").any { cleaned.contains(it) }) {
            failures.add("technical_demo")
        }
        if (plan.desire.isBlank() && plan.pitch.isBlank()) failures.add("no_desire")
        if (hook.isNotBlank() && HookEngine.score(hook, language, analysis, plan).purchaseAppeal < 0.55) {
            failures.add("weak_purchase_appeal")
        }
        failures += CreativeConsistencyEngine.coherenceFailures(prompt, hook, plan)
        return failures.distinct()
    }

    fun hashtagExtras(plan: Plan, russian: Boolean): List<String> = when (plan.settingType) {
        SettingType.FISHING_SPOT -> if (russian) listOf("#tiktokshop", "#рыбалка", "#природа", "#ugc") else listOf("#tiktokshop", "#angeln", "#outdoor", "#ugc")
        SettingType.CAMPSITE, SettingType.OUTDOOR_NEUTRAL, SettingType.GARDEN, SettingType.BEACH ->
            if (russian) listOf("#tiktokshop", "#природа", "#ugc", "#обзор") else listOf("#tiktokshop", "#outdoor", "#ugc", "#alltag")
        SettingType.HOME_KITCHEN -> if (russian) listOf("#tiktokshop", "#обзор", "#кухня", "#ugc") else listOf("#tiktokshop", "#alltag", "#küche", "#ugc")
        SettingType.WORKSHOP, SettingType.GARAGE -> if (russian) listOf("#tiktokshop", "#гараж", "#ugc", "#обзор") else listOf("#tiktokshop", "#werkstatt", "#ugc", "#alltag")
        SettingType.OFFICE -> if (russian) listOf("#tiktokshop", "#офис", "#ugc", "#обзор") else listOf("#tiktokshop", "#büro", "#ugc", "#alltag")
        SettingType.TRAVEL -> if (russian) listOf("#tiktokshop", "#вдороге", "#ugc", "#обзор") else listOf("#tiktokshop", "#unterwegs", "#ugc", "#alltag")
        else -> if (russian) listOf("#tiktokshop", "#обзор", "#ugc", "#дом") else listOf("#tiktokshop", "#alltag", "#ugc", "#zuhause")
    }

    fun safeAction(
        fingerprint: JSONObject?,
        analysis: JSONObject? = null,
        idea: SellingIdea? = null,
        settingType: SettingType = SettingType.INDOOR_NEUTRAL,
        chosen: Angle? = null,
    ): String {
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) return ActionIdentity.MICROWAVE_SAFE_ACTION
        val id = CrossProductGuard.productScopeText(fingerprint, analysis)
        val seated = listOf("chair", "stuhl", "кресл", "стул", "seat", "armchair").any { id.contains(it) } &&
            !id.contains("car seat")
        if (seated) {
            val trayBit = if (settingType == SettingType.FISHING_SPOT) " or uses a clearly visible tray" else ""
            return "The person is already seated in the referenced chair. Relaxed posture. One LOW-RISK moment: a hand rests on the arm$trayBit. Do not fold, unfold, rotate a backrest, change leg height or reconstruct hidden geometry. The chair stays exact and stable."
        }
        if (ProductIdentity.looksLikeCookwarePan(fingerprint, analysis)) {
            return "One hand casually touches the wooden handle of the referenced pan. The lid stays static. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry."
        }
        return when (idea ?: chosen?.idea) {
            SellingIdea.VISUAL_APPEAL -> "The referenced product stays in place while the person shares one quiet natural glance. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry."
            SellingIdea.PORTABILITY -> "One hand rests on the referenced product as it already stands ready. Do not fold, unfold or reconstruct hidden geometry."
            else -> ActionIdentity.DEFAULT_SAFE_ACTION
        }
    }

    fun fishingChairFingerprint(): JSONObject = org.json.JSONObject()
        .put("overall_geometry", "outdoor fishing chair with armrests, stable legs and a side bait tray")
        .put(
            "identity_critical_components",
            org.json.JSONArray()
                .put("seat")
                .put("backrest")
                .put("armrests")
                .put("side bait tray")
                .put("legs")
                .put("visible frame joints"),
        )
        .put("confidence", 0.86)
}
