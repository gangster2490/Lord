package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

/**
 * Scores candidate selling angles from evidence, then picks ONE safe idea.
 * Does not first-match keywords and does not remap the winner to a second desire.
 */
object SellingAngleSelector {
    data class ScoredAngle(
        val motivation: CreativeStrategyEngine.Motivation,
        val idea: CreativeStrategyEngine.SellingIdea,
        val hookType: CreativeStrategyEngine.HookType,
        val relevance: Double,
        val purchase: Double,
        val visual: Double,
        val claimSafety: Double,
        val motionSafety: Double,
        val identity: Double,
        val settingCoherence: Double,
        val naturalness: Double,
    ) {
        val total: Double
            get() = 0.20 * purchase + 0.16 * relevance + 0.14 * settingCoherence +
                0.12 * visual + 0.12 * claimSafety + 0.10 * motionSafety +
                0.10 * identity + 0.06 * naturalness
        val safe: Boolean
            get() = motionSafety >= 0.75 && identity >= 0.85 && claimSafety >= 0.85 && settingCoherence >= 0.55
    }

    fun ranked(
        analysis: JSONObject?,
        fingerprint: JSONObject?,
        setting: CreativeConsistencyEngine.SettingEvidence,
        blob: String = CreativeConsistencyEngine.blob(analysis, fingerprint),
    ): List<ScoredAngle> {
        val signals = signals(blob, fingerprint, analysis)
        return candidates(signals, setting)
            .sortedByDescending { it.total }
    }

    fun winner(
        analysis: JSONObject?,
        fingerprint: JSONObject?,
        setting: CreativeConsistencyEngine.SettingEvidence,
        blob: String = CreativeConsistencyEngine.blob(analysis, fingerprint),
    ): ScoredAngle {
        val scored = ranked(analysis, fingerprint, setting, blob)
        val safe = scored.filter { it.safe }
        val pool = if (safe.isNotEmpty()) safe else scored
        return pool.maxByOrNull { it.total } ?: fallback(setting)
    }

    fun secondary(
        winner: ScoredAngle,
        analysis: JSONObject?,
        fingerprint: JSONObject?,
        setting: CreativeConsistencyEngine.SettingEvidence,
        blob: String = CreativeConsistencyEngine.blob(analysis, fingerprint),
    ): CreativeStrategyEngine.Motivation? {
        val next = ranked(analysis, fingerprint, setting, blob)
            .firstOrNull { it.motivation != winner.motivation && it.safe && it.relevance >= 0.55 }
            ?: return when (winner.motivation) {
                CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY -> CreativeStrategyEngine.Motivation.COMFORT
                else -> null
            }
        return next.motivation.takeIf { it != winner.motivation }
    }

    private data class Signals(
        val pain: Double,
        val outdoorHobby: Double,
        val comfort: Double,
        val organize: Double,
        val portable: Double,
        val visual: Double,
        val function: Double,
        val homeCook: Double,
        val time: Double,
        val space: Double,
        val gift: Double,
        val premium: Double,
        val cleanliness: Double,
        val microwave: Boolean,
        val seated: Boolean,
        val fishing: Boolean,
        val cookware: Boolean,
    )

