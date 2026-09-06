package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

object CreativeStrategyEngine {
    enum class Motivation { PROBLEM_SOLVER, COMFORT, HOME_COZY, DEMONSTRABLE_FUNCTION, PORTABLE, VISUAL, OUTDOOR_HOBBY }
    enum class HookType { PROBLEM, COMFORT, CONVENIENCE, HOME, CURIOSITY, VISUAL }
    enum class SellingIdea {
        COMFORT, CONVENIENCE, CLEANLINESS, PORTABILITY, SIMPLE_USE, VISUAL_APPEAL, ORGANIZATION, OUTDOOR_PRACTICALITY, HOME_FEELING
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
    ) {
        fun toPublicJson(): JSONObject = JSONObject()
            .put("primary_motivation", primary.name)
            .put("secondary_motivation", secondary?.name ?: "")
            .put("selling_idea", idea.name)
            .put("hook_type", hookType.name)
            .put("concept_kind", conceptKind)
            .put("confidence", confidence)
    }

    fun plan(analysis: JSONObject?, fingerprint: JSONObject? = null): Plan {
        val blob = blob(analysis, fingerprint)
        val primary = primaryMotivation(blob, fingerprint, analysis)
        val secondary = secondaryMotivation(primary, blob)
        val angles = scoreAngles(primary, secondary, blob, fingerprint, analysis).sortedByDescending { it.total }
        val chosen = angles.first()
        val setting = settingFor(primary, blob)
        val action = safeAction(fingerprint, analysis, chosen)
        val human = humanFor(primary, blob, fingerprint, analysis)
        val concepts = listOf(
            Concept("safest", chosen, action, setting, human, chosen.motionSafety * 0.5 + chosen.identity * 0.5),
            Concept("purchase", chosen, action, setting, human, chosen.purchase * 0.6 + chosen.visual * 0.4),
            Concept("natural", chosen, action, setting, human, 0.55 * chosen.relevance + 0.45 * chosen.claimSafety),
        )
        val winner = concepts.maxByOrNull { it.score } ?: concepts.first()
        val kitchen = isKitchenBlob(blob) || isMicrowaveBlob(blob) ||
            ProductIdentity.looksLikeMicrowaveCover(fingerprint) ||
            ProductIdentity.looksLikeCookwarePan(fingerprint, analysis)
        return Plan(
            primary = primary,
            secondary = secondary,
            idea = chosen.idea,
            hookType = chosen.hookType,
            setting = setting,
            human = human,
            action = action,
            lighting = lightingFor(primary),
            camera = cameraFor(kitchen, primary),
            formatTone = formatTone(kitchen, primary),
            speechContext = speechContext(primary, blob),
            opening = openingFor(chosen),
            pitch = chosen.pitch,
            confidence = chosen.total,
            conceptKind = winner.kind,
            kitchenDefault = kitchen,
        )
    }

    fun looksLikeFishingChair(fingerprint: JSONObject?, analysis: JSONObject? = null): Boolean {
        val blob = blob(analysis, fingerprint)
        val chair = listOf("chair", "stuhl", "кресл", "стул", "seat").any { blob.contains(it) }
        val fishing = listOf("fish", "рыбал", "angel", "bait", "озера", "lake", "ufer", "берег").any { blob.contains(it) }
        return chair && fishing
    }

    fun settingConflicts(setting: String, plan: Plan): Boolean {
        val lower = setting.lowercase()
        val kitchenish = lower.contains("kitchen") || lower.contains("küche") || lower.contains("кухн")
        return when (plan.primary) {
            Motivation.OUTDOOR_HOBBY, Motivation.PORTABLE -> kitchenish
            Motivation.DEMONSTRABLE_FUNCTION -> kitchenish && !plan.kitchenDefault
            else -> false
        }
    }

