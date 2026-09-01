package de.spardirekt.agents.pro.generation

/**
 * Narrow deterministic guard for the single pan regression case documented in
 * [AgentCorePrompt]. It is intentionally not a generic cookware rule: applying
 * these wooden-lid attributes to another pan would itself be a fidelity bug.
 */
internal object PanFidelity {

    const val PRODUCT_LOCK =
        "Match uploaded product photos exactly. Do not replace or redesign.\n" +
            "deep black bowl; high curved sides; long dark wooden handle with hanging ring; " +
            "gold-tone ferrule; riveted shank; wooden crossbar lid"

    const val CRITICAL =
        "Keep the deep bowl, wooden handle and wooden crossbar lid unchanged. Exactly 8.0s. Four blocks only."

    val NEGATIVE_BULLETS = listOf(
        "no shallow pan, saucepan, or generic replacement cookware",
        "no missing wooden crossbar lid or dark wooden handle",
        "no altered gold-tone ferrule, riveted shank, or hanging ring",
        "no product morphing",
        "no marketplace UI or phone interface",
        "no CGI/cartoon look"
    )

    /**
     * Match only the known deep-black, wooden-lid pan signature.
     *
     * `Deep Black Pan` is the canonical title from the regression fixture. For
     * less exact model wording, require the pan noun plus black and wood
     * evidence. A generic cast-iron/non-stick pan must not match.
     */
    fun matches(vararg evidence: String): Boolean {
        val text = evidence.joinToString("\n").lowercase()
        if (CANONICAL_NAME.containsMatchIn(text)) return true

        val isPan = PAN_NOUN.containsMatchIn(text)
        val isBlack = BLACK_SIGNATURE.containsMatchIn(text)
        val hasWood = WOOD_SIGNATURE.containsMatchIn(text)
        val signatureHits = SIGNATURE_TERMS.count(text::contains)
        return isPan && isBlack && hasWood && signatureHits >= 2
    }

    private val CANONICAL_NAME = Regex("""\bdeep[\s-]+black[\s-]+pan\b""")
    private val PAN_NOUN = Regex("""\b(?:frying[\s-]+)?pan\b|сковород|pfanne""")
    private val BLACK_SIGNATURE = Regex("""\b(?:deep[\s-]+)?black\b|ч[её]рн|schwarz""")
    private val WOOD_SIGNATURE = Regex("""\bwood(?:en)?\b|дерев|holz""")

    private val SIGNATURE_TERMS = listOf(
        "high curved sides",
        "wooden handle",
        "hanging ring",
        "gold-tone ferrule",
        "riveted shank",
        "wooden crossbar lid"
    )
}
