package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

/**
 * Builds one canonical creative concept from evidence, then every prompt section
 * is derived from that object. Context comes from photos and listing evidence,
 * not from product-class stereotypes.
 */
object CreativeConsistencyEngine {
    data class SettingEvidence(
        val type: CreativeStrategyEngine.SettingType,
        val confidence: Double,
        val source: String,
        val text: String,
    )

    fun build(
        analysis: JSONObject?,
        fingerprint: JSONObject? = null,
        firstFrameContext: String? = null,
    ): CreativeStrategyEngine.Plan {
        val cleanAnalysis = CrossProductGuard.cleanAnalysis(analysis, fingerprint)
        val cleanFingerprint = CrossProductGuard.cleanFingerprint(fingerprint, cleanAnalysis)
        val blob = blob(cleanAnalysis, cleanFingerprint, firstFrameContext)
        val setting = resolveSetting(cleanAnalysis, cleanFingerprint, firstFrameContext, blob)
        val angle = SellingAngleSelector.winner(cleanAnalysis, cleanFingerprint, setting, blob)
        val primary = angle.motivation
        val secondary = SellingAngleSelector.secondary(angle, cleanAnalysis, cleanFingerprint, setting, blob)
        val idea = angle.idea
        val hookType = angle.hookType
        val action = CreativeStrategyEngine.safeAction(cleanFingerprint, cleanAnalysis, idea, setting.type)
        val human = humanFor(setting.type, cleanFingerprint, cleanAnalysis)
        val lighting = lightingFor(setting.type, blob)
        val kitchen = setting.type == CreativeStrategyEngine.SettingType.HOME_KITCHEN
        return CreativeStrategyEngine.Plan(
            primary = primary,
            secondary = secondary,
            idea = idea,
            hookType = hookType,
            setting = setting.text,
            human = human,
            action = action,
            lighting = lighting,
            camera = cameraFor(setting.type),
            formatTone = formatTone(setting.type, idea),
            speechContext = speechContext(setting.type),
            opening = openingFor(hookType),
            pitch = pitchFor(primary, idea),
            confidence = setting.confidence,
            conceptKind = "canonical",
            kitchenDefault = kitchen,
            boughtFor = PurchaseAppealEngine.boughtFor(primary),
            viewerFeel = PurchaseAppealEngine.viewerFeel(primary),
            desire = PurchaseAppealEngine.desireFor(idea),
            settingType = setting.type,
            settingConfidence = setting.confidence,
            emotionalTone = emotionalTone(setting.type, primary),
            hookIntent = hookIntent(hookType),
            speechIntent = "one casual native line that states the single desire",
            visualStyle = visualStyle(setting.type),
            actionRiskLabel = if (PurchaseAppealEngine.scoreAction(action).highRisk) "HIGH" else "LOW",
        )
    }

