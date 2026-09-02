package de.spardirekt.veoprompt.ultra.compliance

/**
 * Internal TikTok Shop Content Quality & Compliance Policy for Veo Prompt Ultra.
 *
 * Encodes the shoppable-video rules that apply to generated VEO packages
 * (voiceover, title, overlays, hashtags, prompt instructions).
 * This is an operating checklist for the owner — not legal advice and not
 * a substitute for Seller Center.
 *
 * Policy snapshot: TikTok Shop Content Policy (22 May 2026) and
 * Product Listing Policy (2 Jun 2026), plus the app's product-fidelity contract.
 */
object TikTokShopPolicy {

    const val VERSION = "2026.06-v1"
    const val TITLE = "TikTok Shop Content Quality & Compliance Policy"

    enum class Severity { INFO, MEDIUM, HIGH }

    data class Rule(
        val code: String,
        val title: String,
        val summary: String,
        val severity: Severity
    )

    val RULES: List<Rule> = listOf(
        Rule(
            "CQ_ACCURACY",
            "Accuracy & product match",
            "Every spoken line, overlay and title must describe the same physical product shown in the uploaded photos.",
            Severity.HIGH
        ),
        Rule(
            "CQ_NO_REDESIGN",
            "No generic replacement",
            "Do not replace, modernize or stylize the photographed product.",
            Severity.HIGH
        ),
        Rule(
            "CQ_FIRST_FRAME",
            "Product visible immediately",
            "The product must be readable from the first second. No food-only or environment-only hook.",
            Severity.MEDIUM
        ),
        Rule(
            "CL_UNSUPPORTED",
            "No unsupported claims",
            "Do not claim performance, materials, certifications or origin unless HIGH-confidence evidence exists.",
            Severity.HIGH
        ),
        Rule(
            "CL_MEDICAL",
            "No medical or transformation claims",
            "Do not imply clinical outcomes, cures, treatments or extreme physical transformations.",
            Severity.HIGH
        ),
        Rule(
            "CL_SUPERLATIVE",
            "No absolute superlatives or price superiority",
            "Do not use cheapest / lowest price / best ever / #1 / guaranteed.",
            Severity.HIGH
        ),
        Rule(
            "PR_PRICE_UI",
            "No prices or marketplace UI",
            "Do not show prices, discounts, coupons, seller, ratings, buttons or phone UI.",
            Severity.HIGH
        ),
        Rule(
            "PR_URGENCY",
            "No fake urgency",
            "Do not use last-chance, only-today, hurry or similar scarcity language.",
            Severity.MEDIUM
        ),
        Rule(
            "PR_SYMPATHY",
            "No sympathy selling",
            "Do not use hardship, illness or pity to encourage a purchase.",
            Severity.HIGH
        ),
        Rule(
            "PR_FORCED_CTA",
            "No forced hard CTA",
            "Do not command Закажите / Купите / Jetzt kaufen. Soft invitation only.",
            Severity.MEDIUM
        ),
        Rule(
            "PR_OFF_PLATFORM",
            "No off-platform redirect",
            "Do not send buyers to WhatsApp, Telegram, QR checkout or link-in-bio shops.",
            Severity.HIGH
        ),
        Rule(
            "PR_POLITICAL",
            "No political promotion",
            "Do not use political advertising or election fundraising language.",
            Severity.HIGH
        ),
        Rule(
            "PR_HARMFUL",
            "No harmful or manipulative content",
            "No gambling, shocking, spam or fraudulent promotional behavior.",
            Severity.HIGH
        ),
        Rule(
            "AI_TRUTH",
            "AI must not alter the product",
            "AI-generated video must keep the photographed product unchanged.",
            Severity.HIGH
        ),
        Rule(
            "AI_LABEL",
            "AI disclosure",
            "Fully or mostly AI-generated video should be labelled when published.",
            Severity.INFO
        )
    )

    val BANNED_SUPERLATIVES = listOf(
        "cheapest", "lowest price", "lowest-price", "best ever", "best-ever",
        "number one", "#1", "guaranteed", "must have", "must-have",
        "günstigste", "billigste", "beste der welt", "garantiert",
        "самый дешёв", "самый дешев", "самый лучш", "гарантия 100"
    )

    val BANNED_URGENCY = listOf(
        "last chance", "limited time", "only today", "hurry", "act now",
        "selling out", "don't miss", "letzte chance", "nur heute",
        "jetzt kaufen", "jetzt bestellen", "только сегодня", "успей",
        "срочно", "последний шанс"
    )

    val BANNED_MEDICAL = listOf(
        "cure", "cures", "heals", "treats cancer", "clinically proven",
        "miracle", "fda approved", "medical grade",
        "heilt", "klinisch bewiesen", "wunderheil",
        "лечит", "излечива", "клинически доказан"
    )

    val BANNED_SYMPATHY = listOf(
        "please help me", "my kids won't", "i am dying",
        "bitte helft", "meine kinder hungern",
        "помогите мне", "дети голодают"
    )

    val BANNED_HARD_CTA = listOf(
        "закажите в tiktok shop", "закажите сейчас", "купите сейчас",
        "jetzt bei tiktok shop bestellen", "jetzt kaufen", "shop now on tiktok shop"
    )

    val BANNED_OFF_PLATFORM = listOf(
        "whatsapp", "telegram", "link in bio", "scan the qr",
        "instagram.com", "buy on amazon", "купи в вотсап"
    )

    val BANNED_POLITICAL = listOf(
        "vote for", "election campaign", "donate to the campaign",
        "голосуй за", "wahlkampf"
    )

    val BANNED_HARMFUL = listOf(
        "casino", "jackpot guaranteed", "free money glitch"
    )

    val REGULATED_UNLESS_HIGH = listOf(
        "waterproof", "certified", "organic", "made in usa",
        "made in the usa", "made in america", "made in germany",
        "cast iron", "чугун", "non-stick", "nonstick", "антипригар",
        "stainless steel", "нержавеющ"
    )

    val PRICE_REGEX = Regex("""([$€£¥₽]\s?\d)|(\d+\s?%)|(\d+[.,]\d{2}\s?[$€£¥₽]?)|(add to cart)|(buy now)""", RegexOption.IGNORE_CASE)
}
