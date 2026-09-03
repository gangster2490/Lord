package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel

/**
 * Regression product locks used by tests and validation hints.
 */
object RegressionLocks {

    data class ProductLockSpec(
        val id: String,
        val identityHints: List<String>,
        val requiredDetails: List<String>,
        val forbidden: List<String>,
        val setting: String,
        val overlayLines: List<String>,
        val negativePrefix: List<String>,
        val voiceover: String
    )

    val PAN = ProductLockSpec(
        id = "deep_black_pan_wooden_lid",
        identityHints = listOf("pan", "сковород", "pfanne", "крышк"),
        requiredDetails = listOf(
            "deep rounded bowl",
            "high sides",
            "wooden handle",
            "ferrule",
            "rivet",
            "hanging ring",
            "wooden lid"
        ),
        forbidden = listOf("wok", "generic pan", "cast-iron skillet substitute"),
        setting = "Professional kitchen studio, warm daylight, marble counter, 35mm close-up.",
        overlayLines = listOf("Holzdeckel", "Tiefe Form"),
        negativePrefix = listOf(
            "- no generic replacement pan or wok",
            "- no redesigned silhouette or shallower bowl",
            "- no missing wooden lid, ferrule, rivets or hanging ring",
            "- no changed handle geometry",
            "- no invented non-stick claims",
            "- no product morphing between shots"
        ),
        voiceover = "Tiefer Topf, fester Holzdeckel, einfach kochen."
    )

    val FISHING_CHAIR = ProductLockSpec(
        id = "fishing_chair",
        identityHints = listOf("chair", "стул", "fishing"),
        requiredDetails = listOf("frame", "backrest", "tray", "feet"),
        forbidden = listOf("generic camping chair"),
        setting = "Outdoor lakeside bank, folding fishing chair, soft daylight, 35mm medium shot.",
        overlayLines = listOf("Rahmen", "Tablett"),
        negativePrefix = listOf(
            "- no generic camping chair",
            "- no missing metal frame",
            "- no missing padded backrest",
            "- no missing side tray",
            "- no missing rubber feet",
            "- no kitchen cookware or lid swap"
        ),
        voiceover = "Fester Rahmen, gepolsterte Lehne, Tablett bleibt dabei."
    )

    val PH_BITS = ProductLockSpec(
        id = "ph_screwdriver_bits",
        identityHints = listOf("bit", "ph", "screwdriver", "бит"),
        requiredDetails = listOf("PH tip", "collar"),
        forbidden = listOf("generic replacement bits"),
        setting = "Organized workshop bench, PH screwdriver bits, daylight, 35mm close-up.",
        overlayLines = listOf("PH Bits", "Kragen"),
        negativePrefix = listOf(
            "- no generic replacement bits",
            "- no missing PH tip geometry",
            "- no missing hex collar",
            "- no missing length markings",
            "- no kitchen cookware or lid swap",
            "- no extra tool brands"
        ),
        voiceover = "PH-Spitze, klarer Kragen, griffbereit auf der Bank."
    )

    val RICE_WASHER = ProductLockSpec(
        id = "rice_washing_container",
        identityHints = listOf("rice", "wash", "drain", "рис"),
        requiredDetails = listOf("bowl", "lid", "drain"),
        forbidden = listOf("generic bowl"),
        setting = "Clean kitchen sink, rice washing container, daylight, 35mm close-up.",
        overlayLines = listOf("Schüssel", "Ablauf"),
        negativePrefix = listOf(
            "- no generic bowl",
            "- no missing clear bowl",
            "- no missing fitted lid",
            "- no missing side drain",
            "- no kitchen cookware or lid swap",
            "- no product morphing between shots"
        ),
        voiceover = "Klare Schüssel, fester Deckel, Wasser läuft seitlich ab."
    )

    val STOVE_CASE = ProductLockSpec(
        id = "closed_portable_stove_case",
        identityHints = listOf("stove", "case", "плит"),
        requiredDetails = listOf("closed case"),
        forbidden = listOf("open burner", "flame", "canister"),
        setting = "Camping table, closed portable stove case, daylight, 35mm close-up.",
        overlayLines = listOf("Koffer", "Geschlossen"),
        negativePrefix = listOf(
            "- no open burner",
            "- no flame",
            "- no canister",
            "- no missing closed case",
            "- no kitchen cookware or lid swap",
            "- no invented stove internals"
        ),
        voiceover = "Geschlossener Koffer, feste Verschlüsse, einfach tragen."
    )

    val CONTACT_GRILL = ProductLockSpec(
        id = "contact_grill",
        identityHints = listOf("grill", "гриль"),
        requiredDetails = listOf("plates"),
        forbidden = listOf("invented heating coils"),
        setting = "Kitchen countertop, contact grill, daylight, 35mm close-up.",
        overlayLines = listOf("Platten", "Kontakt"),
        negativePrefix = listOf(
            "- no invented heating coils",
            "- no missing ridged plates",
            "- no missing hinge",
            "- no missing lid handle",
            "- no kitchen cookware or lid swap",
            "- no product morphing between shots"
        ),
        voiceover = "Gerillte Platten, festes Scharnier, gleichmäßig zu."
    )

    val all = listOf(PAN, FISHING_CHAIR, PH_BITS, RICE_WASHER, STOVE_CASE, CONTACT_GRILL)

    fun matchingSpec(model: ProductModel): ProductLockSpec? {
        val blob = (model.productIdentity + " " + model.productCategory + " " +
            model.visualSignature.joinToString(" ")).lowercase()
        return all.maxByOrNull { spec ->
            spec.identityHints.count { hint -> blob.contains(hint.lowercase()) }
        }?.takeIf { spec ->
            spec.identityHints.any { hint -> blob.contains(hint.lowercase()) }
        }
    }

    fun violations(prompt: String, model: ProductModel): List<String> {
        val spec = matchingSpec(model) ?: return emptyList()
        val lower = prompt.lowercase()
        val missing = spec.requiredDetails.filter { detail ->
            !containsAllTokens(lower, detail)
        }
        val positive = prompt.lineSequence()
            .map { it.trim().lowercase() }
            .filterNot { it.startsWith("- no") || it.startsWith("no ") || it.startsWith("-no") }
            .joinToString("\n")
        val forbiddenHits = spec.forbidden.filter { positive.contains(it.lowercase()) }
        return missing.map { "missing:$it" } + forbiddenHits.map { "forbidden:$it" }
    }

    private fun containsAllTokens(haystack: String, detail: String): Boolean {
        val tokens = detail.lowercase()
            .split(Regex("[^a-zA-Zа-яА-Я0-9]+"))
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return haystack.contains(detail.lowercase())
        return tokens.all { haystack.contains(it) }
    }
}