    fun resolveSetting(
        analysis: JSONObject?,
        fingerprint: JSONObject?,
        firstFrameContext: String?,
        blob: String = blob(analysis, fingerprint, firstFrameContext),
    ): SettingEvidence {
        val frame = firstFrameContext.orEmpty().ifBlank { analysis?.optString("first_frame_context").orEmpty() }
        val visible = analysis?.optString("observed_context").orEmpty()
        val use = analysis?.optString("observed_use_case").orEmpty()
        val category = analysis?.optString("product_category").orEmpty()
        val supporting = analysis?.optJSONArray("supporting_contexts")?.let { arr ->
            (0 until arr.length()).joinToString(" ") { arr.optString(it) }
        }.orEmpty()
        val contextText = frame.ifBlank { visible }
        val packshot = isPackshot(contextText)
        val weighted = listOf(
            scoreLayer(contextText, if (packshot) 1.0 else 3.0, if (frame.isBlank()) "visible_context" else "first_frame")
                .takeUnless { packshot && frame.isNotBlank() },
            scoreLayer(visible, if (packshot) 1.2 else 2.6, "visible_context")
                .takeUnless { !packshot && visible.equals(contextText, true) },
            scoreLayer(supporting, 1.8, "supporting"),
            scoreLayer(use, 2.0, "use_case"),
            scoreLayer(category, 1.8, "category"),
            scoreLayer(blob, 0.35, "blob"),
        ).filterNotNull()
        val totals = mutableMapOf<CreativeStrategyEngine.SettingType, Double>()
        val sources = mutableMapOf<CreativeStrategyEngine.SettingType, String>()
        weighted.forEach { (type, score, source) ->
            if (score <= 0) return@forEach
            totals[type] = (totals[type] ?: 0.0) + score
            if ((totals[type] ?: 0.0) >= (totals[sources.keys.firstOrNull()] ?: 0.0)) {
                sources[type] = source
            }
        }
        val best = totals.maxByOrNull { it.value }
        if (best == null || best.value < 1.15) {
            val outdoorHint = outdoorTokens.any { blob.contains(it) }
            val type = if (outdoorHint) CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL
            else CreativeStrategyEngine.SettingType.INDOOR_NEUTRAL
            return SettingEvidence(type, 0.35, "fallback", settingText(type, lowConfidence = true))
        }
        val type = best.key
        val confidence = (best.value / 6.0).coerceIn(0.4, 0.98)
        val generic = type == CreativeStrategyEngine.SettingType.INDOOR_NEUTRAL ||
            type == CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL ||
            type == CreativeStrategyEngine.SettingType.OTHER ||
            type == CreativeStrategyEngine.SettingType.RETAIL_NEUTRAL_TABLETOP
        return SettingEvidence(type, confidence, sources[type] ?: "evidence", settingText(type, lowConfidence = generic))
    }

    fun settingConflicts(text: String, plan: CreativeStrategyEngine.Plan): Boolean {
        val lower = text.lowercase()
        val foreign = CreativeStrategyEngine.SettingType.entries.filter { it != plan.settingType }.any { other ->
            other != CreativeStrategyEngine.SettingType.OTHER &&
                other != CreativeStrategyEngine.SettingType.INDOOR_NEUTRAL &&
                other != CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL &&
                other != CreativeStrategyEngine.SettingType.RETAIL_NEUTRAL_TABLETOP &&
                other.conflictTokens().any { lower.contains(it) } &&
                plan.settingType.ownTokens().none { lower.contains(it) }
        }
        if (!foreign) return false
        return plan.settingConfidence >= 0.5
    }

    fun coherenceFailures(prompt: String, hook: String, plan: CreativeStrategyEngine.Plan): List<String> {
        val failures = mutableListOf<String>()
        val setting = prompt.substringAfter("SETTING:", "").substringBefore("CAMERA:", prompt).lowercase()
        val lighting = prompt.substringAfter("LIGHTING:", "").substringBefore("SPEECH:", "").lowercase()
        val format = prompt.substringAfter("FORMAT:", "").substringBefore("REFERENCE:", "").lowercase()
        val speech = prompt.substringAfter("SPEECH:", "").substringBefore("ANTI-MORPH:", "").lowercase()
        if (settingConflicts(setting, plan) || settingConflicts(format, plan)) failures.add("setting_mismatch")
        if (plan.settingType.isOutdoor() && lighting.contains("home indoor window") && !lighting.contains("outdoor")) {
            failures.add("lighting_mismatch")
        }
        if (!plan.settingType.isOutdoor() && lighting.contains("ordinary outdoor daylight") && plan.settingConfidence >= 0.55) {
            failures.add("lighting_mismatch")
        }
        if (hook.isNotBlank() && settingConflicts(hook, plan)) failures.add("speech_setting_mismatch")
        if (plan.settingType.isOutdoor() && speech.contains("in their own kitchen")) failures.add("speech_setting_mismatch")
        if (!plan.kitchenDefault && format.contains("lived-in kitchen feeling")) failures.add("format_mismatch")
        if (plan.settingType == CreativeStrategyEngine.SettingType.HOME_KITCHEN &&
            format.contains("natural outdoor hobby") && plan.settingConfidence >= 0.55
        ) {
            failures.add("format_mismatch")
        }
        return failures.distinct()
    }

