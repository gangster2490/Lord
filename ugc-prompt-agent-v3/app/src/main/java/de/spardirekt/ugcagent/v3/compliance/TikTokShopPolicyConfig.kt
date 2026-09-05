package de.spardirekt.ugcagent.v3.compliance

object TikTokShopPolicyConfig {
    const val VERSION = "2026.09.1"
    const val LAST_UPDATED = "2026-09-05"

    val absoluteClaimPatterns: List<Pair<String, String>> = listOf(
        Regex("\\bbeste[rsn]?\\b", RegexOption.IGNORE_CASE).pattern to "beste",
        Regex("\\bbest\\b", RegexOption.IGNORE_CASE).pattern to "best",
        Regex("\\bgarantiert\\b", RegexOption.IGNORE_CASE).pattern to "garantiert",
        Regex("\\bguaranteed\\b", RegexOption.IGNORE_CASE).pattern to "guaranteed",
        Regex("100%").pattern to "100%",
        Regex("\\bperfekt\\b", RegexOption.IGNORE_CASE).pattern to "perfekt",
        Regex("funktioniert immer", RegexOption.IGNORE_CASE).pattern to "funktioniert immer",
        Regex("nie wieder", RegexOption.IGNORE_CASE).pattern to "nie wieder",
        Regex("\\babsolut\\b", RegexOption.IGNORE_CASE).pattern to "absolut",
        Regex("nummer\\s*1", RegexOption.IGNORE_CASE).pattern to "Nummer 1",
        Regex("\\beinzigartig\\b", RegexOption.IGNORE_CASE).pattern to "einzigartig",
        Regex("must[- ]have", RegexOption.IGNORE_CASE).pattern to "must-have",
        Regex("game[- ]changer", RegexOption.IGNORE_CASE).pattern to "game changer",
    )

    val medicalPatterns: List<Pair<String, String>> = listOf(
        Regex("\\bheilt\\b", RegexOption.IGNORE_CASE).pattern to "heilt",
        Regex("\\bkuriert\\b", RegexOption.IGNORE_CASE).pattern to "kuriert",
        Regex("\\bbehandelt\\b", RegexOption.IGNORE_CASE).pattern to "behandelt",
        Regex("prevents disease", RegexOption.IGNORE_CASE).pattern to "prevents disease",
        Regex("relieves pain|lindert schmerzen", RegexOption.IGNORE_CASE).pattern to "relieves pain",
        Regex("medically proven", RegexOption.IGNORE_CASE).pattern to "medically proven",
        Regex("clinically guaranteed", RegexOption.IGNORE_CASE).pattern to "clinically guaranteed",
        Regex("medical miracle", RegexOption.IGNORE_CASE).pattern to "medical miracle",
    )

    val shippingPatterns: List<Pair<String, String>> = listOf(
        Regex("free shipping|versandkostenfrei", RegexOption.IGNORE_CASE).pattern to "free shipping",
        Regex("guaranteed delivery|liefergarantie", RegexOption.IGNORE_CASE).pattern to "delivery guarantee",
        Regex("next[- ]day delivery", RegexOption.IGNORE_CASE).pattern to "next-day delivery",
        Regex("fastest shipping|schnell geliefert", RegexOption.IGNORE_CASE).pattern to "fastest shipping",
    )

    val scarcityPatterns: List<Pair<String, String>> = listOf(
        Regex("only today|nur heute", RegexOption.IGNORE_CASE).pattern to "only today",
        Regex("last chance", RegexOption.IGNORE_CASE).pattern to "last chance",
        Regex("low stock|nur noch wenige", RegexOption.IGNORE_CASE).pattern to "fake scarcity",
        Regex("limitiert", RegexOption.IGNORE_CASE).pattern to "limitiert",
    )

    val comparativePatterns: List<Pair<String, String>> = listOf(
        Regex("better than|besser als", RegexOption.IGNORE_CASE).pattern to "comparative claim",
        Regex("cheaper than|günstiger als", RegexOption.IGNORE_CASE).pattern to "cheaper than",
        Regex("stronger than|stärker als", RegexOption.IGNORE_CASE).pattern to "stronger than",
        Regex("best on tiktok", RegexOption.IGNORE_CASE).pattern to "best on TikTok",
    )

    val offPlatformPatterns: List<Pair<String, String>> = listOf(
        Regex("https?://", RegexOption.IGNORE_CASE).pattern to "external URL",
        Regex("whatsapp", RegexOption.IGNORE_CASE).pattern to "WhatsApp order",
        Regex("\\bqr[- ]?code\\b", RegexOption.IGNORE_CASE).pattern to "QR code purchase",
        Regex("buy on another site|auf einer anderen seite kaufen", RegexOption.IGNORE_CASE).pattern to "off-platform checkout",
    )

    val certificationWords = listOf(
        "bpa-free", "bpa free", "ce-zertifiziert", "certified", "tested", "approved",
        "award-winning", "clinically tested",
    )

    val restrictedCategoryKeywords = listOf(
        "waffe", "weapon", "firearm", "ammunition",
        "tabak", "tobacco", "vape", "e-cigarette",
        "cannabis", "cbd oil medicine",
        "prescription", "verschreibungspflichtig",
        "explode", "sprengstoff",
        "counterfeit", "fälschung",
    )

    val disclosurePattern = Regex("\\b(werbung|anzeige)\\b", RegexOption.IGNORE_CASE)

    val marketplaceIgnore = listOf(
        "price", "old price", "discount", "commission", "affiliate",
        "seller name", "shop name", "seller rank", "ranking", "best seller",
        "reviews counter", "likes", "cart button", "buy button", "free sample",
        "tiktok navigation", "marketplace navigation", "phone status bar", "seller badges",
        "earn €", "per sale", "% commission",
    )
}