    private fun signals(blob: String, fingerprint: JSONObject?, analysis: JSONObject?): Signals {
        val scope = CrossProductGuard.productScopeText(fingerprint, analysis)
        val microwave = ProductIdentity.looksLikeMicrowaveCover(fingerprint) ||
            listOf("microwave cover", "cover food", "микроволн", "mikrowelle").any { scope.contains(it) }
        val cookware = ProductIdentity.looksLikeCookwarePan(fingerprint, analysis)
        val fishing = CreativeStrategyEngine.looksLikeFishingChair(fingerprint, analysis)
        val seated = listOf("chair", "stuhl", "кресл", "стул", "seat", "armchair").any { scope.contains(it) }
        val pain = strength(
            blob,
            listOf("mess", "splash", "брызг", "убор", "cover food", "leak", "clutter", "возн", "putzen", "spatter", "nacharbeit"),
        ).let { base ->
            when {
                microwave -> maxOf(base, 0.92)
                blob.contains("clean") && (blob.contains("mess") || blob.contains("splash") || microwave) -> maxOf(base, 0.8)
                else -> base
            }
        }
        val outdoorHobby = if (fishing) {
            maxOf(
                0.9,
                strength(blob, listOf("fish", "рыбал", "angeln", "camp", "кемпинг", "hiking", "bait", "lakeside", "riverside")),
            )
        } else {
            strength(blob, listOf("camp", "кемпинг", "hiking", "outdoor", "draußen"))
        }
        val comfort = strength(blob, listOf("cushion", "подуш", "comfort", "комфорт", "armchair", "bequem", "удобн сид"))
            .let { base ->
                val chairHit = seated && !blob.contains("car seat")
                if (chairHit) maxOf(base, 0.72) else base
            }
        val organize = strength(blob, listOf("organizer", "storage", "хранен", "drawer", "shelf", "порядок", "ordnung"))
        val portable = strength(blob, listOf("portable", "travel", "компакт", "складн", "походн", "suitcase", "folding"))
            .let { base ->
                if (microwave || (seated && fishing)) 0.0 else base
            }
        val visual = strength(blob, listOf("lamp", "ламп", "decor", "декор", "glassware", "бокал", "fashion", "светильн", "aesthetic"))
        val function = strength(blob, listOf("waffle", "grill", "processor", "pump", "blender", "вафельн", "гриль"))
        val homeCook = strength(blob, listOf("cookware", "frying pan", "сковород", "кастрюл", "serving", "посуд", "pfanne"))
            .let { if (cookware) maxOf(it, 0.8) else it }
        val time = strength(blob, listOf("faster", "quick", "time-sav", "экономит время"))
        val space = strength(blob, listOf("space-sav", "compact storage", "flat pack"))
        val gift = strength(blob, listOf("gift", "подарок"))
        val premium = strength(blob, listOf("premium", "elegant", "luxury"))
        val cleanliness = strength(blob, listOf("mess", "splash", "брызг", "убор", "cover food", "cleanliness"))
            .let { if (microwave) maxOf(it, 0.9) else it }
        return Signals(
            pain, outdoorHobby, comfort, organize, portable, visual, function, homeCook,
            time, space, gift, premium, cleanliness, microwave, seated, fishing, cookware,
        )
    }