    fun settingText(type: CreativeStrategyEngine.SettingType, lowConfidence: Boolean): String {
        if (lowConfidence) {
            return when {
                type.isOutdoor() ->
                    "Neutral realistic outdoor spot matching the First Frame. Ordinary daylight. Not a studio, showroom or invented niche set."
                else ->
                    "Neutral realistic indoor environment matching the First Frame. Natural available light. Not a studio, showroom or invented niche set."
            }
        }
        return when (type) {
            CreativeStrategyEngine.SettingType.HOME_KITCHEN ->
                "Ordinary cozy home kitchen. Warm, lived-in, slightly imperfect. Not a studio, showroom or commercial set."
            CreativeStrategyEngine.SettingType.BATHROOM ->
                "Real home bathroom. Ordinary indoor light. Lived-in, not a studio or showroom."
            CreativeStrategyEngine.SettingType.BEDROOM ->
                "Ordinary home bedroom. Natural indoor light. Lived-in, not a studio or showroom."
            CreativeStrategyEngine.SettingType.LIVING_ROOM ->
                "Ordinary lived-in living room. Natural indoor light. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.OFFICE ->
                "Ordinary home desk or small office. Natural indoor light. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.WORKSHOP ->
                "Ordinary home workshop or repair corner. Lived-in, slightly imperfect. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.GARAGE ->
                "Ordinary home garage. Lived-in, slightly imperfect. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.CAR ->
                "Ordinary car interior or driveway context. Natural available light. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.GARDEN ->
                "Simple home garden or yard. Natural outdoor daylight. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.CAMPSITE ->
                "Simple outdoor camp area. Natural daylight. Real use, not a studio or showroom."
            CreativeStrategyEngine.SettingType.FISHING_SPOT ->
                "Real lakeside or riverside fishing spot. Ordinary outdoor daylight. Lived-in hobby scene, not a studio, showroom or commercial set."
            CreativeStrategyEngine.SettingType.BEACH ->
                "Ordinary beach or shoreline. Natural daylight. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.GYM ->
                "Simple gym or home workout corner. Ordinary available light. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.TRAVEL ->
                "Simple travel or on-the-go context matching the references. Natural available light. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.RETAIL_NEUTRAL_TABLETOP ->
                "Quiet realistic tabletop matching the First Frame. Natural available light. Not a commercial set."
            CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL ->
                "Neutral realistic outdoor spot matching the First Frame. Ordinary daylight. Not a studio or showroom."
            CreativeStrategyEngine.SettingType.INDOOR_NEUTRAL, CreativeStrategyEngine.SettingType.OTHER ->
                "Ordinary lived-in home interior matching the First Frame. Natural available light. Not a studio, showroom or commercial set."
        }
    }

    fun lightingFor(type: CreativeStrategyEngine.SettingType, blob: String = ""): String {
        val evening = listOf("evening", "night", "вечер", "ноч", "dusk").any { blob.contains(it) }
        return when {
            evening ->
                "Realistic practical ambient evening light. Slightly imperfect real UGC. No studio-commercial lighting, glossy advertising look, fake glow, or showroom perfection."
            type.isOutdoor() ->
                "Ordinary outdoor daylight. Natural reflections. Slightly imperfect real UGC. No studio-commercial lighting, glossy advertising look, fake glow, or showroom perfection."
            type == CreativeStrategyEngine.SettingType.CAR || type == CreativeStrategyEngine.SettingType.TRAVEL ->
                "Natural available light matching the real use context. Slightly imperfect real UGC. No studio-commercial lighting, glossy advertising look, fake glow, or showroom perfection."
            else -> ProductLock.LIGHTING_LOCK
        }
    }