    fun actionConflicts(action: String, plan: Plan): Boolean {
        if (ActionIdentity.isUnsafeAction(action)) return true
        val lower = action.lowercase()
        if (plan.primary == Motivation.OUTDOOR_HOBBY) {
            if (listOf("fold", "unfold", "180", "recline", "leg height", "height adjustment", "backrest").any { lower.contains(it) }) {
                return true
            }
        }
        return false
    }

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
        val lower = prompt.lowercase()
        if (hook.isNotBlank() && HookEngine.isWeak(hook, language, analysis, plan)) failures.add("hook_mismatch")
        if (settingConflicts(prompt, plan)) failures.add("setting_mismatch")
        if (plan.primary == Motivation.OUTDOOR_HOBBY && (lower.contains("fold the chair") || lower.contains("180-degree"))) {
            failures.add("high_risk_motion")
        }
        if (listOf("best", "perfect", "always", "never", "guaranteed").any { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(hook) }) {
            failures.add("unsafe_claim")
        }
        return failures
    }

    fun hashtagExtras(plan: Plan, russian: Boolean): List<String> = when (plan.primary) {
        Motivation.OUTDOOR_HOBBY -> if (russian) listOf("#tiktokshop", "#рыбалка", "#природа", "#ugc") else listOf("#tiktokshop", "#angeln", "#outdoor", "#ugc")
        Motivation.PORTABLE -> if (russian) listOf("#tiktokshop", "#вдороге", "#ugc", "#обзор") else listOf("#tiktokshop", "#unterwegs", "#ugc", "#alltag")
        else -> if (russian) listOf("#tiktokshop", "#обзор", "#кухня", "#ugc") else listOf("#tiktokshop", "#alltag", "#küche", "#ugc")
    }

    private fun primaryMotivation(blob: String, fingerprint: JSONObject?, analysis: JSONObject?): Motivation {
        if (looksLikeFishingChair(fingerprint, analysis) || isOutdoorHobby(blob)) return Motivation.OUTDOOR_HOBBY
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint) || isCleaningOrCover(blob)) return Motivation.PROBLEM_SOLVER
        if (ProductIdentity.looksLikeCookwarePan(fingerprint, analysis)) return Motivation.HOME_COZY
        if (isDemonstrable(blob)) return Motivation.DEMONSTRABLE_FUNCTION
        if (isPortable(blob)) return Motivation.PORTABLE
        if (isVisual(blob)) return Motivation.VISUAL
        if (isTool(blob)) return Motivation.PROBLEM_SOLVER
        if (isComfort(blob)) return Motivation.COMFORT
        if (isKitchenBlob(blob)) return Motivation.HOME_COZY
        return Motivation.HOME_COZY
    }

    private fun secondaryMotivation(primary: Motivation, blob: String): Motivation? = when (primary) {
        Motivation.OUTDOOR_HOBBY -> if (isPortable(blob)) Motivation.PORTABLE else Motivation.COMFORT
        Motivation.PROBLEM_SOLVER -> if (isKitchenBlob(blob)) Motivation.HOME_COZY else null
        Motivation.HOME_COZY -> Motivation.DEMONSTRABLE_FUNCTION.takeIf { isDemonstrable(blob) }
        Motivation.PORTABLE -> Motivation.COMFORT
        Motivation.VISUAL -> Motivation.HOME_COZY
        Motivation.DEMONSTRABLE_FUNCTION -> Motivation.HOME_COZY.takeIf { isKitchenBlob(blob) }
        Motivation.COMFORT -> Motivation.OUTDOOR_HOBBY.takeIf { isOutdoorHobby(blob) }
    }

    private fun scoreAngles(
        primary: Motivation,
        secondary: Motivation?,
        blob: String,
        fingerprint: JSONObject?,
        analysis: JSONObject?,
    ): List<Angle> {
        val candidates = when (primary) {
            Motivation.OUTDOOR_HOBBY -> listOf(
                angle(SellingIdea.COMFORT, Motivation.COMFORT, HookType.COMFORT, "comfort during a long outdoor sit", 0.96, 0.9, 0.92, 0.95, 0.94, 0.96),
                angle(SellingIdea.CONVENIENCE, Motivation.COMFORT, HookType.CONVENIENCE, "needed bits stay in reach", 0.88, 0.86, 0.9, 0.94, 0.9, 0.93),
                angle(SellingIdea.OUTDOOR_PRACTICALITY, Motivation.OUTDOOR_HOBBY, HookType.CURIOSITY, "stable outdoor seating", 0.84, 0.7, 0.8, 0.9, 0.35, 0.7),
            )
            Motivation.PROBLEM_SOLVER -> listOf(
                angle(SellingIdea.CLEANLINESS, Motivation.PROBLEM_SOLVER, HookType.PROBLEM, "pain then simple relief", 0.95, 0.88, 0.93, 0.92, 0.9, 0.95),
                angle(SellingIdea.SIMPLE_USE, Motivation.PROBLEM_SOLVER, HookType.CONVENIENCE, "one easy protective use", 0.86, 0.84, 0.82, 0.94, 0.92, 0.95),
                angle(SellingIdea.HOME_FEELING, Motivation.HOME_COZY, HookType.HOME, "pleasant everyday kitchen use", 0.7, 0.72, 0.7, 0.94, 0.93, 0.95),
            )
            Motivation.HOME_COZY -> listOf(
                angle(SellingIdea.HOME_FEELING, Motivation.HOME_COZY, HookType.HOME, "warm daily home use", 0.94, 0.86, 0.88, 0.96, 0.93, 0.95),
                angle(SellingIdea.SIMPLE_USE, Motivation.HOME_COZY, HookType.CONVENIENCE, "a casual kitchen touch", 0.82, 0.84, 0.8, 0.95, 0.94, 0.95),
                angle(SellingIdea.VISUAL_APPEAL, Motivation.VISUAL, HookType.VISUAL, "how it sits in a real kitchen", 0.74, 0.9, 0.78, 0.95, 0.96, 0.95),
            )
            Motivation.DEMONSTRABLE_FUNCTION -> listOf(
                angle(SellingIdea.SIMPLE_USE, Motivation.DEMONSTRABLE_FUNCTION, HookType.CURIOSITY, "one clear useful function", 0.93, 0.9, 0.9, 0.9, 0.7, 0.9),
                angle(SellingIdea.CONVENIENCE, Motivation.COMFORT, HookType.CONVENIENCE, "the useful moment stays simple", 0.84, 0.8, 0.84, 0.93, 0.88, 0.93),
                angle(SellingIdea.HOME_FEELING, Motivation.HOME_COZY, HookType.HOME, "everyday useful object", 0.7, 0.68, 0.7, 0.94, 0.9, 0.93),
            )
            Motivation.PORTABLE -> listOf(
                angle(SellingIdea.PORTABILITY, Motivation.PORTABLE, HookType.CONVENIENCE, "easy to keep around and carry", 0.94, 0.8, 0.9, 0.94, 0.7, 0.9),
                angle(SellingIdea.CONVENIENCE, Motivation.COMFORT, HookType.CONVENIENCE, "practical to have nearby", 0.86, 0.78, 0.84, 0.95, 0.88, 0.93),
                angle(SellingIdea.COMFORT, Motivation.COMFORT, HookType.COMFORT, "comfort without fuss", 0.78, 0.76, 0.8, 0.94, 0.9, 0.93),
            )
            Motivation.VISUAL -> listOf(
                angle(SellingIdea.VISUAL_APPEAL, Motivation.VISUAL, HookType.VISUAL, "look and atmosphere", 0.95, 0.96, 0.9, 0.95, 0.96, 0.95),
                angle(SellingIdea.HOME_FEELING, Motivation.HOME_COZY, HookType.HOME, "how it lives in the room", 0.82, 0.86, 0.8, 0.96, 0.96, 0.95),
                angle(SellingIdea.SIMPLE_USE, Motivation.HOME_COZY, HookType.CURIOSITY, "a quiet real-life glance", 0.7, 0.74, 0.7, 0.95, 0.96, 0.95),
            )
            Motivation.COMFORT -> listOf(
                angle(SellingIdea.COMFORT, Motivation.COMFORT, HookType.COMFORT, "the experience feels easier", 0.95, 0.88, 0.92, 0.94, 0.9, 0.94),
                angle(SellingIdea.CONVENIENCE, Motivation.COMFORT, HookType.CONVENIENCE, "less fuss in the moment", 0.86, 0.82, 0.86, 0.95, 0.9, 0.94),
                angle(SellingIdea.OUTDOOR_PRACTICALITY, Motivation.OUTDOOR_HOBBY, HookType.COMFORT, "comfort in the real setting", 0.8, 0.8, 0.82, 0.93, 0.86, 0.93),
            )
        }
        return candidates.map { angle ->
            if (angle.pitch.contains("stable outdoor seating") && looksLikeFishingChair(fingerprint, analysis)) {
                angle.copy(motionSafety = 0.3, identity = 0.65)
            } else angle
        }
    }

    private fun angle(
        idea: SellingIdea,
        motivation: Motivation,
        hookType: HookType,
        pitch: String,
        relevance: Double,
        visual: Double,
        purchase: Double,
        claimSafety: Double,
        motionSafety: Double,
        identity: Double,
    ) = Angle(idea, motivation, hookType, pitch, relevance, visual, purchase, claimSafety, motionSafety, identity)

    fun safeAction(fingerprint: JSONObject?, analysis: JSONObject? = null, chosen: Angle? = null): String {
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) return ActionIdentity.MICROWAVE_SAFE_ACTION
        if (looksLikeFishingChair(fingerprint, analysis)) {
            return "The person is already seated in the referenced fishing chair. Relaxed posture. One LOW-RISK moment: a hand rests on the arm or uses a clearly visible tray. Do not fold, unfold, rotate a backrest, change leg height or reconstruct hidden geometry. The chair stays exact and stable."
        }
        if (ProductIdentity.looksLikeCookwarePan(fingerprint, analysis)) {
            return "One hand casually touches the wooden handle of the referenced pan. The lid stays static. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry."
        }
        return when (chosen?.idea) {
            SellingIdea.VISUAL_APPEAL -> "The referenced product stays in place while the person shares one quiet natural glance. Do not fold, open, rotate, pull, detach or reconstruct hidden geometry."
            SellingIdea.PORTABILITY -> "One hand rests on the referenced product as it already stands ready. Do not fold, unfold or reconstruct hidden geometry."
            else -> ActionIdentity.DEFAULT_SAFE_ACTION
        }
    }

    private fun settingFor(primary: Motivation, blob: String): String = when {
        looksLikeFishing(blob) -> "Real lakeside or riverside fishing spot. Ordinary outdoor daylight. Lived-in hobby scene, not a studio, showroom or commercial set."
        isCamping(blob) -> "Simple outdoor camp area. Natural daylight. Real use, not a studio or showroom."
        primary == Motivation.OUTDOOR_HOBBY -> "Real outdoor hobby spot. Ordinary daylight. Not a studio, showroom or commercial set."
        isTool(blob) && !isKitchenBlob(blob) -> "Ordinary home garage, workshop or repair corner. Lived-in, slightly imperfect. Not a studio or showroom."
        isCleaning(blob) && !isKitchenBlob(blob) -> "Real home bathroom or kitchen cleaning context. Ordinary indoor light. Not a studio or showroom."
        isMicrowaveBlob(blob) || isKitchenBlob(blob) ->
            "Ordinary cozy home kitchen. Warm, lived-in, slightly imperfect. Not a studio, showroom or commercial set."
        else -> "Ordinary lived-in home interior. Natural available light. Not a studio, showroom or commercial set."
    }

    private fun humanFor(primary: Motivation, blob: String, fingerprint: JSONObject?, analysis: JSONObject?): String {
        val base = ProductLock.HUMAN_LOCK
        val extra = when {
            looksLikeFishingChair(fingerprint, analysis) ->
                "Person already seated comfortably. Relaxed posture. Brief natural use of a visible tray or a light rest on the arm/seat. No folding or complex adjustment."
            ProductIdentity.looksLikeMicrowaveCover(fingerprint) ->
                "Cover already over a plate. One hand lightly touches the handle. Microwave context stays visible in the background."
            ProductIdentity.looksLikeCookwarePan(fingerprint, analysis) ->
                "Casual touch of the wooden handle. Lid remains static. Relaxed kitchen moment."
            primary == Motivation.VISUAL -> "Quiet natural presence. No pointing at features."
            else -> "Relaxed real-use posture."
        }
        return "$base $extra No presenter poses, pointing at features, fake influencer gestures, excessive smiling or theatrical reactions."
    }

    private fun lightingFor(primary: Motivation): String = when (primary) {
        Motivation.OUTDOOR_HOBBY, Motivation.PORTABLE ->
            "Ordinary outdoor daylight. Natural reflections. Slightly imperfect real UGC. No studio-commercial lighting, glossy advertising look, fake glow, or showroom perfection."
        else -> ProductLock.LIGHTING_LOCK
    }

    private fun cameraFor(kitchen: Boolean, primary: Motivation): String {
        val tail = if (kitchen) " Warm homely handheld UGC, not a polished commercial move."
        else " Natural handheld UGC in the real use setting, not a polished commercial move."
        return ProductLock.CAMERA_LOCK + tail
    }

    private fun formatTone(kitchen: Boolean, primary: Motivation): String = when {
        kitchen -> "Warm, homely, lived-in kitchen feeling. Not a showroom."
        primary == Motivation.OUTDOOR_HOBBY -> "Natural outdoor hobby UGC. Real use, not a showroom."
        else -> "Natural lived-in UGC. Not a showroom."
    }

    private fun speechContext(primary: Motivation, blob: String): String = when {
        looksLikeFishing(blob) -> "at a real fishing spot"
        isCamping(blob) -> "at a simple camp"
        isTool(blob) && !isKitchenBlob(blob) -> "in a home workshop corner"
        isKitchenBlob(blob) || primary == Motivation.HOME_COZY || primary == Motivation.PROBLEM_SOLVER -> "in their own kitchen"
        primary == Motivation.OUTDOOR_HOBBY -> "outdoors in a real use moment"
        else -> "at home in a real moment"
    }

    private fun openingFor(angle: Angle): String = when (angle.hookType) {
        HookType.PROBLEM -> "hook and immediate problem context so the relief idea is already clear"
        HookType.COMFORT -> "hook and immediate comfort context so the viewer already feels why it helps"
        HookType.CONVENIENCE -> "hook and immediate convenience context so usefulness is already clear"
        HookType.VISUAL -> "hook and immediate visual presence so desirability is already clear"
        HookType.CURIOSITY -> "hook and immediate useful detail so the idea is already clear"
        HookType.HOME -> "hook and immediate home context so the viewer already grasps the everyday appeal"
    }

    private fun blob(analysis: JSONObject?, fingerprint: JSONObject?): String {
        return listOf(
            analysis?.toString().orEmpty(),
            fingerprint?.toString().orEmpty(),
            analysis?.optString("product_category").orEmpty(),
            analysis?.optString("observed_use_case").orEmpty(),
            analysis?.optString("observed_context").orEmpty(),
        ).joinToString(" ").lowercase()
    }

    private fun isMicrowaveBlob(blob: String): Boolean =
        blob.contains("microwave") ||
            blob.contains("микроволн") ||
            (blob.contains("dome") && blob.contains("rectangular") && blob.contains("vent") && blob.contains("green"))

    private fun isKitchenBlob(blob: String): Boolean =
        listOf("kitchen", "кухн", "microwave", "микроволн", "pan", "сковород", "кастрюл", "cookware", "waffle", "grill").any { blob.contains(it) }

    private fun isOutdoorHobby(blob: String): Boolean =
        listOf("fish", "рыбал", "angel", "camp", "кемпинг", "outdoor", "озера", "lake", "ufer", "берег", "hiking").any { blob.contains(it) }

    private fun looksLikeFishing(blob: String): Boolean =
        listOf("fish", "рыбал", "angel", "bait", "озера", "lake").any { blob.contains(it) }

    private fun isCamping(blob: String): Boolean = listOf("camp", "кемпинг", "tent", "палатк").any { blob.contains(it) }

    private fun isCleaningOrCover(blob: String): Boolean =
        listOf("cover", "крыш", "splash", "брызг", "clean", "убор", "organizer", "хранен").any { blob.contains(it) }

    private fun isCleaning(blob: String): Boolean = listOf("clean", "убор", "bath", "ванн").any { blob.contains(it) }

    private fun isDemonstrable(blob: String): Boolean =
        listOf("waffle", "grill", "processor", "pump", "blender", "вафельн", "гриль").any { blob.contains(it) }

    private fun isPortable(blob: String): Boolean =
        listOf("folding", "fold", "portable", "travel", "компакт", "складн", "походн").any { blob.contains(it) } &&
            !ProductIdentity.looksLikeMicrowaveCover(null)

    private fun isVisual(blob: String): Boolean =
        listOf("lamp", "ламп", "decor", "декор", "glassware", "бокал", "fashion", "светильн").any { blob.contains(it) }

    private fun isTool(blob: String): Boolean =
        listOf("tool", "инструмент", "repair", "garage", "workshop", "гараж").any { blob.contains(it) }

    private fun isComfort(blob: String): Boolean =
        listOf("cushion", "подуш", "comfort", "комфорт", "chair", "кресл", "seat").any { blob.contains(it) }

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
