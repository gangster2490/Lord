package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject

object ProductLexicon {
    val SPECIALIZED_TERMS = listOf(
        "cylindrical reservoir",
        "steam vent",
        "heating coil",
        "blender jar",
        "solar panel",
        "sd card",
        "reservoir",
        "hinges",
        "hinge",
        "battery",
        "motor",
        "usb",
        "cartridge",
        "wick",
        "nozzle",
        "impeller",
        "turbine",
        "piston",
        "motherboard",
        "zipper",
        "clips",
        "vent",
        "tank",
        "modules",
        "dome",
    )

    fun allowedVocabulary(fingerprint: JSONObject?, analysis: JSONObject?, extra: String = ""): String {
        val parts = mutableListOf<String>()
        parts += CrossProductGuard.planningText(analysis, fingerprint)
        analysis?.optString("product_category")?.let { parts += it }
        analysis?.optString("observed_use_case")?.let { parts += it }
        if (extra.isNotBlank()) parts += extra
        if (ProductIdentity.looksLikeMicrowaveCover(fingerprint) ||
            CrossProductGuard.family(fingerprint, analysis, extra) == CrossProductGuard.Family.MICROWAVE_COVER
        ) {
            parts += ProductIdentity.MICROWAVE_COVER_LOCK
            parts += "reservoir vent clips modules dome cap tank"
        }
        if (ProductIdentity.looksLikeCookwarePan(fingerprint, analysis) ||
            CrossProductGuard.family(fingerprint, analysis, extra) == CrossProductGuard.Family.COOKWARE_PAN
        ) {
            parts += ProductIdentity.COOKWARE_PAN_LOCK
        }
        return parts.joinToString(" ").lowercase()
    }

    fun termBelongsToProduct(term: String, fingerprint: JSONObject?, analysis: JSONObject?, extra: String = ""): Boolean {
        val allowed = allowedVocabulary(fingerprint, analysis, extra)
        val needle = term.lowercase()
        if (allowed.contains(needle)) return true
        if (needle == "clips" && allowed.contains("clip")) return true
        return false
    }

    fun foreignTermsIn(text: String, fingerprint: JSONObject?, analysis: JSONObject?, extra: String = ""): List<String> {
        val lower = text.lowercase()
        return SPECIALIZED_TERMS.filter { term ->
            val pattern = Regex("(?i)\\b${Regex.escape(term)}s?\\b")
            pattern.containsMatchIn(lower) && !termBelongsToProduct(term, fingerprint, analysis, extra)
        }
    }

    fun containsForeign(text: String, fingerprint: JSONObject?, analysis: JSONObject?, extra: String = ""): Boolean =
        foreignTermsIn(text, fingerprint, analysis, extra).isNotEmpty()

    fun stripForeign(text: String, fingerprint: JSONObject?, analysis: JSONObject?, extra: String = ""): String {
        val foreign = foreignTermsIn(text, fingerprint, analysis, extra).sortedByDescending { it.length }
        if (foreign.isEmpty()) return text
        val heading = Regex("(?im)^(FORMAT|REFERENCE|PRODUCT IDENTITY LOCK|MOVING COMPONENT LOCK|SETTING|CAMERA|ACTION|HUMAN BEHAVIOUR|LIGHTING|SPEECH|ANTI-MORPH|TIMING)\\s*:?\\s*$")
        return text.lineSequence().joinToString("\n") { raw ->
            if (heading.matches(raw.trim())) return@joinToString raw
            var line = raw
            foreign.forEach { term ->
                line = Regex("(?i)\\b${Regex.escape(term)}s?\\b").replace(line, "")
            }
            line = line
                .replace(Regex("invented\\s*,", RegexOption.IGNORE_CASE), "invented")
                .replace(Regex("\\s+,", RegexOption.IGNORE_CASE), ",")
                .replace(Regex(",\\s*,"), ",")
                .replace(Regex("\\s{2,}"), " ")
                .replace(Regex("\\s+\\."), ".")
                .trim()
            if (line.length < 8 && !line.contains("\"")) "" else line
        }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