    fun humanFor(
        type: CreativeStrategyEngine.SettingType,
        fingerprint: JSONObject?,
        analysis: JSONObject?,
    ): String {
        val extra = when {
            CreativeStrategyEngine.looksLikeFishingChair(fingerprint, analysis) && type == CreativeStrategyEngine.SettingType.FISHING_SPOT ->
                "Person already seated comfortably. Relaxed posture. Brief natural use of a visible tray or a light rest on the arm/seat. No folding or complex adjustment."
            ProductIdentity.looksLikeMicrowaveCover(fingerprint) && type == CreativeStrategyEngine.SettingType.HOME_KITCHEN ->
                "Cover already over a plate. One hand lightly touches the handle. Microwave context stays visible in the background."
            ProductIdentity.looksLikeMicrowaveCover(fingerprint) ->
                "Cover already in place. One hand lightly touches the visible handle. Keep the referenced environment."
            ProductIdentity.looksLikeCookwarePan(fingerprint, analysis) && type == CreativeStrategyEngine.SettingType.HOME_KITCHEN ->
                "Casual touch of the wooden handle. Lid remains static. Relaxed kitchen moment."
            ProductIdentity.looksLikeCookwarePan(fingerprint, analysis) ->
                "Casual touch of the wooden handle. Lid remains static. Relaxed everyday handling."
            else -> when (type) {
                CreativeStrategyEngine.SettingType.HOME_KITCHEN -> "Relaxed kitchen movement. Casual everyday handling."
                CreativeStrategyEngine.SettingType.FISHING_SPOT, CreativeStrategyEngine.SettingType.CAMPSITE,
                CreativeStrategyEngine.SettingType.BEACH, CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL,
                CreativeStrategyEngine.SettingType.GARDEN -> "Calm outdoor use. Natural hobby posture."
                CreativeStrategyEngine.SettingType.OFFICE -> "Natural desk interaction. Quiet, unhurried."
                CreativeStrategyEngine.SettingType.WORKSHOP, CreativeStrategyEngine.SettingType.GARAGE ->
                    "Ordinary workshop handling. Practical, not performative."
                CreativeStrategyEngine.SettingType.BATHROOM -> "Simple personal-use gesture. Quiet and everyday."
                CreativeStrategyEngine.SettingType.CAR -> "Casual in-car or driveway handling."
                CreativeStrategyEngine.SettingType.GYM -> "Simple personal-use gesture in a workout context."
                CreativeStrategyEngine.SettingType.TRAVEL -> "Casual on-the-go handling."
                else -> "Relaxed real-use posture."
            }
        }
        return "${ProductLock.HUMAN_LOCK} $extra No presenter poses, pointing at features, fake influencer gestures, excessive smiling or theatrical reactions."
    }

    fun speechContext(type: CreativeStrategyEngine.SettingType): String = when (type) {
        CreativeStrategyEngine.SettingType.HOME_KITCHEN -> "in their own kitchen"
        CreativeStrategyEngine.SettingType.BATHROOM -> "in a real bathroom at home"
        CreativeStrategyEngine.SettingType.BEDROOM -> "in their own bedroom"
        CreativeStrategyEngine.SettingType.LIVING_ROOM -> "in their living room"
        CreativeStrategyEngine.SettingType.OFFICE -> "at a desk"
        CreativeStrategyEngine.SettingType.WORKSHOP, CreativeStrategyEngine.SettingType.GARAGE -> "in a home workshop corner"
        CreativeStrategyEngine.SettingType.CAR -> "by the car in a real moment"
        CreativeStrategyEngine.SettingType.GARDEN -> "in the garden"
        CreativeStrategyEngine.SettingType.CAMPSITE -> "at a simple camp"
        CreativeStrategyEngine.SettingType.FISHING_SPOT -> "at a real fishing spot"
        CreativeStrategyEngine.SettingType.BEACH -> "by the water"
        CreativeStrategyEngine.SettingType.GYM -> "in a simple workout corner"
        CreativeStrategyEngine.SettingType.TRAVEL -> "on the go"
        CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL -> "outdoors in a real use moment"
        else -> "at home in a real moment"
    }

    fun formatTone(type: CreativeStrategyEngine.SettingType, idea: CreativeStrategyEngine.SellingIdea): String {
        val desire = PurchaseAppealEngine.desireFor(idea)
        val feeling = when (type) {
            CreativeStrategyEngine.SettingType.HOME_KITCHEN ->
                "Warm, homely, lived-in kitchen feeling. Not a showroom. Organic UGC, not a technical demo."
            CreativeStrategyEngine.SettingType.FISHING_SPOT, CreativeStrategyEngine.SettingType.CAMPSITE,
            CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL, CreativeStrategyEngine.SettingType.BEACH,
            CreativeStrategyEngine.SettingType.GARDEN ->
                "Natural outdoor hobby UGC. Real use, not a showroom. Organic UGC, not a technical demo."
            else -> "Natural lived-in UGC matching the real use setting. Not a showroom. Organic UGC, not a technical demo."
        }
        return "$feeling One desire only: $desire. Not a technical demo or feature brochure."
    }

