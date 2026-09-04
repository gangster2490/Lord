package de.spardirekt.veoprompt.ultra.compliance

/**
 * Gemini / VEO prompt sanitizer policy.
 *
 * Operating snapshot of Google Gemini API and Veo safety filters that refuse
 * a pasted product-ad prompt. Not legal advice. Findings live in safetyAudit.gemini
 * only — never inside veoPrompt.
 */
object GeminiVeoPolicy {

    const val VERSION = "2026.09-v1"
    const val TITLE = "Gemini / VEO Prompt Sanitizer"
    const val SOURCE =
        "Google Gemini API prohibited-use policy and Veo 3 safety filters (operating snapshot, Sep 2026)"

    enum class Severity { INFO, MEDIUM, HIGH }

    data class Rule(
        val code: String,
        val title: String,
        val summary: String,
        val severity: Severity,
        val hardBlock: Boolean = false
    )

    val RULES: List<Rule> = listOf(
        Rule(
            "GV_SUBMIT",
            "Product-only Gemini submission",
            "The owner pastes the full veoPrompt into Gemini / VEO. Keep the prompt a clean product-ad brief.",
            Severity.INFO
        ),
        Rule(
            "GV_NO_MINORS_UNSAFE",
            "No minors in unsafe or sexual contexts",
            "Gemini / VEO refuse sexual, nude, or exploitative depictions of minors. Do not write those scenes.",
            Severity.HIGH,
            hardBlock = true
        ),
        Rule(
            "GV_NO_SEXUAL",
            "No sexual or pornographic content",
            "No explicit sex, erotic posing, or pornographic framing in a product-ad prompt.",
            Severity.HIGH
        ),
        Rule(
            "GV_NO_NUDITY",
            "No nudity",
            "No nude or undressed bodies. Product ads stay fully clothed or product-only.",
            Severity.HIGH
        ),
        Rule(
            "GV_NO_REAL_PERSON",
            "No real-person or celebrity likeness",
            "Veo refuses photorealistic identifiable people, celebrity lookalikes, and cloned faces or voices.",
            Severity.HIGH
        ),
        Rule(
            "GV_NO_VIOLENCE",
            "No graphic violence or gore",
            "No blood splatter, gore, torture, corpses, or graphic injury.",
            Severity.HIGH
        ),
        Rule(
            "GV_NO_WEAPONS",
            "No weapons or explosives instructions",
            "No firearm assembly, bomb-making, or aiming a weapon at a person.",
            Severity.HIGH,
            hardBlock = true
        ),
        Rule(
            "GV_NO_SELF_HARM",
            "No self-harm methods",
            "No suicide methods or instructions to injure oneself.",
            Severity.HIGH,
            hardBlock = true
        ),
        Rule(
            "GV_NO_HATE",
            "No hate or extremist symbols",
            "No slurs, hate calls, or extremist imagery.",
            Severity.HIGH
        ),
        Rule(
            "GV_NO_DRUGS",
            "No illegal drug use",
            "No depiction of using or preparing illegal drugs.",
            Severity.HIGH
        ),
        Rule(
            "GV_NO_COPYRIGHT_CHAR",
            "No copyrighted characters",
            "No Disney, Marvel, Pokémon, or other copyrighted cartoon / movie characters.",
            Severity.MEDIUM
        ),
        Rule(
            "GV_NO_CHILD_TALENT",
            "No child talent on camera",
            "Veo often refuses photorealistic children. Use adult hands or product-only framing.",
            Severity.MEDIUM
        )
    )

    val HARD_BLOCK_CODES = RULES.filter { it.hardBlock }.map { it.code }.toSet()

    val BANNED_MINORS_UNSAFE = listOf(
        "sexualized child", "sexualised child", "child nude", "nude child",
        "naked child", "sexy teen", "sexy teenager", "underage girl", "underage boy",
        "child pornography", "child porn", "csam", "minor in lingerie",
        "сексуализированный ребёнок", "голое дитя", "ребёнок ню",
        "nacktes kind", "sexuelles kind"
    )

    val BANNED_SEXUAL = listOf(
        "explicit sex", "pornographic", "erotic dance", "sex scene",
        "sexual intercourse", "hardcore porn", "orgasm close-up",
        "seductive lingerie model", "striptease",
        "порнографи", "эротический танец", "секс-сцена",
        "pornografisch", "erotischer tanz"
    )

    val BANNED_NUDITY = listOf(
        "fully nude", "completely naked", "naked woman", "naked man",
        "topless model", "nude body", "undressed body",
        "полностью голая", "полностью голый", "голое тело",
        "völlig nackt", "nackte frau", "nackter mann"
    )

    val BANNED_REAL_PERSON = listOf(
        "celebrity lookalike", "celebrity clone", "famous actor's face",
        "photorealistic celebrity", "resembles a celebrity",
        "real person's face", "identifiable real person",
        "clone of a celebrity", "looks exactly like the celebrity",
        "deepfake of a real person",
        "лицо знаменитости", "клон знаменитости", "реальное лицо человека",
        "promi-double", "berühmten gesicht"
    )

    val BANNED_VIOLENCE = listOf(
        "gushing blood", "blood splatter", "pool of blood", "graphic gore",
        "severed limb", "dismembered body", "decapitated", "torture scene",
        "graphic corpse", "someone gets stabbed", "someone gets shot",
        "лужа крови", "брызги крови", "отрубленн",
        "blutspritzer", "abgetrenntes glied"
    )

    val BANNED_WEAPONS = listOf(
        "how to build a bomb", "make a bomb", "assemble a firearm",
        "make explosives", "point a gun at", "aim the rifle at",
        "shoot the person", "detonate the explosive",
        "как сделать бомбу", "собрать огнестрел", "навести пистолет",
        "bombe bauen", "waffe zusammenbauen"
    )

