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
        val forbidden: List<String>
    )

    val PAN = ProductLockSpec(
        id = "deep_black_pan_wooden_lid",
        identityHints = listOf("pan", "сковород", "pfanne", "lid", "крышк"),
        requiredDetails = listOf(
            "deep rounded bowl",
            "high sides",
            "wooden handle",
            "ferrule",
            "rivet",
            "hanging ring",
            "wooden lid"
        ),
        forbidden = listOf("wok", "generic pan", "cast-iron skillet substitute")
    )

    val FISHING_CHAIR = ProductLockSpec(
        id = "fishing_chair",
        identityHints = listOf("chair", "стул", "fishing"),
        requiredDetails = listOf("frame", "backrest", "tray", "feet"),
        forbidden = listOf("generic camping chair")
    )

    val PH_BITS = ProductLockSpec(
        id = "ph_screwdriver_bits",
        identityHints = listOf("bit", "ph", "screwdriver", "бит"),
        requiredDetails = listOf("PH tip", "collar"),
        forbidden = listOf("generic replacement bits")
    )

    val RICE_WASHER = ProductLockSpec(
        id = "rice_washing_container",
        identityHints = listOf("rice", "wash", "drain", "рис"),
        requiredDetails = listOf("bowl", "lid", "drain"),
        forbidden = listOf("generic bowl")
    )

    val STOVE_CASE = ProductLockSpec(
        id = "closed_portable_stove_case",
        identityHints = listOf("stove", "case", "плит"),
        requiredDetails = listOf("closed case"),
        forbidden = listOf("open burner", "flame", "canister")
    )

    val CONTACT_GRILL = ProductLockSpec(
        id = "contact_grill",
        identityHints = listOf("grill", "гриль"),
        requiredDetails = listOf("plates"),
        forbidden = listOf("invented heating coils")
    )

    val all = listOf(PAN, FISHING_CHAIR, PH_BITS, RICE_WASHER, STOVE_CASE, CONTACT_GRILL)

    fun matchingSpec(model: ProductModel): ProductLockSpec? {
        val blob = (model.productIdentity + " " + model.productCategory + " " +
            model.visualSignature.joinToString(" ")).lowercase()
        return all.firstOrNull { spec ->
            spec.identityHints.any { blob.contains(it.lowercase()) }
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