    fun cameraFor(type: CreativeStrategyEngine.SettingType): String {
        val tail = if (type == CreativeStrategyEngine.SettingType.HOME_KITCHEN) {
            " Warm homely handheld UGC, not a polished commercial move."
        } else {
            " Natural handheld UGC in the real use setting, not a polished commercial move."
        }
        return ProductLock.CAMERA_LOCK + tail
    }

    fun openingFor(hookType: CreativeStrategyEngine.HookType): String = when (hookType) {
        CreativeStrategyEngine.HookType.PROBLEM, CreativeStrategyEngine.HookType.DAILY_FRUSTRATION ->
            "hook and immediate problem context so the relief idea is already clear"
        CreativeStrategyEngine.HookType.COMFORT -> "hook and immediate comfort context so the viewer already feels why it helps"
        CreativeStrategyEngine.HookType.CONVENIENCE, CreativeStrategyEngine.HookType.ORGANIZATION ->
            "hook and immediate convenience context so usefulness is already clear"
        CreativeStrategyEngine.HookType.VISUAL -> "hook and immediate visual presence so desirability is already clear"
        CreativeStrategyEngine.HookType.CURIOSITY, CreativeStrategyEngine.HookType.SATISFYING_USE ->
            "hook and immediate useful detail so the idea is already clear"
        CreativeStrategyEngine.HookType.HOME, CreativeStrategyEngine.HookType.OWNERSHIP ->
            "hook and immediate home context so the viewer already grasps the everyday appeal"
        CreativeStrategyEngine.HookType.OUTDOOR ->
            "hook and immediate outdoor context so the viewer already feels why this helps outside"
        CreativeStrategyEngine.HookType.SIMPLICITY ->
            "hook and immediate simple-use context so the idea is already clear"
    }

    fun pitchFor(primary: CreativeStrategyEngine.Motivation, idea: CreativeStrategyEngine.SellingIdea): String =
        "${idea.name.lowercase().replace('_', ' ')} in a real ${primary.name.lowercase().replace('_', ' ')} moment"

    private fun emotionalTone(type: CreativeStrategyEngine.SettingType, primary: CreativeStrategyEngine.Motivation): String = when {
        type.isOutdoor() -> "calm outdoor ease"
        type == CreativeStrategyEngine.SettingType.HOME_KITCHEN -> "warm everyday home"
        primary == CreativeStrategyEngine.Motivation.PROBLEM_SOLVER -> "quiet relief"
        else -> "simple everyday usefulness"
    }

    private fun hookIntent(type: CreativeStrategyEngine.HookType): String = when (type) {
        CreativeStrategyEngine.HookType.PROBLEM, CreativeStrategyEngine.HookType.DAILY_FRUSTRATION -> "name the irritation, then the relief"
        CreativeStrategyEngine.HookType.COMFORT, CreativeStrategyEngine.HookType.OUTDOOR -> "make the easier experience felt immediately"
        CreativeStrategyEngine.HookType.VISUAL, CreativeStrategyEngine.HookType.OWNERSHIP -> "make the object desirable to have"
        else -> "make one useful everyday benefit obvious"
    }

    private fun visualStyle(type: CreativeStrategyEngine.SettingType): String =
        if (type.isOutdoor()) "handheld outdoor UGC" else "handheld indoor UGC"

    private fun scoreLayer(
        text: String,
        weight: Double,
        source: String,
    ): Triple<CreativeStrategyEngine.SettingType, Double, String>? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val scored = CreativeStrategyEngine.SettingType.entries.mapNotNull { type ->
            val hits = type.lexicon().count { lower.contains(it) }
            if (hits == 0) null else type to hits * weight
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return Triple(best.first, best.second, source)
    }

    private fun isPackshot(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("white background", "studio", "seamless", "packshot", "infographic", "marketplace", "pure white").any { lower.contains(it) }
    }

    fun blob(analysis: JSONObject?, fingerprint: JSONObject?, firstFrameContext: String? = null): String =
        CrossProductGuard.planningText(analysis, fingerprint, firstFrameContext).lowercase()

