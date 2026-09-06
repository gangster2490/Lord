package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

/**
 * Stops leftover identity/scene language from one product family appearing in another.
 */
object CrossProductGuard {
    enum class Family { MICROWAVE_COVER, COOKWARE_PAN, FISHING_GEAR, GENERIC }

    fun family(
        fingerprint: JSONObject?,
        analysis: JSONObject?,
        extra: String = "",
    ): Family {
        val fromEvidence = familyFromEvidence(fingerprint, analysis)
        if (fromEvidence != Family.GENERIC) return fromEvidence
        if (fingerprint != null) return Family.GENERIC
        val use = listOf(
            analysis?.optString("product_category").orEmpty(),
            analysis?.optString("observed_use_case").orEmpty(),
        ).joinToString(" ").trim()
        if (use.isNotEmpty()) return Family.GENERIC
        return inferFamilyFromText(extra)
    }

    fun leakTerms(
        fingerprint: JSONObject?,
        analysis: JSONObject?,
        plan: CreativeStrategyEngine.Plan? = null,
        extra: String = "",
    ): List<String> {
        val fam = family(fingerprint, analysis, extra)
        if (fingerprint == null && analysis == null && fam == Family.GENERIC) return emptyList()
        val kitchenOk = kitchenAllowed(fam, plan)
        val fishingOk = fam == Family.FISHING_GEAR ||
            plan?.settingType == CreativeStrategyEngine.SettingType.FISHING_SPOT
        val out = mutableListOf<String>()
        if (fam != Family.MICROWAVE_COVER) out += microwavePack
        if (fam != Family.COOKWARE_PAN) out += panPack
        if (!fishingOk) out += fishingPack
        if (!kitchenOk) out += kitchenScenePack
        return out.distinct()
    }

    fun leaks(
        text: String,
        fingerprint: JSONObject?,
        analysis: JSONObject?,
        plan: CreativeStrategyEngine.Plan? = null,
        extra: String = "",
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val lower = text.lowercase()
        val blob = extra.ifBlank { text }
        return leakTerms(fingerprint, analysis, plan, blob).filter { term ->
            lower.contains(term.lowercase())
        }
    }

    fun containsLeak(
        text: String,
        fingerprint: JSONObject?,
        analysis: JSONObject?,
        plan: CreativeStrategyEngine.Plan? = null,
        extra: String = "",
    ): Boolean = leaks(text, fingerprint, analysis, plan, extra).isNotEmpty()

    fun isForeignIdentityLine(
        line: String,
        fingerprint: JSONObject?,
        analysis: JSONObject?,
        extra: String = "",
    ): Boolean = leaks(line, fingerprint, analysis, extra = extra).isNotEmpty()

