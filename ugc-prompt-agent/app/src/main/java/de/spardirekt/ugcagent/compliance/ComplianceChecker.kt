package de.spardirekt.ugcagent.compliance

data class ForbiddenHit(
    val pattern: String,
    val label: String,
)

data class ComplianceResult(
    val forbiddenHits: List<ForbiddenHit>,
    val hasAdDisclosure: Boolean,
) {
    val hasForbiddenLanguage: Boolean get() = forbiddenHits.isNotEmpty()
    val missingAdDisclosure: Boolean get() = !hasAdDisclosure
}

private data class ForbiddenRule(
    val regex: Regex,
    val label: String,
)

object ComplianceChecker {
    private val rules: List<ForbiddenRule> = listOf(
        ForbiddenRule(Regex("(?i)\\bbeste[rsn]?\\b"), "beste"),
        ForbiddenRule(Regex("(?i)\\beinzigartig\\b"), "einzigartig"),
        ForbiddenRule(Regex("(?i)\\bgarantiert\\b"), "garantiert"),
        ForbiddenRule(Regex("(?i)\\b100%\\b"), "100%"),
        ForbiddenRule(Regex("(?i)\\bheilt\\b"), "heilt"),
        ForbiddenRule(Regex("(?i)\\bkuriert\\b"), "kuriert"),
        ForbiddenRule(Regex("(?i)\\blindert\\s+schmerzen\\b"), "lindert schmerzen"),
        ForbiddenRule(Regex("(?i)\\bschnell\\s+geliefert\\b"), "schnell geliefert"),
        ForbiddenRule(Regex("(?i)\\bversandkostenfrei\\s+garantiert\\b"), "versandkostenfrei garantiert"),
        ForbiddenRule(Regex("(?i)\\bnur\\s+heute\\b"), "nur heute"),
        ForbiddenRule(Regex("(?i)\\blimitiert\\b"), "limitiert"),
    )

    val forbiddenPatterns: List<Regex> = rules.map { it.regex }

    private val adDisclosure = Regex("(?i)\\b(werbung|anzeige)\\b")

    fun checkCompliance(text: String): List<String> =
        rules.filter { it.regex.containsMatchIn(text) }.map { it.regex.pattern }

    fun checkAdDisclosure(text: String): Boolean =
        adDisclosure.containsMatchIn(text)

    fun evaluate(prompt: String, caption: String = ""): ComplianceResult {
        val combined = listOf(prompt, caption).filter { it.isNotBlank() }.joinToString("\n")
        val hits = rules.filter { it.regex.containsMatchIn(combined) }.map {
            ForbiddenHit(pattern = it.regex.pattern, label = it.label)
        }
        return ComplianceResult(
            forbiddenHits = hits,
            hasAdDisclosure = checkAdDisclosure(combined),
        )
    }
}