    private fun candidates(
        signals: Signals,
        setting: CreativeConsistencyEngine.SettingEvidence,
    ): List<ScoredAngle> {
        val outdoor = setting.type.isOutdoor()
        val kitchen = setting.type == CreativeStrategyEngine.SettingType.HOME_KITCHEN
        val office = setting.type == CreativeStrategyEngine.SettingType.OFFICE
        val list = mutableListOf<ScoredAngle>()

        if (signals.pain >= 0.45 || signals.microwave) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.PROBLEM_SOLVER,
                idea = if (signals.cleanliness >= 0.5 || signals.microwave) {
                    CreativeStrategyEngine.SellingIdea.CLEANLINESS
                } else CreativeStrategyEngine.SellingIdea.SIMPLE_USE,
                hook = CreativeStrategyEngine.HookType.PROBLEM,
                relevance = maxOf(signals.pain, if (signals.microwave) 0.92 else 0.0),
                purchase = 0.96,
                visual = 0.9,
                settingCoherence = when {
                    signals.microwave && kitchen -> 1.0
                    kitchen -> 0.95
                    signals.microwave -> 0.8
                    else -> 0.62
                },
                naturalness = 0.9,
                motion = if (signals.microwave) 0.92 else 0.94,
            )
        }
        if (signals.outdoorHobby >= 0.45) {
            val idea = if (signals.seated || signals.comfort >= 0.5) {
                CreativeStrategyEngine.SellingIdea.COMFORT
            } else CreativeStrategyEngine.SellingIdea.OUTDOOR_PRACTICALITY
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY,
                idea = idea,
                hook = CreativeStrategyEngine.HookType.OUTDOOR,
                relevance = signals.outdoorHobby,
                purchase = 0.93,
                visual = 0.92,
                settingCoherence = if (outdoor) 1.0 else 0.18,
                naturalness = if (outdoor) 0.97 else 0.4,
                motion = if (signals.seated) 0.98 else 0.9,
            )
        }
        if (signals.comfort >= 0.45) {
            val outdoorComfort = outdoor && signals.fishing
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.COMFORT,
                idea = CreativeStrategyEngine.SellingIdea.COMFORT,
                hook = if (outdoorComfort) CreativeStrategyEngine.HookType.OUTDOOR else CreativeStrategyEngine.HookType.COMFORT,
                relevance = if (signals.fishing && outdoor) signals.comfort * 0.72 else signals.comfort,
                purchase = 0.9,
                visual = 0.88,
                settingCoherence = when {
                    setting.type == CreativeStrategyEngine.SettingType.LIVING_ROOM ||
                        setting.type == CreativeStrategyEngine.SettingType.BEDROOM -> 1.0
                    outdoorComfort -> 0.7
                    outdoor -> 0.55
                    else -> 0.9
                },
                naturalness = 0.94,
                motion = if (signals.seated) 0.98 else 0.92,
            )
        }
        if (signals.organize >= 0.45) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.ORGANIZATION,
                idea = CreativeStrategyEngine.SellingIdea.ORGANIZATION,
                hook = CreativeStrategyEngine.HookType.ORGANIZATION,
                relevance = signals.organize,
                purchase = 0.9,
                visual = 0.86,
                settingCoherence = when {
                    office -> 1.0
                    outdoor -> 0.35
                    kitchen -> 0.55
                    else -> 0.85
                },
                naturalness = 0.9,
            )
        }
        if (signals.homeCook >= 0.45 || (kitchen && (signals.cookware || signals.microwave))) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.HOME_COZY,
                idea = CreativeStrategyEngine.SellingIdea.HOME_FEELING,
                hook = CreativeStrategyEngine.HookType.HOME,
                relevance = if (signals.microwave) 0.5 else maxOf(signals.homeCook, if (kitchen) 0.7 else 0.0),
                purchase = if (signals.microwave) 0.7 else 0.9,
                visual = 0.88,
                settingCoherence = when {
                    kitchen && !signals.microwave -> 1.0
                    kitchen && signals.microwave -> 0.62
                    outdoor -> 0.2
                    else -> 0.35
                },
                naturalness = if (kitchen && !signals.microwave) 0.96 else 0.55,
            )
        }
        if (signals.portable >= 0.55 && !signals.microwave && !(signals.seated && signals.fishing)) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.PORTABLE,
                idea = CreativeStrategyEngine.SellingIdea.PORTABILITY,
                hook = CreativeStrategyEngine.HookType.CONVENIENCE,
                relevance = signals.portable,
                purchase = 0.86,
                visual = 0.8,
                settingCoherence = when {
                    setting.type == CreativeStrategyEngine.SettingType.TRAVEL ||
                        setting.type == CreativeStrategyEngine.SettingType.CAMPSITE -> 1.0
                    signals.seated -> 0.4
                    else -> 0.7
                },
                naturalness = 0.82,
                motion = if (signals.seated) 0.45 else 0.9,
                identity = if (signals.seated) 0.55 else 0.94,
                claim = 0.9,
            )
        }
        if (signals.visual >= 0.5) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.VISUAL,
                idea = CreativeStrategyEngine.SellingIdea.VISUAL_APPEAL,
                hook = CreativeStrategyEngine.HookType.VISUAL,
                relevance = signals.visual,
                purchase = 0.84,
                visual = 0.96,
                settingCoherence = if (outdoor && signals.fishing) 0.3 else 0.85,
                naturalness = 0.86,
            )
        }
        if (signals.function >= 0.5) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.DEMONSTRABLE_FUNCTION,
                idea = CreativeStrategyEngine.SellingIdea.SIMPLE_USE,
                hook = CreativeStrategyEngine.HookType.SATISFYING_USE,
                relevance = signals.function,
                purchase = 0.82,
                visual = 0.8,
                settingCoherence = if (setting.type == CreativeStrategyEngine.SettingType.WORKSHOP ||
                    setting.type == CreativeStrategyEngine.SettingType.GARAGE || kitchen
                ) 0.9 else 0.7,
                naturalness = 0.8,
            )
        }
        if (office && signals.organize < 0.45) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.CONVENIENCE,
                idea = CreativeStrategyEngine.SellingIdea.CONVENIENCE,
                hook = CreativeStrategyEngine.HookType.CONVENIENCE,
                relevance = 0.7,
                purchase = 0.84,
                visual = 0.82,
                settingCoherence = 1.0,
                naturalness = 0.9,
            )
        }
        if (signals.time >= 0.55) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.TIME_SAVING,
                idea = CreativeStrategyEngine.SellingIdea.CONVENIENCE,
                hook = CreativeStrategyEngine.HookType.CONVENIENCE,
                relevance = signals.time,
                purchase = 0.86,
                visual = 0.78,
                settingCoherence = 0.8,
                naturalness = 0.82,
            )
        }
        if (signals.space >= 0.55) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.SPACE_SAVING,
                idea = CreativeStrategyEngine.SellingIdea.PORTABILITY,
                hook = CreativeStrategyEngine.HookType.CONVENIENCE,
                relevance = signals.space,
                purchase = 0.8,
                visual = 0.76,
                settingCoherence = 0.75,
                naturalness = 0.8,
            )
        }
        if (signals.gift >= 0.55) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.GIFT_APPEAL,
                idea = CreativeStrategyEngine.SellingIdea.VISUAL_APPEAL,
                hook = CreativeStrategyEngine.HookType.OWNERSHIP,
                relevance = signals.gift,
                purchase = 0.78,
                visual = 0.88,
                settingCoherence = 0.7,
                naturalness = 0.75,
            )
        }
        if (signals.premium >= 0.55) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.PREMIUM_FEEL,
                idea = CreativeStrategyEngine.SellingIdea.VISUAL_APPEAL,
                hook = CreativeStrategyEngine.HookType.VISUAL,
                relevance = signals.premium,
                purchase = 0.76,
                visual = 0.9,
                settingCoherence = 0.7,
                naturalness = 0.74,
                claim = 0.8,
            )
        }
        if (setting.type == CreativeStrategyEngine.SettingType.WORKSHOP ||
            setting.type == CreativeStrategyEngine.SettingType.GARAGE
        ) {
            list += angle(
                motivation = CreativeStrategyEngine.Motivation.DEMONSTRABLE_FUNCTION,
                idea = CreativeStrategyEngine.SellingIdea.SIMPLE_USE,
                hook = CreativeStrategyEngine.HookType.SATISFYING_USE,
                relevance = 0.6,
                purchase = 0.8,
                visual = 0.82,
                settingCoherence = 1.0,
                naturalness = 0.88,
            )
        }
        list += fallback(setting).copy(
            relevance = if (setting.confidence < 0.5) 0.55 else 0.35,
            purchase = 0.62,
            settingCoherence = 0.9,
            naturalness = 0.8,
        )
        return list.distinctBy { it.motivation to it.idea }
    }

    private fun fallback(setting: CreativeConsistencyEngine.SettingEvidence): ScoredAngle {
        val outdoor = setting.type.isOutdoor()
        val low = setting.confidence < 0.5
        return angle(
            motivation = if (low) CreativeStrategyEngine.Motivation.OTHER else CreativeStrategyEngine.Motivation.SIMPLICITY,
            idea = CreativeStrategyEngine.SellingIdea.SIMPLE_USE,
            hook = if (low) CreativeStrategyEngine.HookType.CURIOSITY else CreativeStrategyEngine.HookType.SIMPLICITY,
            relevance = 0.4,
            purchase = 0.6,
            visual = 0.75,
            settingCoherence = 0.9,
            naturalness = 0.8,
            motion = 0.98,
        ).let { if (outdoor) it.copy(hookType = CreativeStrategyEngine.HookType.CURIOSITY) else it }
    }

    private fun angle(
        motivation: CreativeStrategyEngine.Motivation,
        idea: CreativeStrategyEngine.SellingIdea,
        hook: CreativeStrategyEngine.HookType,
        relevance: Double,
        purchase: Double,
        visual: Double,
        settingCoherence: Double,
        naturalness: Double,
        motion: Double = 0.96,
        identity: Double = 0.96,
        claim: Double = 0.95,
    ): ScoredAngle = ScoredAngle(
        motivation = motivation,
        idea = idea,
        hookType = hook,
        relevance = relevance,
        purchase = purchase,
        visual = visual,
        claimSafety = claim,
        motionSafety = motion,
        identity = identity,
        settingCoherence = settingCoherence,
        naturalness = naturalness,
    )

    private fun strength(blob: String, tokens: List<String>): Double {
        val hits = tokens.count { blob.contains(it) }
        return when {
            hits <= 0 -> 0.0
            hits == 1 -> 0.58
            hits == 2 -> 0.78
            else -> 0.92
        }
    }
}
