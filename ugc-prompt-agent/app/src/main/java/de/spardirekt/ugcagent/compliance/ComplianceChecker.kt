package de.spardirekt.ugcagent.compliance

data class ForbiddenHit(
    val pattern: String,
)

data class ComplianceResult(
    val forbiddenHits: List<ForbiddenHit>,
    val hasAdDisclosure: Boolean,
) {
    val hasForbiddenLanguage: Boolean get() = forbiddenHits.isNotEmpty()
    val missingAdDisclosure: Boolean get() = !hasAdDisclosure
}

object ComplianceChecker {
    val forbiddenPatterns: List<Regex> = listOf(
        // Superlative / unbelegte Bestleistungen
        Regex("(?i)\\bbeste[rsn]?\\b"),
        Regex("(?i)\\beinzigartig\\b"),
        Regex("(?i)\\bgarantiert\\b"),
        Regex("(?i)\\b100%\\b"),
        // Medizinische/Heilversprechen
        Regex("(?i)\\bheilt\\b"),
        Regex("(?i)\\bkuriert\\b"),
        Regex("(?i)\\blindert\\s+schmerzen\\b"),
        // UWG-relevante Lieferversprechen
        Regex("(?i)\\bschnell\\s+geliefert\\b"),
        Regex("(?i)\\bversandkostenfrei\\s+garantiert\\b"),
        // Übertriebene Dringlichkeit
        Regex("(?i)\\bnur\\s+heute\\b"),
        Regex("(?i)\\blimitiert\\b"),
    )

    private val adDisclosure = Regex("(?i)\\b(werbung|anzeige)\\b")

    fun checkCompliance(text: String): List<String> =
        forbiddenPatterns.filter { it.containsMatchIn(text) }.map { it.pattern }

    fun checkAdDisclosure(text: String): Boolean =
        adDisclosure.containsMatchIn(text)

    fun evaluate(prompt: String, caption: String = ""): ComplianceResult {
        val combined = listOf(prompt, caption).filter { it.isNotBlank() }.joinToString("\n")
        val hits = checkCompliance(combined).map { ForbiddenHit(it) }
        return ComplianceResult(
            forbiddenHits = hits,
            hasAdDisclosure = checkAdDisclosure(combined),
        )
    }
}