    private val outdoorTokens = listOf("outdoor", "outside", "draußen", "улиц", "lake", "camp", "fish", "garden", "beach")
}

internal fun CreativeStrategyEngine.SettingType.isOutdoor(): Boolean = this in setOf(
    CreativeStrategyEngine.SettingType.CAMPSITE,
    CreativeStrategyEngine.SettingType.FISHING_SPOT,
    CreativeStrategyEngine.SettingType.BEACH,
    CreativeStrategyEngine.SettingType.GARDEN,
    CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL,
)

private fun CreativeStrategyEngine.SettingType.lexicon(): List<String> = when (this) {
    CreativeStrategyEngine.SettingType.HOME_KITCHEN -> listOf("kitchen", "кухн", "küche", "stove", "herd", "cooktop", "плит", "home kitchen")
    CreativeStrategyEngine.SettingType.BATHROOM -> listOf("bathroom", "ванн", "bath ", "sink vanity")
    CreativeStrategyEngine.SettingType.BEDROOM -> listOf("bedroom", "спальн", "bedside")
    CreativeStrategyEngine.SettingType.LIVING_ROOM -> listOf("living room", "гостиц", "wohnzimmer", "sofa")
    CreativeStrategyEngine.SettingType.OFFICE -> listOf("office", "офис", "desk", "büro", "workspace")
    CreativeStrategyEngine.SettingType.WORKSHOP -> listOf("workshop", "werkstatt", "workbench", "repair")
    CreativeStrategyEngine.SettingType.GARAGE -> listOf("garage", "гараж")
    CreativeStrategyEngine.SettingType.CAR -> listOf("car interior", "dashboard", "авто", "driveway", "in the car")
    CreativeStrategyEngine.SettingType.GARDEN -> listOf("garden", "yard", "сад", "garten", "backyard")
    CreativeStrategyEngine.SettingType.CAMPSITE -> listOf("camp", "кемпинг", "tent", "палатк", "campsite")
    CreativeStrategyEngine.SettingType.FISHING_SPOT -> listOf("fish", "рыбал", "lake", "lakeside", "riverside", "bait", "ufer", "берег", "angeln", "озера")
    CreativeStrategyEngine.SettingType.BEACH -> listOf("beach", "shore", "пляж", "strand")
    CreativeStrategyEngine.SettingType.GYM -> listOf("gym", "workout", "спортзал", "fitness")
    CreativeStrategyEngine.SettingType.TRAVEL -> listOf("travel", "airport", "suitcase", "поездк", "unterwegs")
    CreativeStrategyEngine.SettingType.RETAIL_NEUTRAL_TABLETOP -> listOf("tabletop", "white table", "counter display")
    CreativeStrategyEngine.SettingType.OUTDOOR_NEUTRAL -> listOf("outdoor", "outside", "draußen", "на улиц")
    CreativeStrategyEngine.SettingType.INDOOR_NEUTRAL, CreativeStrategyEngine.SettingType.OTHER -> emptyList()
}

private fun CreativeStrategyEngine.SettingType.ownTokens(): List<String> = lexicon() + when (this) {
    CreativeStrategyEngine.SettingType.HOME_KITCHEN -> listOf("kitchen")
    CreativeStrategyEngine.SettingType.FISHING_SPOT -> listOf("lakeside", "riverside", "fishing")
    else -> emptyList()
}

private fun CreativeStrategyEngine.SettingType.conflictTokens(): List<String> = when (this) {
    CreativeStrategyEngine.SettingType.HOME_KITCHEN -> listOf("kitchen", "küche", "кухн")
    CreativeStrategyEngine.SettingType.FISHING_SPOT -> listOf("lakeside", "fishing spot", "рыбал")
    CreativeStrategyEngine.SettingType.CAMPSITE -> listOf("camp area", "campsite")
    CreativeStrategyEngine.SettingType.WORKSHOP -> listOf("workshop")
    CreativeStrategyEngine.SettingType.GARAGE -> listOf("garage")
    CreativeStrategyEngine.SettingType.OFFICE -> listOf("desk", "office")
    else -> emptyList()
}