    fun strip(
        text: String,
        fingerprint: JSONObject?,
        analysis: JSONObject?,
        plan: CreativeStrategyEngine.Plan? = null,
    ): String {
        val foreign = leaks(text, fingerprint, analysis, plan, extra = text).sortedByDescending { it.length }
        if (foreign.isEmpty()) return text
        val heading = Regex(
            "(?im)^(FORMAT|REFERENCE|PRODUCT IDENTITY LOCK|MOVING COMPONENT LOCK|SETTING|CAMERA|ACTION|HUMAN BEHAVIOUR|LIGHTING|SPEECH|ANTI-MORPH|TIMING)\\s*:?\\s*$",
        )
        return text.lineSequence().joinToString("\n") { raw ->
            if (heading.matches(raw.trim())) return@joinToString raw
            var line = raw
            foreign.forEach { term ->
                line = Regex("(?i)${Regex.escape(term)}").replace(line, "")
            }
            line = line
                .replace(Regex("\\s+,"), ",")
                .replace(Regex(",\\s*,"), ",")
                .replace(Regex("\\s{2,}"), " ")
                .replace(Regex("\\s+\\."), ".")
                .trim()
            if (line.length < 8 && !line.contains("\"")) "" else line
        }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun familyFromEvidence(fingerprint: JSONObject?, analysis: JSONObject?): Family {
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint)) return Family.MICROWAVE_COVER
        if (CreativeStrategyEngine.looksLikeFishingChair(fingerprint, analysis)) return Family.FISHING_GEAR
        if (ProductIdentity.looksLikeCookwarePan(fingerprint, analysis)) return Family.COOKWARE_PAN
        val use = listOf(
            analysis?.optString("product_category").orEmpty(),
            analysis?.optString("observed_use_case").orEmpty(),
        ).joinToString(" ").lowercase()
        return when {
            listOf("microwave cover", "cover food", "микроволн", "mikrowelle").any { use.contains(it) } ->
                Family.MICROWAVE_COVER
            listOf("fishing chair", "bait tray", "рыбал").any { use.contains(it) } -> Family.FISHING_GEAR
            listOf("frying pan", "saucepan", "skillet", "cookware", "сковород", "кастрюл").any { use.contains(it) } ->
                Family.COOKWARE_PAN
            else -> Family.GENERIC
        }
    }

    private fun kitchenAllowed(fam: Family, plan: CreativeStrategyEngine.Plan?): Boolean {
        if (fam == Family.MICROWAVE_COVER) return true
        if (plan?.kitchenDefault == true || plan?.settingType == CreativeStrategyEngine.SettingType.HOME_KITCHEN) return true
        if (fam != Family.COOKWARE_PAN) return false
        val type = plan?.settingType ?: return true
        return !type.isOutdoor() &&
            type != CreativeStrategyEngine.SettingType.OFFICE &&
            type != CreativeStrategyEngine.SettingType.WORKSHOP &&
            type != CreativeStrategyEngine.SettingType.GARAGE &&
            type != CreativeStrategyEngine.SettingType.GYM &&
            type != CreativeStrategyEngine.SettingType.TRAVEL &&
            type != CreativeStrategyEngine.SettingType.CAR
    }

    private fun inferFamilyFromText(text: String): Family {
        if (text.isBlank()) return Family.GENERIC
        val lower = text.lowercase()
        fun hits(pack: List<String>) = pack.count { lower.contains(it.lowercase()) }
        val ranked = listOf(
            Family.MICROWAVE_COVER to hits(microwavePack),
            Family.COOKWARE_PAN to hits(panPack),
            Family.FISHING_GEAR to hits(fishingPack),
        ).sortedByDescending { it.second }
        val best = ranked.first()
        val second = ranked.getOrNull(1)?.second ?: 0
        return if (best.second >= 2 && best.second > second) best.first else Family.GENERIC
    }

    private val microwavePack = listOf(
        "cylindrical reservoir",
        "circular upper vent",
        "circular upper component",
        "circular vent",
        "rectangular upper modules",
        "rectangular modules",
        "rectangular transparent upper",
        "green circular base ring",
        "transparent dome body",
        "microwave cover",
        "microwave",
        "микроволн",
        "mikrowelle",
        "разогрев",
        "cover food",
        "отмывать микроволновку",
        "nach jedem aufwärmen",
        "aufwärmen zu putzen",
    )

    private val panPack = listOf(
        "hanging ring",
        "wooden handle of the referenced pan",
        "wooden handle grip",
        "metallic/gold-colored collar",
        "short metal tang",
        "referenced pan",
        "frying pan",
        "сковород",
        "кастрюл",
    )

    private val fishingPack = listOf(
        "side bait tray",
        "bait tray",
        "fishing chair",
        "fishing spot",
        "lakeside",
        "riverside",
        "на рыбалке",
        "рыбал",
        "beim angeln",
        "angeln",
        "берег",
        "am wasser so zu sitzen",
    )

    private val kitchenScenePack = listOf(
        "ordinary cozy home kitchen",
        "lived-in kitchen feeling",
        "in their own kitchen",
        "warm, homely, lived-in kitchen",
        "домашнюю кухню",
        "in der küche",
        "на кухне",
        "relaxed kitchen moment",
        "relaxed kitchen movement",
    )
}