    val BANNED_SELF_HARM = listOf(
        "how to commit suicide", "suicide method", "kill yourself",
        "cut yourself", "self-harm tutorial",
        "способ самоубийства", "убей себя",
        "selbstmordmethode", "bring dich um"
    )

    val BANNED_HATE = listOf(
        "nazi salute", "white power symbol", "kill all immigrants",
        "racial slur shouted", "hate crime staging",
        "нацистское приветствие", "убей всех",
        "hitlergruß"
    )

    val BANNED_DRUGS = listOf(
        "snort cocaine", "inject heroin", "smoke meth",
        "cook methamphetamine", "prepare cocaine lines",
        "нюхать кокаин", "колоть героин",
        "kokain sniffen", "heroin spritzen"
    )

    val BANNED_COPYRIGHT_CHAR = listOf(
        "disney character", "mickey mouse", "marvel superhero",
        "spiderman costume", "pokemon character", "hello kitty character",
        "персонаж дисней", "супергерой marvel"
    )

    val BANNED_CHILD_TALENT = listOf(
        "child model", "kid influencer", "toddler holding",
        "baby model", "children playing with the product",
        "photorealistic child", "photorealistic children",
        "ребёнок-модель", "ребёнок держит товар",
        "kindermodel", "kleinkind hält"
    )

    val SAFE_FALLBACK_VOICE = "Same photographed product, clean commercial demo."

    val REPLACEMENTS: List<Pair<String, String>> = listOf(
        "sexualized child" to "product only, no people",
        "sexualised child" to "product only, no people",
        "child nude" to "product only",
        "nude child" to "product only",
        "naked child" to "product only",
        "celebrity lookalike" to "anonymous adult hand only",
        "celebrity clone" to "no identifiable person",
        "photorealistic celebrity" to "product-only framing",
        "real person's face" to "no face, product only",
        "identifiable real person" to "no identifiable person",
        "gushing blood" to "clean dry product surface",
        "blood splatter" to "clean product surface",
        "graphic gore" to "clean product-ad lighting",
        "fully nude" to "fully clothed product demo",
        "naked woman" to "adult hand only",
        "naked man" to "adult hand only",
        "child model" to "adult hand only",
        "kid influencer" to "product-only framing",
        "toddler holding" to "adult hand holding",
        "photorealistic child" to "product-only framing",
        "photorealistic children" to "product-only framing",
        "disney character" to "unbranded product styling",
        "marvel superhero" to "unbranded product styling"
    )

    val CRITICAL_LOCK_LINES = listOf(
        "GEMINI / VEO HARD LOCK: Product-only commercial. No real-person likeness, no celebrities, no minors.",
        "No sexual content, nudity, gore, weapons, self-harm, hate, or illegal drug use.",
        "Photorealistic faces of identifiable real people are forbidden.",
        "Keep the photographed product unchanged across all four shots."
    )

    val NEGATIVE_BULLETS = listOf(
        "- no celebrity or real-person likeness",
        "- no children or minors on camera",
        "- no nudity or sexual content",
        "- no gore, blood splatter, or graphic injury",
        "- no weapons, explosions, or violent acts",
        "- no hate symbols or slurs",
        "- no illegal drugs or self-harm",
        "- no copyrighted cartoon or movie characters"
    )

    fun allBannedPhrases(): List<Pair<String, String>> = listOf(
        "GV_NO_MINORS_UNSAFE" to BANNED_MINORS_UNSAFE,
        "GV_NO_SEXUAL" to BANNED_SEXUAL,
        "GV_NO_NUDITY" to BANNED_NUDITY,
        "GV_NO_REAL_PERSON" to BANNED_REAL_PERSON,
        "GV_NO_VIOLENCE" to BANNED_VIOLENCE,
        "GV_NO_WEAPONS" to BANNED_WEAPONS,
        "GV_NO_SELF_HARM" to BANNED_SELF_HARM,
        "GV_NO_HATE" to BANNED_HATE,
        "GV_NO_DRUGS" to BANNED_DRUGS,
        "GV_NO_COPYRIGHT_CHAR" to BANNED_COPYRIGHT_CHAR,
        "GV_NO_CHILD_TALENT" to BANNED_CHILD_TALENT
    ).flatMap { (code, phrases) -> phrases.map { code to it } }

    fun severityOf(code: String): String =
        RULES.firstOrNull { it.code == code }?.severity?.name ?: "HIGH"

    fun systemPromptBlock(): String = """
GEMINI / VEO PROMPT SANITIZER (${VERSION})
Source: $SOURCE
Applies to veoPrompt, voiceover, title and overlays before the owner pastes into Gemini / VEO.
Store findings in safetyAudit.gemini only. NEVER put SAFETY AUDIT, AIGC AUDIT or GEMINI AUDIT inside veoPrompt.

HARD PROHIBITIONS (Gemini / VEO will refuse the prompt):
- No sexual content, pornography, or nudity.
- No minors in sexual, nude, or exploitative scenes. No photorealistic child talent.
- No celebrity lookalikes, cloned faces, or identifiable real people.
- No gore, blood splatter, torture, or graphic injury.
- No weapons assembly, bomb-making, or aiming a weapon at a person.
- No suicide or self-harm methods.
- No hate symbols or slurs.
- No illegal drug use.
- No copyrighted cartoon or movie characters.

REQUIRED:
- Product-only commercial. Adult hands only if hands are needed.
- Photorealistic VEO output stays a product ad, not a film of real people.
- Keep the photographed product unchanged.
""".trimIndent()
}
