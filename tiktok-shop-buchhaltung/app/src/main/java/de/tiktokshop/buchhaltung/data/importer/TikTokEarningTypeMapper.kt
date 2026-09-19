package de.tiktokshop.buchhaltung.data.importer

/**
 * Bildet die exakten "Type of earnings"-Werte aus dem TikTok-Excel auf den in der App
 * verwendeten `incomeType` ab (§7). Anders als `ocr.IncomeTypeSuggester` (das auf unscharfen
 * OCR-Text angewendet wird) arbeitet dieser Mapper auf exakten Excel-Strings.
 */
object TikTokEarningTypeMapper {

    private val EXACT_MAPPING = mapOf(
        "Standard commission" to "Provision",
        "Shop ads commission" to "Provision",
        "Seller bonus" to "Bonus",
        "Affiliate partner bonus" to "Bonus",
        "Rewards" to "Rewards",
    )

    /** Unbekannte Earning-Types werden unverändert als incomeType übernommen statt verworfen. */
    fun mapToIncomeType(earningType: String): String = EXACT_MAPPING[earningType.trim()] ?: earningType.trim()
}
