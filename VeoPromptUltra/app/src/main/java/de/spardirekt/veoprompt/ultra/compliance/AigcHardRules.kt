package de.spardirekt.veoprompt.ultra.compliance

/**
 * TikTok Shop AIGC Hard Rules.
 *
 * Snapshot of Seller Center “AI-Generated Content Restrictions and Requirements”
 * (13 May 2026). Operating checklist for generated VEO packages — not legal advice.
 *
 * VEO 3.1 output is fully AI-generated photorealistic video. Disclosure is required
 * on publish. The audit lives in safetyAudit only — never inside veoPrompt.
 */
object AigcHardRules {

    const val VERSION = "2026.05-v1"
    const val TITLE = "TikTok Shop AIGC Hard Rules"
    const val SOURCE =
        "TikTok Shop AI-Generated Content Restrictions and Requirements (13 May 2026)"

    enum class Severity { INFO, MEDIUM, HIGH }

    data class Rule(
        val code: String,
        val title: String,
        val summary: String,
        val severity: Severity
    )

    val RULES: List<Rule> = listOf(
        Rule(
            "AIGC_DISCLOSE",
            "Mandatory AI disclosure",
            "Fully or significantly AI-generated photorealistic video must be labelled with TikTok’s “AI-generated content” toggle and may also use on-screen text or a description note.",
            Severity.INFO
        ),
        Rule(
            "AIGC_NO_DECEIVE",
            "No deception",
            "AI must not mislead, deceive, or create a false impression about the product, people, or facts. Do not present VEO output as live footage.",
            Severity.HIGH
        ),
        Rule(
            "AIGC_NO_IMPERSONATE",
            "No impersonation",
            "Do not imitate another person’s identity, likeness, or voice. No celebrity clones or unauthorized digital humans.",
            Severity.HIGH
        ),
        Rule(
            "AIGC_NO_FALSE_ENDORSE",
            "No fake expert personas",
            "Do not fabricate doctors, professors, news anchors, or other authority figures to endorse a product.",
            Severity.HIGH
        ),
        Rule(
            "AIGC_NO_PRODUCT_ALTER",
            "No product not-as-described",
            "Do not change the photographed product’s size, color, shape, materials, or features.",
            Severity.HIGH
        ),
        Rule(
            "AIGC_NO_UNREALISTIC",
            "No false or instant results",
            "Do not invent miraculous, instant, or otherwise unrealistic product effects.",
            Severity.HIGH
        ),
        Rule(
            "AIGC_NO_FAKE_FEATURES",
            "No invented visual features",
            "Do not add glow, fake 3D of a flat product, or other features the real product does not have.",
            Severity.HIGH
        ),
        Rule(
            "AIGC_NO_FEAR",
            "No fear-based AI visuals",
            "Do not use shocking organ, disease, or damaged-body imagery to pressure a purchase.",
            Severity.HIGH
        ),
        Rule(
            "AIGC_NO_IP",
            "No unauthorized IP",
            "Do not use a third party’s likeness, voice, name, or trademark without authorization.",
            Severity.HIGH
        ),
        Rule(
            "AIGC_PRODUCT_MATCH",
            "Product must match photos",
            "The product in the video must match the uploaded physical product being sold.",
            Severity.HIGH
        )
    )

    val BANNED_DECEIVE = listOf(
        "this is real footage", "not ai generated", "not ai-generated",
        "actually filmed", "real customer filmed", "hidden camera real",
        "это реальная съёмка", "это не нейросеть",
        "echt gefilmt", "keine ki", "kein ki-video"
    )

    val BANNED_IMPERSONATION = listOf(
        "as a doctor", "i am a doctor", "i'm a doctor", "im a doctor",
        "als arzt", "ich bin arzt", "я врач", "я доктор",
        "celebrity face", "celebrity endorsement", "impersonate",
        "deepfake", "voice clone", "cloned voice", "clone of",
        "stolen likeness", "someone else's face"
    )

    val BANNED_FALSE_ENDORSE = listOf(
        "doctor recommended", "professor recommended",
        "dermatologist recommended", "clinically endorsed",
        "ai doctor", "digital human doctor", "virtual doctor",
        "news anchor endorses", "expert lecture endorses",
        "виртуальный врач", "доктор рекомендует"
    )

    val BANNED_UNREALISTIC = listOf(
        "instantly grows hair", "grows hair overnight", "fills bald spots",
        "removes cavities", "restores teeth instantly",
        "overnight cure", "miracle transformation", "cures in seconds",
        "instant transformation", "regrows hair instantly",
        "мгновенно вырастает", "за секунду вылеч"
    )

    val BANNED_PRODUCT_ALTER = listOf(
        "change the product color", "change the product size",
        "make the product larger", "make the product smaller",
        "redesign the product", "different product than photos",
        "alter the product appearance"
    )

    val BANNED_FAKE_FEATURES = listOf(
        "fake 3d", "fake 3-d", "turn the product into 3d",
        "add a glow the product does not", "invented glow",
        "glowing feature the product does not"
    )

    val BANNED_FEAR = listOf(
        "damaged organs", "rotting organs", "diseased organ",
        "shocking disease", "destroyed organs",
        "гниющие органы", "разрушенные органы"
    )

    val BANNED_IP = listOf(
        "unauthorized trademark", "without permission use the brand",
        "stolen brand logo", "use nike logo", "use adidas logo"
    )

    val CRITICAL_LOCK_LINES = listOf(
        "AIGC HARD LOCK: Do not alter the photographed product's size, color, shape, materials or features.",
        "Do not invent unrealistic or instant results.",
        "Do not impersonate real people, doctors, celebrities or brands.",
        "Photorealistic VEO output is fully AI-generated; the owner must enable TikTok's AI-generated content toggle when publishing."
    )

    val NEGATIVE_BULLETS = listOf(
        "- no fake doctor, professor or expert persona",
        "- no celebrity impersonation or cloned voice",
        "- no product size, color or feature change",
        "- no instant miracle results",
        "- no glowing invented product features",
        "- no fake 3D of a flat product",
        "- no fear-based organ or disease visuals"
    )

    fun systemPromptBlock(): String = """
TIKTOK SHOP AIGC HARD RULES (${VERSION})
Source: $SOURCE
Applies to veoPrompt, voiceover, title and overlays.
Store findings in safetyAudit only. NEVER put SAFETY AUDIT or AIGC AUDIT inside veoPrompt.

HARD PROHIBITIONS:
- Do not use AI to mislead, deceive, or create a false impression.
- Do not impersonate a real person, celebrity, or cloned voice.
- Do not fabricate doctors, professors, news anchors or other authority endorsements.
- Do not change the photographed product's size, color, shape, materials or features.
- Do not invent miraculous, instant, or otherwise unrealistic results.
- Do not add glow, fake 3D, or features the real product does not have.
- Do not use fear-based organ or disease imagery.
- Do not use a third party's likeness, voice, name or trademark without authorization.

REQUIRED:
- VEO 3.1 output is fully AI-generated photorealistic video.
- The owner must enable TikTok's "AI-generated content" toggle when publishing.
- Optional extra disclosure: short on-screen note or description line.

PERMITTED if the product stays accurate:
- Style, lighting, background, translation, dubbing, subtitles, copy support,
  and simple use-demonstration scenes without miraculous effects.
""".trimIndent()
}
